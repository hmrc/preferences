/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.repository

import play.api.libs.json.*
import play.api.libs.json.Json.*
import uk.gov.hmrc.mongo.play.json.formats.{ MongoFormats, MongoJavatimeFormats }
import uk.gov.hmrc.mongo.play.json.{ Codecs, PlayMongoRepository }
import uk.gov.hmrc.mongo.*
import uk.gov.hmrc.mongo.workitem.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.preferences.controllers.model.EmailToken
import uk.gov.hmrc.preferences.controllers.model.Credentials
import uk.gov.hmrc.preferences.model.Reminders.*
import org.bson.types.ObjectId

import javax.inject.{ Inject, Singleton }
import org.mongodb.scala.model.Filters.*
import org.mongodb.scala.{ ObservableFuture, SingleObservableFuture }
import com.mongodb.{ BasicDBObject, ReadConcern }
import org.mongodb.scala.ToSingleObservablePublisher
import org.apache.commons.lang3.time.StopWatch
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.bson.conversions.Bson
import org.mongodb.scala.bson.{ BsonDocument, BsonInt32, BsonString }
import org.mongodb.scala.bson.collection.mutable.Document
import org.mongodb.scala.model.{ Aggregates, Filters, FindOneAndUpdateOptions, IndexModel, IndexOptions, ReturnDocument }
import org.mongodb.scala.model.Indexes.{ ascending, compoundIndex, descending }

import scala.concurrent.{ ExecutionContext, Future }
import org.mongodb.scala.model.Updates.{ combine, unset }
import org.mongodb.scala.result.UpdateResult
import uk.gov.hmrc.mongo.play.json.Codecs.toBson
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{ Failed, Succeeded }
import uk.gov.hmrc.preferences.JsonToBson.jsobjToBson
import uk.gov.hmrc.preferences.model.{ DocStats, EmailBounce, EmailEvent, EmailVerificationLink, EntityId, Event, Language, MarkForDeEnrolment, OptPageEvent, PendingEmailAddress, Preferences, Reminder }
import uk.gov.hmrc.preferences.util.Dc

import java.time.{ Instant, LocalDate }

trait PreferencesRepository {

  def qb: PreferenceQueryBuilder = new PreferenceQueryBuilder {}

  protected val repo: PlayMongoRepository[Preferences]

  def updated(entityId: EntityId)(implicit hc: HeaderCarrier): Future[Option[Preferences]]

  def findPreferencesById(id: ObjectId): Future[Option[Preferences]]

  def createOrUpdateTermsAndConditions(
    preferences: Preferences,
    credentials: Option[Credentials]
  )(implicit hc: HeaderCarrier): Future[PreferenceUpdateResult]

  def findExpiredRecordsForDeEnrolment(count: Int, expiryDate: Instant): Future[Seq[Preferences]] =
    repo.collection
      .find(lt("markForDeEnrolment.time", expiryDate))
      .limit(count)
      .toFuture()

  def findUnverifiedExpired(expiryCutoff: Instant): Future[Seq[Preferences]] =
    repo.collection
      .find(qb.unverifiedUsersQuery(expiryCutoff))
      .sort(Document("createdAt" -> 1))
      .toFuture()

  def findUnverifiedTwoEmailsExpired(expiryCutoff: Instant): Future[Seq[Preferences]] =
    repo.collection
      .find(qb.unverifiedWithTwoEmailsQuery(expiryCutoff))
      .sort(Document("createdAt" -> 1))
      .toFuture()

  def markEmailVerified(
    id: ObjectId,
    pendingEmail: PendingEmailAddress,
    language: Option[Language],
    event: Option[Event]
  )(implicit hc: HeaderCarrier): Future[Unit]

  def markForDeEnrolment(entityId: EntityId, identifier: String)(implicit hc: HeaderCarrier): Future[Boolean]

  def unsetDeEnrolment(entityId: EntityId)(implicit hc: HeaderCarrier): Future[Boolean]

  def addBouncesAndClearVerificationLink(
    preferences: Preferences,
    emailBounce: Option[EmailBounce],
    pendingEmailBounce: Option[EmailBounce],
    shouldIncBounce: Boolean
  )(implicit hc: HeaderCarrier): Future[PreferenceUpdateResult]

  def expireEmailVerificationLink(id: ObjectId, link: EmailVerificationLink)(implicit
    hc: HeaderCarrier
  ): Future[Boolean]

  def setUnverifiedEmailAddress(entityId: EntityId, email: PendingEmailAddress, event: Seq[Event])(implicit
    hc: HeaderCarrier
  ): Future[Unit]

  def setUnverifiedEmailLanguage(entityId: EntityId, lang: Option[Language]): Future[PreferenceUpdateResult]

  def unsetPendingEmail(entityId: EntityId, event: EmailEvent): Future[PreferenceUpdateResult]

  def setVerifiedEmailLanguage(entityId: EntityId, lang: Option[Language]): Future[PreferenceUpdateResult]

  def findBy(entityId: EntityId)(implicit hc: HeaderCarrier): Future[Option[Preferences]]

  def findByEmail(emailAddress: String)(implicit hc: HeaderCarrier): Future[Seq[Preferences]]

  def findByVerificationToken(token: EmailToken)(implicit hc: HeaderCarrier): Future[Option[Preferences]]

  def countPreferencesUpdatedOn(date: Option[LocalDate])(implicit hc: HeaderCarrier): Future[Long]

  def pullReminder(unverifiedEmailsBefore: => Instant, retryIncompleteBefore: => Instant, reminderField: String)(
    implicit hc: HeaderCarrier
  ): Future[Option[ReminderWorkItem]]

  def setReminderSucceeded(reminder: ReminderWorkItem)(implicit hc: HeaderCarrier): Future[Boolean]

  def setReminderFailed(reminder: ReminderWorkItem)(implicit hc: HeaderCarrier): Future[Boolean]

  def removeById(id: ObjectId)(implicit ec: ExecutionContext): Future[Boolean]

  def removeAll()(implicit ec: ExecutionContext): Future[Boolean]

  def updatePreferenceEventTypeAndOptInPage(id: ObjectId, event: OptPageEvent, events: List[Event]): Future[Boolean]

  def updateById(id: ObjectId, update: Bson): Future[Boolean]

}

case class EmailWithVerificationLink(email: String, emailVerificationLink: EmailVerificationLink)

@Singleton
class PreferencesMongoRepository @Inject() (
  mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
    extends PreferencesRepository with PreferencesRepositoryHelper {

  def indexes(): Seq[IndexModel] =
    Seq(
      IndexModel(ascending("createdAt"), IndexOptions().name("individualPrefsCreatedAtUnique")),
      IndexModel(ascending("updatedAt"), IndexOptions().name("individualPrefsUpdatedAt").sparse(true)),
      IndexModel(ascending("email.email"), IndexOptions().name("saPrefsEmail").sparse(true)),
      IndexModel(ascending("email.lowercaseEmail"), IndexOptions().name("saPrefsLowercaseEmail").sparse(true)),
      IndexModel(
        ascending("email.verifiedWithLink._id"),
        IndexOptions().name("saPrefsEmailVerifiedWithLinkId").sparse(true)
      ),
      IndexModel(ascending("pendingEmail.email"), IndexOptions().name("saPrefsPendingUnverifiedEmail").sparse(true)),
      IndexModel(
        ascending("pendingEmail.lowercaseEmail"),
        IndexOptions().name("saPrefsPendingUnverifiedLowercaseEmail").sparse(true)
      ),
      IndexModel(
        ascending("pendingEmail.verificationLink._id"),
        IndexOptions().name("saPrefsPendingEmailVerificationLinkId").sparse(true)
      ),
      IndexModel(
        ascending("pendingEmail.verificationLink.linkSentTime", "email.verifiedOn"),
        IndexOptions().name("pendingEmailVerificationLinkLinkSentTime").sparse(true).background(true)
      ),
      IndexModel(
        ascending("pendingEmail.reminder.status", "pendingEmail.reminder.updatedAt"),
        IndexOptions().name("saPrefsReminderStatusUpdatedAt").sparse(true)
      ),
      IndexModel(
        ascending("pendingEmail.reminder.status", "pendingEmail.verificationLink.linkSentTime"),
        IndexOptions().name("saPrefsReminderStatusLinkSentTime").sparse(true)
      ),
      IndexModel(
        ascending("pendingEmail.secondReminder.status", "pendingEmail.verificationLink.linkSentTime"),
        IndexOptions().name("saPrefsSecondReminderStatusLinkSentTime").sparse(true)
      ),
      IndexModel(
        ascending("entityId"),
        IndexOptions().name("individualPrefsEntityIdUnique").unique(true).sparse(true).background(true)
      ),
      IndexModel(
        descending("markForDeEnrolment.time"),
        IndexOptions()
          .name("markForDeEnrolmentIndex")
          .partialFilterExpression(exists("markForDeEnrolment.time"))
      ),
      // "generic.optedInAndVerified"
      IndexModel(
        compoundIndex(
          descending("termsAndConditions.generic.accepted"),
          descending("email")
        ),
        IndexOptions()
          .name("genericAcceptedWithEmailIndex")
          .sparse(true)
          .background(true)
      ),
      // "pendingVerification"
      IndexModel(
        compoundIndex(
          descending("email"),
          descending("pendingEmail"),
          descending("pendingEmail.lastBounce")
        ),
        IndexOptions()
          .name("emailAndPendingEmailAndPendingLastBounceExistsIndex")
          .sparse(true)
          .background(true)
      ),
      // "pendingVerificationOfChangedEmail"
      IndexModel(
        compoundIndex(
          descending("email"),
          descending("pendingEmail"),
          descending("email.lastBounce"),
          descending("pendingEmail.lastBounce")
        ),
        IndexOptions()
          .name("emailAndPendingEmailAndLastBounceAndPendingLastBounceExistsIndex")
          .sparse(true)
          .background(true)
      )
    )

  override protected[repository] val repo: PlayMongoRepository[Preferences] =
    new PlayMongoRepository[Preferences](
      mongoComponent,
      "saIndividualPreferences",
      Preferences.formats,
      indexes = indexes(),
      extraCodecs = Seq(
        Codecs.playFormatCodec(MongoJavatimeFormats.instantFormat),
        Codecs.playFormatCodec(MongoFormats.objectIdFormat),
        Codecs.playFormatCodec(Event.eventFormats),
        Codecs.playFormatCodec(Event.versionFormats),
        Codecs.playFormatCodec(Event.optInPageFormats),
        Codecs.playFormatCodec(Event.optInEventFormats),
        Codecs.playFormatCodec(Event.emailEventFormats),
        Codecs.playFormatCodec(Event.customerOptOutFormats),
        Codecs.playFormatCodec(Event.adminOptOutEventFormats),
        Codecs.playFormatCodec(Event.systemOptOutEventFormats),
        Codecs.playFormatCodec(Event.systemOptOutEventFormats)
      ),
      // Note: this will not replace all, only if there is a difference in an index definition
      replaceIndexes = true
    ) {}

  override def removeById(id: ObjectId)(implicit ec: ExecutionContext): Future[Boolean] =
    repo.collection.deleteOne(equal("_id", id)).toFuture().map(_.wasAcknowledged())

  override def removeAll()(implicit ec: ExecutionContext): Future[Boolean] =
    repo.collection.deleteMany(empty()).toFuture().map(_.wasAcknowledged())

  override def createOrUpdateTermsAndConditions(
    prefs: Preferences,
    credentials: Option[Credentials]
  )(implicit hc: HeaderCarrier): Future[PreferenceUpdateResult] =
    updateTermsAndConditions(prefs, credentials)

  override def markEmailVerified(
    id: ObjectId,
    pendingEmail: PendingEmailAddress,
    language: Option[Language],
    event: Option[Event]
  )(implicit hc: HeaderCarrier): Future[Unit] =
    withCurrentTime { implicit time =>
      val updater =
        combine(
          setEmailMailVerifiedUpdate(time, pendingEmail, language),
          pushEventUpdate(event)
        )

      repo.collection
        .updateOne(
          filter = Document("_id" -> id, "pendingEmail.email" -> pendingEmail.email),
          update = updater
        )
        .toFuture()
        .map(ur => if (ur.getModifiedCount == 0) throw BrokenVerificationLinkException(id) else ())
    }

  override def markForDeEnrolment(entityId: EntityId, identifier: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    withCurrentTime { implicit time =>
      repo.collection
        .updateOne(
          filter = equal("entityId", Codecs.toBson(entityId)),
          update = markPreferenceForDeEnrolment(MarkForDeEnrolment(time, identifier))
        )
        .toFuture()
        .map(_.wasAcknowledged())
    }

  override def unsetDeEnrolment(entityId: EntityId)(implicit hc: HeaderCarrier): Future[Boolean] =
    repo.collection
      .updateOne(
        filter = equal("entityId", Codecs.toBson(entityId)),
        update = unsetDeEnrolmentJson
      )
      .toFuture()
      .map(_.wasAcknowledged())

  override def updated(entityId: EntityId)(implicit hc: HeaderCarrier): Future[Option[Preferences]] =
    withCurrentTime { implicit time =>
      repo.collection
        .findOneAndUpdate(
          filter = equal("entityId", Codecs.toBson(entityId)),
          update = setUpdatedAtUpdate(time)
        )
        .toFutureOption()
    }

  // FIXME This is only used by the admin controller, would be nice to move this out of here
  override def expireEmailVerificationLink(id: ObjectId, link: EmailVerificationLink)(implicit
    hc: HeaderCarrier
  ): Future[Boolean] =
    repo.collection
      .updateOne(
        filter = equal("_id", id),
        update = setPendingEmailVerificationLinkSentUpdate(link)
      )
      .toFuture()
      .map(_.wasAcknowledged())

  override def setUnverifiedEmailAddress(entityId: EntityId, email: PendingEmailAddress, event: Seq[Event])(implicit
    hc: HeaderCarrier
  ): Future[Unit] =
    logger.debug(s"[setUnverifiedEmailAddress] adding ${event.size} events; preference.entityId: $entityId")

    repo.collection
      .updateOne(
        filter = equal("entityId", Codecs.toBson(entityId)),
        update = combine(pushEventsForUpdate(event), setPendingEmailUpdate(email))
      )
      .toFuture()
      .filter(d => d.wasAcknowledged() && d.getModifiedCount > 0)
      .transform(
        _ => (),
        _ => new RuntimeException(s"could not set email [${email.email}] on preference with entity id [$entityId]")
      )

  override def setUnverifiedEmailLanguage(
    entityId: EntityId,
    lang: Option[Language]
  ): Future[PreferenceUpdateResult] =
    repo.collection
      .findOneAndUpdate(
        filter = findByEntityIdWithPendingEmailQuery(entityId),
        update = setPendingEmailLanguageUpdate(lang),
        options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
      )
      .toFutureOption()
      .map {
        case Some(_) => PreferenceUpdated
        case _       => LanguageNotUpdated
      }

  override def unsetPendingEmail(entityId: EntityId, event: EmailEvent): Future[PreferenceUpdateResult] =
    repo.collection
      .findOneAndUpdate(
        filter = equal("entityId", Codecs.toBson(entityId)),
        update = combine(toBson(unsetPendingEmailQuery).asDocument(), pushEventsForUpdate(Seq(event))),
        options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
      )
      .toFutureOption()
      .map {
        case Some(_) => PreferenceUpdated
        case _       => NoEmailForPreference
      }

  override def setVerifiedEmailLanguage(
    entityId: EntityId,
    lang: Option[Language]
  ): Future[PreferenceUpdateResult] =
    repo.collection
      .findOneAndUpdate(
        filter = findByEntityIdWithVerifiedEmailQuery(entityId),
        update = setEmailLanguageUpdate(lang)
      )
      .toFutureOption()
      .map {
        case Some(_) => PreferenceUpdated
        case _       => LanguageNotUpdated
      }

  // USED IN TESTING ONLY //
  def hasBounceCount(id: ObjectId)(implicit ec: ExecutionContext): Future[Boolean] =
    repo.collection
      .find(
        filter = hasBounceCountQuery(id)
      )
      .toSingle()
      .toFutureOption()
      .map(_.isDefined)

  override def addBouncesAndClearVerificationLink(
    preferences: Preferences,
    emailBounce: Option[EmailBounce],
    pendingEmailBounce: Option[EmailBounce],
    shouldIncBounce: Boolean
  )(implicit hc: HeaderCarrier): Future[PreferenceUpdateResult] =
    withCurrentTime { implicit time =>
      val incrementBounce = if (shouldIncBounce) {
        incrementBounceQueryUpdate(emailBounce)
      } else {
        Document()
      }
      val setLastBounce = setLastBounceUpdate(emailBounce)
      val setPendingEmailBounce = setPendingEmailBounceUpdate(pendingEmailBounce)
      val setEvents = setEventsUpdate(preferences.events)
      createOrUpdate(
        preferences.entityId,
        combine(incrementBounce, setLastBounce, setPendingEmailBounce, setUpdateAt(time), setEvents)
      )
    }

  override def findPreferencesById(id: ObjectId): Future[Option[Preferences]] = {
    val stopWatch = StopWatch.createStarted()
    repo.collection
      .find(
        filter = equal("_id", id)
      )
      .toSingle()
      .toFutureOption()
      .map(_.map { p =>
        stopWatch.stop()
        logger.debug(
          s"[stopwatch $stopWatch] findPreferencesById id: $id, entityId: ${p.entityId}, " +
            s"containing ${p.events.getOrElse(List.empty).size} events and ${p.surveys.getOrElse(List.empty).size} surveys"
        )
        p
      })
      .recoverWith { case e =>
        logger.error(s"Unable to parse preferences for ${id.toString} due to $e")
        Future.failed(new NotFoundException(s"Unable to parse preferences for ${id.toString}"))
      }
  }

  override def findBy(entityId: EntityId)(implicit hc: HeaderCarrier): Future[Option[Preferences]] = {
    import uk.gov.hmrc.preferences.model.EntityId.formats
    val stopWatch = StopWatch.createStarted()
    repo.collection
      .find(
        filter = equal("entityId", Codecs.toBson(entityId))
      )
      .toSingle()
      .toFutureOption()
      .map(_.map { p =>
        stopWatch.stop()
        logger.debug(
          s"[stopwatch $stopWatch] findBy id: ${p._id}, entityId: $entityId, " +
            s"containing ${p.events.getOrElse(List.empty).size} events and ${p.surveys.getOrElse(List.empty).size} surveys"
        )
        p
      })
      .recoverWith { case e =>
        logger.error(
          s"Unable to parse preferences for entityId ${entityId.value} due to $e. Preferences record is now being deleted."
        )
        repo.collection
          .deleteOne(equal("entityId", Codecs.toBson(entityId)))
          .toFutureOption()
          .map(_ => None)
          .recoverWith { case ex =>
            logger.error(s"Error occurred trying to delete $entityId, $ex")
            Future.successful(None)
          }
      }
  }

  override def findByEmail(emailAddress: String)(implicit hc: HeaderCarrier): Future[Seq[Preferences]] =
    repo.collection
      .find(
        filter = findByEmailQuery(emailAddress)
      )
      .toFuture()
      .recoverWith { case e =>
        logger.debug(s"Unable to parse preferences for emailAddress due to $e")
        Future.failed(new NotFoundException(s"Unable to parse preferences for emailAddress due to $e"))
      }

  override def findByVerificationToken(token: EmailToken)(implicit hc: HeaderCarrier): Future[Option[Preferences]] = {
    val items = repo.collection
      .find(
        filter = findByVerificationTokenQuery(token)
      )
      .collect()
    items
      .map(i =>
        i.size match {
          case 0 => None
          case 1 => Some(i.head)
          case _ =>
            logger.error(
              s"Got more than one item for verification token $token. The chances of this are like one in a trillion so lucky you. We will return null here and this will require the user to reverify their mail"
            )
            None
        }
      )
      .toSingle()
      .toFuture()
      .recoverWith { case e =>
        logger.error(s"Unable to parse preferences for token $token due to $e")
        Future.failed(new NotFoundException(s"Unable to parse preferences for token $token"))
      }
  }

  // USED IN TESTING ONLY //
  override def countPreferencesUpdatedOn(date: Option[LocalDate])(implicit hc: HeaderCarrier): Future[Long] =
    repo.collection
      .withReadConcern(ReadConcern.LOCAL)
      .countDocuments(hasPreferencesUpdatedOnQuery(date))
      .toFuture()

  override def pullReminder(
    unverifiedEmailsBefore: => Instant,
    retryIncompleteBefore: => Instant,
    reminderField: String = firstReminder
  )(implicit hc: HeaderCarrier): Future[Option[ReminderWorkItem]] = {

    val findDueRemindersOrIncompleteBefore =
      findDueRemindersOrIncompleteBeforeQuery(unverifiedEmailsBefore, retryIncompleteBefore, reminderField)

    implicit val reminderFormat = Reminder.reminderFormat

    val setPendingEmailReminderUpdate: JsObject =
      Json.obj("$set" -> Json.obj(reminderField -> toJson(Reminder(ProcessingStatus.InProgress, Dc.instantNow()))))

    def preferencesToReminderWorkItem(prefs: Preferences): Option[ReminderWorkItem] =
      for {
        pendingEmailAddress <- prefs.pendingEmail
        verificationLink    <- pendingEmailAddress.verificationLink
      } yield ReminderWorkItem(prefs._id, pendingEmailAddress.email, verificationLink, reminderField)

    repo.collection
      .findOneAndUpdate(
        filter = findDueRemindersOrIncompleteBefore,
        update = jsobjToBson(setPendingEmailReminderUpdate),
        options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
      )
      .toSingle()
      .toFutureOption()
      .map(x =>
        x.flatMap { pref =>
          val eventsSize = pref.events.getOrElse(List.empty).size
          if (eventsSize > 10)
            logger.debug(s"[pullReminder] id: ${pref._id}, entityId: ${pref.entityId}, events: $eventsSize")
          preferencesToReminderWorkItem(pref)
        }
      )
  }

  override def setReminderSucceeded(reminder: ReminderWorkItem)(implicit hc: HeaderCarrier): Future[Boolean] =
    setReminderStatus(reminder.preferencesId, Succeeded, reminder.reminderField)

  override def setReminderFailed(reminder: ReminderWorkItem)(implicit hc: HeaderCarrier): Future[Boolean] =
    setReminderStatus(reminder.preferencesId, Failed, reminder.reminderField)

  override def updatePreferenceEventTypeAndOptInPage(
    id: ObjectId,
    event: OptPageEvent,
    events: List[Event]
  ): Future[Boolean] = {
    logger.debug(s"Migrating preference record ID ${id.toString}")
    val migrationUpdate = setEventTypeAndOptInPageUpdate(event, events)
    updateById(id, migrationUpdate)
  }

  override def updateById(id: ObjectId, update: Bson): Future[Boolean] =
    repo.collection
      .updateOne(
        filter = equal("_id", id),
        update = update
      )
      .toFuture()
      .map(r => r.wasAcknowledged())

  private def getPreferencesCollection =
    mongoComponent.database.getCollection(repo.collectionName)

  def countNocBlocks(): Future[Long] =
    getPreferencesCollection.countDocuments(Filters.exists(fieldName = "noc", exists = true)).toFuture()

  def countUpsBlocks(): Future[Long] =
    getPreferencesCollection.countDocuments(Filters.exists(fieldName = "ups", exists = true)).toFuture()

  def streamNoc(): Source[BsonDocument, NotUsed] =
    Source.fromPublisher(
      getPreferencesCollection.find[BsonDocument](Filters.exists(fieldName = "noc", exists = true))
    )

  def streamUps(): Source[BsonDocument, NotUsed] =
    Source.fromPublisher(
      getPreferencesCollection.find[BsonDocument](Filters.exists(fieldName = "ups", exists = true))
    )

  def removeNOCBlock(docs: Seq[BsonDocument]): Future[DocStats] = {
    logger.debug(s"PreferencesRepository.removeNOCBlock doc count: ${docs.size}")

    def removeNOC(doc: BsonDocument) = {

      val id = doc.getObjectId("_id")
      getPreferencesCollection
        .updateOne(
          filter = equal("_id", id.getValue),
          update = unset("noc")
        )
        .toFuture()
        .map { (a: UpdateResult) =>
          val stats = if (a.wasAcknowledged()) {
            DocStats(a.getModifiedCount)
          } else {
            logger.warn(s"[remove noc] remove NOC block from ${doc.get("_id").asString()} was not acknowledged")
            DocStats(0)
          }
          logger.debug(s"[remove noc] id: $id, stats: $stats")
          stats
        }
        .recover { case ex =>
          logger.error(s"Remove NOC block failed: ${ex.getMessage}")
          DocStats(0)
        }
    }

    Future
      .sequence {
        docs.map(a => removeNOC(a))
      }
      .map(_.fold(DocStats(0)) { (acc: DocStats, ds: DocStats) =>
        DocStats(acc.docsProcessed + ds.docsProcessed)
      })
  }

  def removeUPSBlock(docs: Seq[BsonDocument]): Future[DocStats] = {
    logger.debug(s"PreferencesRepository.removeUPSBlock doc count: ${docs.size}")

    def removeUPS(doc: BsonDocument) = {

      val id = doc.getObjectId("_id")
      getPreferencesCollection
        .updateOne(
          filter = equal("_id", id.getValue),
          update = unset("ups")
        )
        .toFuture()
        .map { (a: UpdateResult) =>
          val stats = if (a.wasAcknowledged()) {
            DocStats(a.getModifiedCount)
          } else {
            logger.warn(s"[remove ups] remove UPS block from ${doc.get("_id").asString()} was not acknowledged")
            DocStats(0)
          }
          logger.debug(s"[remove ups] id: $id, stats: $stats")
          stats
        }
        .recover { case ex =>
          logger.error(s"Remove UPS block failed: ${ex.getMessage}")
          DocStats(0)
        }
    }

    Future
      .sequence {
        docs.map(a => removeUPS(a))
      }
      .map(_.fold(DocStats(0)) { (acc: DocStats, ds: DocStats) =>
        DocStats(acc.docsProcessed + ds.docsProcessed)
      })
  }

  def stats(): Future[String] =
    mongoComponent.database
      .runCommand(BsonDocument("collStats" -> "saIndividualPreferences", "scale" -> 1))
      .toFuture()
      .map { stats =>
        val size = stats.get[BsonInt32]("size").getOrElse(BsonInt32(0)).longValue()
        val count = stats.get[BsonInt32]("count").getOrElse(BsonInt32(0)).longValue()
        val avgObjSize = stats.get[BsonInt32]("avgObjSize").getOrElse(BsonInt32(0)).longValue()
        s"collStats: [size: $size bytes], [count: $count], [avgObjSize: $avgObjSize]"
      }

}

case class BrokenVerificationLinkException(preferencesId: ObjectId)
    extends RuntimeException(s"No SA Individual Preferences entry found with ID: $preferencesId")

case class ReminderWorkItem(
  preferencesId: ObjectId,
  email: String,
  verificationLink: EmailVerificationLink,
  reminderField: String = firstReminder
)

case class MongoRemoveError(errorMessage: String)

object MongoRemoveError {
  implicit val mongoRemoveErrorFormat: OFormat[MongoRemoveError] = Json.format[MongoRemoveError]
}
