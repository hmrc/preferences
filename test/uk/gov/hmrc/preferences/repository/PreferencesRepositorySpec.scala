/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.repository

import org.apache.pekko.actor.ActorSystem
import org.bson.types.ObjectId
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.{ Filters as MongoFilters, Updates }
import org.mongodb.scala.result.UpdateResult
import org.mongodb.scala.{ ObservableFuture, SingleObservableFuture }
import org.mongodb.scala.ToSingleObservablePublisher
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.{ BeforeAndAfterAll, LoneElement, OptionValues }
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.auth.core.{ AffinityGroup, ConfidenceLevel }
import uk.gov.hmrc.http.{ HeaderCarrier, NotFoundException }
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.mongo.play.json.Codecs.JsonOps
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{ Failed, InProgress, Succeeded, ToDo }
import uk.gov.hmrc.preferences.controllers.model.EmailToken
import uk.gov.hmrc.preferences.controllers.model.Credentials
import uk.gov.hmrc.preferences.jobs.RunModeBridge
import uk.gov.hmrc.preferences.model.EmailEventType.SystemExpiredPendingEmailRemoval
import uk.gov.hmrc.preferences.model.Language.Welsh
import uk.gov.hmrc.preferences.model.PageType.IPage
import uk.gov.hmrc.preferences.model.SurveyType.StandardInterruptOptOut
import uk.gov.hmrc.preferences.model.TermsAndConditions.*
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions
import uk.gov.hmrc.preferences.util.Dc
import utils.{ GenerateRandom, LogCapturing }

import java.time.format.DateTimeFormatter
import java.time.{ Instant, LocalDate, ZoneId }
import java.util.UUID
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PreferencesRepositorySpec
    extends PlaySpec with LoneElement with MongoSupport with ScalaFutures with OptionValues with LogCapturing
    with IntegrationPatience with BeforeAndAfterAll {

  private val credentials =
    Credentials(affinityGroup = Some(AffinityGroup.Individual), ConfidenceLevel.L200)

  private val credsWithoutAffinityGroup =
    Credentials(affinityGroup = None, ConfidenceLevel.L200)

  val lockRepository: MongoLockRepository = mock[MongoLockRepository]
  val runModeBridge: RunModeBridge = mock[RunModeBridge]
  val VerificationTimeout = 30

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    def uniqueEmail: String = s"${UUID.randomUUID}@test.com"
    def inProgress(pendingEmail: PendingEmailAddress, since: Instant): PendingEmailAddress =
      pendingEmail.copy(reminder = Some(Reminder(InProgress, since)))
    def failed(pendingEmail: PendingEmailAddress, since: Instant): PendingEmailAddress =
      pendingEmail.copy(reminder = Some(Reminder(Failed, since)))
    def verificationLinkDetailsMinusDays(days: Int): EmailVerificationLink =
      EmailVerificationLink(linkSentTime = Dc.instantNow().minusDays(days))

    def unsetBounceCount(id: ObjectId): UpdateResult =
      individualRepo.repo.collection
        .updateOne(
          MongoFilters.equal("_id", id),
          Updates.unset("email.bounceCount")
        )
        .toFuture()
        .futureValue

    def unsetPendingEmailLanguage(id: ObjectId): UpdateResult =
      individualRepo.repo.collection
        .updateOne(
          MongoFilters.equal("_id", id),
          Updates.unset("pendingEmail.language")
        )
        .toFuture()
        .futureValue

    def unsetPendingEmailLanguage(entityId: EntityId): UpdateResult =
      individualRepo.repo.collection
        .updateOne(
          MongoFilters.equal("entityId", entityId.value),
          Updates.unset("pendingEmail.language")
        )
        .toFuture()
        .futureValue

    def unsetEmailLanguage(entityId: EntityId): UpdateResult =
      individualRepo.repo.collection
        .updateOne(
          MongoFilters.equal("entityId", entityId.value),
          Updates.unset("email.language")
        )
        .toFuture()
        .futureValue

    val entityId: EntityId = GenerateRandom.entityId()
    val emailAddress: String = uniqueEmail
    val reminder: Reminder = Reminder(Succeeded, Dc.instantNow())
    val limit = 20000
    val validEmail: EmailAddress = EmailAddress("test@test.com", Some(Dc.instantNow()))
    val emailWithBounceCount: EmailAddress = EmailAddress("test@test.com", Some(Dc.instantNow()), bounceCount = 666)

    val pendingEmailAddress: PendingEmailAddress =
      PendingEmailAddress(
        "bob@example.com",
        verificationLink = Some(EmailVerificationLink("id", now)),
        reminder = Some(Reminder(ToDo, now)),
        secondReminder = Some(Reminder(ToDo, now))
      )

    val pendingEmailAddressWithReminder: PendingEmailAddress =
      PendingEmailAddress(
        email = "bob@example.com",
        verificationLink = Some(EmailVerificationLink("id", now)),
        reminder = Some(Reminder(ToDo, now)),
        secondReminder = Some(Reminder(ToDo, now))
      )

    lazy val now: Instant = Dc.instantNow()
    lazy val expired: Instant = now.minusDays(VerificationTimeout + 1)
    lazy val later: Instant = now.plusHours(1)
    lazy val earlier: Instant = now.minusHours(1)

    lazy val individualRepoSomeTimeEarlier: TestRepo = TestRepo(earlier)
    lazy val individualRepo: TestRepo = TestRepo(now)
    lazy val individualRepoSometimeLater: TestRepo = TestRepo(later)

    individualRepo.repo.collection.deleteMany(MongoFilters.empty()).toFuture().futureValue
    individualRepo.repo.ensureIndexes().futureValue

    def givenOptedInAndVerifiedPreference(entityId: EntityId, email: String = validEmail.email): Preferences = {
      val pref = givenOptedInUnverifiedPreference(entityId, email, EmailVerificationLink("id", now))
      await(individualRepo.markEmailVerified(pref._id, pref.pendingEmail.get, pref.pendingEmail.get.language, None))
      val Some(preference) = await(individualRepo.findBy(entityId)): @unchecked
      preference
    }

    def givenOptedInUnverifiedPreference(
      entityId: EntityId,
      email: String,
      verificationLink: EmailVerificationLink,
      maybeReminder: Option[Reminder] = None,
      markForDeEnrolment: Boolean = false
    ): Preferences = {
      await(
        individualRepo.createOrUpdateTermsAndConditions(
          Preferences(
            entityId,
            acceptGenericTermsAndConditions(),
            pendingEmail =
              Some(PendingEmailAddress(email, verificationLink = Some(verificationLink), reminder = maybeReminder))
          ),
          credentials = Some(credentials)
        )
      ) mustBe NewPreferenceCreated
      if (markForDeEnrolment) await(individualRepo.markForDeEnrolment(entityId, "sa"))
      val pref = individualRepo.findBy(entityId).futureValue.get
      unsetPendingEmailLanguage(pref._id)
      pref
    }

    def invalidateEmail(entityId: String): Unit = {
      val _ = await(
        individualRepo.repo.collection
          .findOneAndUpdate(MongoFilters.equal("entityId", entityId), Updates.unset("email.email"))
          .toFuture()
      )
    }

    def invalidatePendingEmail(entityId: String): Unit = {
      val _ = await(
        individualRepo.repo.collection
          .findOneAndUpdate(MongoFilters.equal("entityId", entityId), Updates.unset("pendingEmail.email"))
          .toFuture()
      )
    }

    def acceptGenericTermsAndConditions(updatedAt: Instant = now): TermsAndConditions =
      TermsAndConditions(generic = Accepted(updatedAt))

    def acceptGenericTermsAndConditionsWithEventTypeAndOptInPage(updatedAt: Instant = now): TermsAndConditions =
      TermsAndConditions(generic =
        Accepted(updatedAt, Some(OptEventType.OptIn), Some(OptInPage(Version(0, 0), 7, IPage)))
      )

    val declineGenericTermsAndConditions: TermsAndConditions = TermsAndConditions(generic = Refused(now))

    def markEmailVerified(entityId: EntityId): Unit =
      await(
        individualRepo.findBy(entityId) map (_.get) flatMap (pref =>
          individualRepo.markEmailVerified(pref._id, pref.pendingEmail.get, pref.pendingEmail.get.language, None)
        )
      )

    def addBounces(): PreferenceUpdateResult =
      await(
        individualRepo.addBouncesAndClearVerificationLink(
          individualRepo.findBy(entityId).futureValue.get,
          emailBounce = Some(EmailBounce(Some(1234), now)),
          pendingEmailBounce = Some(EmailBounce(Some(1234), now)),
          shouldIncBounce = true
        )
      )

    def acceptAndVerify(repo: TestRepo): Int => Future[_] = e => {
      repo
        .createOrUpdateTermsAndConditions(
          Preferences(
            EntityId(e.toString),
            acceptGenericTermsAndConditions(),
            pendingEmail = Some(
              PendingEmailAddress("test@test.com", verificationLink = Some(EmailVerificationLink(e.toString, now)))
            )
          ),
          credentials = Some(credentials)
        )
        .futureValue
      val pref = repo.findBy(EntityId(e.toString)).futureValue.get
      repo.markEmailVerified(pref._id, pref.pendingEmail.get, pref.pendingEmail.get.language, None)
    }

    def acceptAndVerifyCustomEmail(repo: TestRepo, email: String): Int => Future[_] = e => {
      repo
        .createOrUpdateTermsAndConditions(
          Preferences(
            EntityId(e.toString),
            acceptGenericTermsAndConditions(),
            pendingEmail = Some(
              PendingEmailAddress(email, verificationLink = Some(EmailVerificationLink(e.toString, now)))
            )
          ),
          credentials = Some(credentials)
        )
        .futureValue
      val pref = repo.findBy(EntityId(e.toString)).futureValue.get
      repo.markEmailVerified(pref._id, pref.pendingEmail.get, pref.pendingEmail.get.language, None)
    }

    def withPreference(addNocBlock: Boolean = true, addUpsBlock: Boolean = true): Unit = {
      val objectId = ObjectId.get().toString
      val entityId = GenerateRandom.entityId().value
      val dateNow = DateTimeFormatter.ISO_INSTANT.format(Dc.instantNow())

      val sb: StringBuilder = new StringBuilder
      sb ++=
        s"""
           |{ 
           |  "_id" : ObjectId("$objectId"),
           |  "entityId" : "$entityId", 
           |  "createdAt" : ISODate("$dateNow"), 
           |  "pendingEmail" : {
           |    "email" : "test@test.co.uk", 
           |  },
           |  "termsAndConditions" : { "generic" : { "accepted" : true, "updatedAt" : ISODate("$dateNow") } },
           |  "updatedAt" : ISODate("$dateNow")
           |""".stripMargin

      if (addNocBlock) {
        sb ++=
          s"""
             |  ,"noc" : { 
             |    "createdAt" : ISODate("$dateNow"), 
             |    "failureCount" : 0,
             |    "status" : "todo", 
             |    "updatedAt" : ISODate("$dateNow") 
             |  }
             |""".stripMargin
      }

      if (addUpsBlock) {
        sb ++=
          s"""
             |  ,"ups": {
             |    "createdAt": ISODate ("$dateNow"),
             |    "failureCount": 0,
             |    "status": "in-progress",
             |    "updatedAt": ISODate ("$dateNow")
             |  }
             |
             |""".stripMargin
      }

      sb ++=
        """}
          |""".stripMargin

      val inserted = mongoDatabase
        .getCollection("saIndividualPreferences")
        .insertOne(Document(sb.mkString))
        .toFuture()
        .futureValue

      inserted.wasAcknowledged() mustBe true
      ()
    }
  }

  "Preferences respository" should {
    "get preference model" in new Setup {
      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(
            entityId,
            acceptGenericTermsAndConditions(),
            pendingEmail = Some(pendingEmailAddress)
          ),
          credentials = Some(credentials)
        )
        .futureValue must be(NewPreferenceCreated)

    }
  }

  "acceptGenericTermsAndConditions" should {

    "save a new sa individual preference" in new Setup {
      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(
            entityId,
            acceptGenericTermsAndConditions(),
            pendingEmail = Some(pendingEmailAddress)
          ),
          credentials = Some(credentials)
        )
        .futureValue must be(NewPreferenceCreated)

      private val Some(result) = individualRepo.findBy(entityId).futureValue: @unchecked
      result must have(
        Symbol("entityId")(entityId),
        Symbol("updatedAt")(now),
        Symbol("createdAt")(now),
        Symbol("pendingEmail")(Some(pendingEmailAddressWithReminder)),
        Symbol("email")(None),
        Symbol("userType")(Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))),
        Symbol("termsAndConditions")(TermsAndConditions(Accepted(now)))
      )
    }

    "save a new sa individual preference and log the fact that an affinity group is missing when retrieving" in new Setup {

      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(
            entityId,
            acceptGenericTermsAndConditions(),
            pendingEmail = Some(pendingEmailAddress)
          ),
          credentials = Some(credsWithoutAffinityGroup)
        )
        .futureValue must be(NewPreferenceCreated)
    }

    "set terms and conditions for previously opted-out preference" in new Setup {
      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(entityId, declineGenericTermsAndConditions),
          credentials = Some(credentials)
        )
        .futureValue must be(NewPreferenceCreated)

      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(
            entityId,
            acceptGenericTermsAndConditions(),
            pendingEmail = Some(pendingEmailAddress)
          ),
          credentials = Some(credentials)
        )
        .futureValue must be(PreferenceUpdated)

      private val Some(result) = individualRepo.findBy(entityId).futureValue: @unchecked
      result must have(
        Symbol("entityId")(entityId),
        Symbol("updatedAt")(now),
        Symbol("createdAt")(now),
        Symbol("pendingEmail")(
          Some(
            PendingEmailAddress(
              email = "bob@example.com",
              verificationLink = Some(EmailVerificationLink("id", now)),
              reminder = Some(Reminder(ToDo, now)),
              secondReminder = Some(Reminder(ToDo, now))
            )
          )
        ),
        Symbol("email")(None),
        Symbol("userType")(Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))),
        Symbol("termsAndConditions")(TermsAndConditions(Accepted(now)))
      )
    }

    "not throw an duplicate key error with near simultaneous optIns" in new Setup {
      private val entityIds = List.fill(10)(entityId)

      await(Future.sequence(entityIds.map { entityId =>
        Thread.sleep(1000)
        individualRepo.createOrUpdateTermsAndConditions(
          Preferences(
            entityId,
            acceptGenericTermsAndConditions(),
            pendingEmail = Some(pendingEmailAddress)
          ),
          credentials = Some(credentials)
        )
      }))

      individualRepo.repo.collection.countDocuments().toFuture().futureValue mustBe 1

      individualRepo.findBy(entityId).futureValue.get must have(
        Symbol("entityId")(entityId),
        Symbol("updatedAt")(now),
        Symbol("createdAt")(now),
        Symbol("pendingEmail")(
          Some(
            PendingEmailAddress(
              email = "bob@example.com",
              verificationLink = Some(EmailVerificationLink("id", now)),
              reminder = Some(Reminder(ToDo, now)),
              secondReminder = Some(Reminder(ToDo, now))
            )
          )
        ),
        Symbol("email")(None),
        Symbol("userType")(Some(UserType(Some(Individual), Some(ConfidenceLevel.L200))))
      )
    }

    "update events" in new Setup {
      val preferences = Preferences(
        entityId,
        acceptGenericTermsAndConditions(),
        pendingEmail = Some(pendingEmailAddress)
      )

      import uk.gov.hmrc.preferences.scheduled.getEmailEvent
      val preferencesWithEvent = preferences.copy(events = Some(List(getEmailEvent(preferences))))

      individualRepo
        .createOrUpdateTermsAndConditions(
          preferencesWithEvent,
          credentials = Some(credentials)
        )
        .futureValue

      individualRepo.findBy(entityId).futureValue.get.events.getOrElse(List.empty).size mustBe 1

      individualRepo
        .createOrUpdateTermsAndConditions(
          preferencesWithEvent.copy(events = preferencesWithEvent.events.map(getEmailEvent(preferences) :: _)),
          credentials = Some(credentials)
        )
        .futureValue

      individualRepo.findBy(entityId).futureValue.get.events.getOrElse(List.empty).size mustBe 2
    }
  }

  "updateTermsAndConditions" should {

    "keep email information when opting out from a service if there is at least one opted it" in new Setup {
      private val optInForAllServices = TermsAndConditions(Accepted(now))
      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(
            entityId,
            optInForAllServices,
            pendingEmail = Some(pendingEmailAddress)
          ),
          credentials = Some(credentials)
        )
        .futureValue must be(NewPreferenceCreated)

      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(entityId, TermsAndConditions(Accepted(now))),
          credentials = Some(credentials)
        )
        .futureValue must be(PreferenceMatched)

      private val Some(result) = individualRepo.findBy(entityId).futureValue: @unchecked
      result must have(
        Symbol("entityId")(entityId),
        Symbol("updatedAt")(now),
        Symbol("createdAt")(now),
        Symbol("pendingEmail")(
          Some(
            PendingEmailAddress(
              email = "bob@example.com",
              verificationLink = Some(EmailVerificationLink("id", now)),
              reminder = Some(Reminder(ToDo, now)),
              secondReminder = Some(Reminder(ToDo, now))
            )
          )
        ),
        Symbol("email")(None),
        Symbol("userType")(Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))),
        Symbol("termsAndConditions")(TermsAndConditions(Accepted(now)))
      )
    }

    "delete email information when opting out from a service if all of them are opted out" in new Setup {
      private val optInForAllServices = TermsAndConditions(Accepted(now))
      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(
            entityId,
            optInForAllServices,
            pendingEmail = Some(pendingEmailAddress)
          ),
          credentials = Some(credentials)
        )
        .futureValue must be(NewPreferenceCreated)

      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(entityId, TermsAndConditions(Refused(now))),
          credentials = Some(credentials)
        )
        .futureValue must be(PreferenceUpdated)

      private val Some(result) = individualRepo.findBy(entityId).futureValue: @unchecked
      result must have(
        Symbol("entityId")(entityId),
        Symbol("updatedAt")(now),
        Symbol("createdAt")(now),
        Symbol("pendingEmail")(None),
        Symbol("email")(None),
        Symbol("userType")(Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))),
        Symbol("termsAndConditions")(TermsAndConditions(Refused(now)))
      )
    }

    "not throw an duplicate key error with near simultaneous optIns" in new Setup {
      private val entityIds = List.fill(10)(entityId)

      await(Future.sequence(entityIds.map { entityId =>
        Thread.sleep(5)
        individualRepo.createOrUpdateTermsAndConditions(
          Preferences(
            entityId,
            TermsAndConditions(Accepted(now)),
            pendingEmail = Some(pendingEmailAddress)
          ),
          credentials = Some(credentials)
        )
      }))

      individualRepo.repo.collection.countDocuments().toFuture().futureValue mustBe 1

      individualRepo.findBy(entityId).futureValue.get must have(
        Symbol("entityId")(entityId),
        Symbol("updatedAt")(now),
        Symbol("createdAt")(now),
        Symbol("pendingEmail")(Some(pendingEmailAddressWithReminder)),
        Symbol("email")(None),
        Symbol("userType")(Some(UserType(Some(Individual), Some(ConfidenceLevel.L200))))
      )
    }
  }

  "optOutOfDigital" should {

    "save a new SA Individual preference" in new Setup {
      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(
            entityId,
            declineGenericTermsAndConditions,
            surveys = Some(List(Survey(StandardInterruptOptOut, now)))
          ),
          credentials = Some(credentials)
        )
        .futureValue must be(NewPreferenceCreated)

      await(individualRepo.findBy(entityId)).get must have(
        Symbol("entityId")(entityId),
        Symbol("email")(None),
        Symbol("pendingEmail")(None),
        Symbol("termsAndConditions")(TermsAndConditions(generic = Refused(now))),
        Symbol("createdAt")(now),
        Symbol("userType")(Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))),
        Symbol("updatedAt")(now),
        Symbol("surveys")(Some(List(Survey(StandardInterruptOptOut, now))))
      )
    }

    "save a new SA Individual preference and update with a second survey" in new Setup {
      val eid = entityId
      val p = Preferences(
        entityId = eid,
        termsAndConditions = declineGenericTermsAndConditions,
        surveys = Some(List(Survey(StandardInterruptOptOut, now)))
      )

      individualRepo
        .createOrUpdateTermsAndConditions(p, credentials = Some(credentials))
        .futureValue must be(NewPreferenceCreated)

      individualRepo
        .createOrUpdateTermsAndConditions(
          p.copy(
            surveys = Some(p.surveys.get ++ List(Survey(StandardInterruptOptOut, now.plusMillis(100))))
          ),
          credentials = Some(credentials)
        )
        .futureValue must be(PreferenceUpdated)

      await(individualRepo.findBy(entityId)).get must have(
        Symbol("entityId")(entityId),
        Symbol("email")(None),
        Symbol("pendingEmail")(None),
        Symbol("termsAndConditions")(TermsAndConditions(generic = Refused(now))),
        Symbol("createdAt")(now),
        Symbol("userType")(Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))),
        Symbol("updatedAt")(now),
        Symbol("surveys")(
          Some(List(Survey(StandardInterruptOptOut, now), Survey(StandardInterruptOptOut, now.plusMillis(100))))
        )
      )
    }

    "not throw a duplicated key error with multiple near simultaneous declines" in new Setup {
      Future
        .sequence(Seq.fill(10) {
          Thread.sleep(1)
          individualRepo
            .createOrUpdateTermsAndConditions(
              Preferences(entityId, declineGenericTermsAndConditions),
              credentials = Some(credentials)
            )
        })
        .futureValue

      individualRepoSometimeLater.repo.collection.countDocuments().toFuture().futureValue mustBe 1

      private val savedPreference = await(individualRepo.findBy(entityId)).get

      savedPreference must have(
        Symbol("entityId")(entityId),
        Symbol("email")(None),
        Symbol("pendingEmail")(None),
        Symbol("termsAndConditions")(TermsAndConditions(generic = Refused(now))),
        Symbol("createdAt")(now),
        Symbol("userType")(Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))),
        Symbol("updatedAt")(now)
      )
    }

    "update an existing SA Individual preference that has a verified email address" in new Setup {
      givenOptedInAndVerifiedPreference(entityId, email = "foo@example.com")

      individualRepoSometimeLater
        .createOrUpdateTermsAndConditions(
          Preferences(entityId, TermsAndConditions(generic = Refused(later))),
          credentials = Some(credentials)
        )
        .futureValue

      individualRepoSometimeLater.repo.collection.countDocuments().toFuture().futureValue mustBe 1

      private val savedPreference = await(individualRepoSometimeLater.findBy(entityId)).get
      savedPreference must have(
        Symbol("entityId")(entityId),
        Symbol("email")(None),
        Symbol("pendingEmail")(None),
        Symbol("termsAndConditions")(TermsAndConditions(generic = Refused(later))),
        Symbol("createdAt")(now),
        Symbol("userType")(Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))),
        Symbol("updatedAt")(later)
      )
    }

    "update an existing SA Individual preference that has both verified and pending email addresses" in new Setup {
      givenOptedInAndVerifiedPreference(entityId, email = "foo@example.com")

      await(individualRepo.setUnverifiedEmailAddress(entityId, PendingEmailAddress("pending@somewhere.com"), Seq.empty))

      individualRepoSometimeLater
        .createOrUpdateTermsAndConditions(
          Preferences(entityId, TermsAndConditions(generic = Refused(later))),
          credentials = Some(credentials)
        )
        .futureValue

      individualRepoSometimeLater.repo.collection.countDocuments().toFuture().futureValue mustBe 1

      private val savedPreference = await(individualRepoSometimeLater.findBy(entityId)).get
      savedPreference must have(
        Symbol("entityId")(entityId),
        Symbol("email")(None),
        Symbol("pendingEmail")(None),
        Symbol("termsAndConditions")(TermsAndConditions(generic = Refused(later))),
        Symbol("createdAt")(now),
        Symbol("userType")(Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))),
        Symbol("updatedAt")(later)
      )
    }

    "Admin user can optOut a user with out effecting credentials" in new Setup {
      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(
            entityId,
            acceptGenericTermsAndConditions(),
            pendingEmail = Some(pendingEmailAddress)
          ),
          credentials = Some(credentials)
        )
        .futureValue must be(NewPreferenceCreated)

      private val Some(optInResult) = individualRepo.findBy(entityId).futureValue: @unchecked
      optInResult must have(
        Symbol("entityId")(entityId),
        Symbol("userType")(Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))),
        Symbol("termsAndConditions")(TermsAndConditions(generic = Accepted(now)))
      )

      individualRepo
        .markEmailVerified(optInResult._id, optInResult.pendingEmail.get, language = None, event = None)
        .futureValue

      private val Some(verifiedResult) = individualRepo.findBy(entityId).futureValue: @unchecked
      verifiedResult must have(
        Symbol("entityId")(entityId),
        Symbol("userType")(Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))),
        Symbol("termsAndConditions")(TermsAndConditions(generic = Accepted(now))),
        Symbol("pendingEmail")(None)
      )

      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(entityId, declineGenericTermsAndConditions),
          credentials = None
        )
        .futureValue must be(PreferenceUpdated)

      private val Some(optOutResult) = individualRepo.findBy(entityId).futureValue: @unchecked
      optOutResult must have(
        Symbol("entityId")(entityId),
        Symbol("userType")(Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))),
        Symbol("termsAndConditions")(TermsAndConditions(generic = Refused(now)))
      )
    }
  }

  "removeById" should {
    "be Ok to delete the preference record for the given id" in new Setup {
      val pref = givenOptedInAndVerifiedPreference(GenerateRandom.entityId())
      await(individualRepo.findBy(pref.entityId)) must be(Symbol("defined"))
      individualRepo.removeById(pref._id).futureValue must be(true)
    }
    "still be Ok to delete the preference record for the given id which is not in DB" in new Setup {
      val id = ObjectId.get()
      await(individualRepo.findPreferencesById(id)) must be(None)
      individualRepo.removeById(id).futureValue must be(true)
    }
  }

  "findPreferencesById" should {
    "find a entityId if it exists for the id" in new Setup {
      private val preference =
        givenOptedInUnverifiedPreference(entityId, "foo@example.com", EmailVerificationLink("id", now))

      await(individualRepo.findPreferencesById(preference._id)) must be(Some(preference))
    }

    "not find an entityId if it does not exist for the id" in new Setup {
      await(individualRepo.findPreferencesById(ObjectId.get())) must be(None)
    }

    "return NotFoundException if it fails to parse the invalid preference" in new Setup {
      private val preference = givenOptedInAndVerifiedPreference(entityId)
      invalidateEmail(entityId.value)
      assertThrows[RuntimeException] {
        await(individualRepo.repo.collection.find(MongoFilters.equal("_id", preference._id)).toFuture())
      }
      assertThrows[NotFoundException] {
        await(individualRepo.findPreferencesById(preference._id))
      }
    }
  }

  "findBy EntityId" should {
    "find a preference if it exists for the entityId" in new Setup {
      givenOptedInUnverifiedPreference(entityId, "foo@example.com", EmailVerificationLink("id", now))
      await(individualRepo.findBy(entityId)) must be(Symbol("defined"))
    }

    "not find a preference if it does not exist for the entityId" in new Setup {
      await(individualRepo.findBy(GenerateRandom.entityId())) must not be Symbol("defined")
    }

    "return None & delete the preference if it fails to parse the invalid preference" in new Setup {
      private val preference = givenOptedInAndVerifiedPreference(entityId)
      invalidateEmail(entityId.value)

      // Bad preference since email.email is missing, so will cause an error
      assertThrows[RuntimeException] {
        await(individualRepo.repo.collection.find(MongoFilters.equal("_id", preference._id)).toFuture())
      }
      await(individualRepo.findBy(entityId)) must be(None)
      individualRepo.repo.ensureIndexes().futureValue

      await {
        individualRepo.repo.collection.find(MongoFilters.equal("_id", preference._id)).toSingle().toFutureOption()
      } must be(None)
    }
  }

  "unique indexes" should {

    "allow multiple preferences with the same email address" in new Setup {
      givenOptedInAndVerifiedPreference(EntityId("112233"), email = "test@test.com")
      givenOptedInAndVerifiedPreference(EntityId("112234"), email = "test@test.com")

      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(
            EntityId("112235"),
            acceptGenericTermsAndConditions(),
            pendingEmail =
              Some(PendingEmailAddress("test2@test.com", verificationLink = Some(EmailVerificationLink("id", now))))
          ),
          credentials = Some(credentials)
        )
        .futureValue must be(NewPreferenceCreated)
      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(
            EntityId("112236"),
            acceptGenericTermsAndConditions(),
            pendingEmail =
              Some(PendingEmailAddress("test2@test.com", verificationLink = Some(EmailVerificationLink("id", now))))
          ),
          credentials = Some(credentials)
        )
        .futureValue must be(NewPreferenceCreated)
    }
  }

  "findByEmail" should {

    "return a preference if it exists for an email address" in new Setup {
      private val email = "test@test.com"

      private val thePreference = Preferences(
        entityId = entityId,
        termsAndConditions = TermsAndConditions(Accepted(now)),
        email = Some(
          EmailAddress(email = email, verifiedOn = Some(now), verifiedWithLink = Some(EmailVerificationLink("id", now)))
        ),
        createdAt = now,
        updatedAt = now,
        userType = Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))
      )
      private val pref = givenOptedInAndVerifiedPreference(entityId, email)

      individualRepo.findByEmail(email).futureValue mustBe Seq(thePreference.copy(_id = pref._id))
    }

    "return a preference if it exists for an email address ignoring case" in new Setup {
      private val email = "test@Test.com"

      private val thePreference = Preferences(
        entityId = entityId,
        termsAndConditions = TermsAndConditions(Accepted(now)),
        email = Some(
          EmailAddress(email = email, verifiedOn = Some(now), verifiedWithLink = Some(EmailVerificationLink("id", now)))
        ),
        createdAt = now,
        updatedAt = now,
        userType = Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))
      )
      private val pref = givenOptedInAndVerifiedPreference(entityId, email)

      individualRepo.findByEmail(email.toLowerCase).futureValue mustBe Seq(thePreference.copy(_id = pref._id))
      individualRepo.findByEmail(email.toUpperCase).futureValue mustBe Seq(thePreference.copy(_id = pref._id))
    }

    "return a preference if it exists for a pending email address ignoring case" in new Setup {
      private val email = "test@Test.com"

      private val thePreference = Preferences(
        entityId = entityId,
        termsAndConditions = TermsAndConditions(Accepted(now)),
        pendingEmail = Some(
          PendingEmailAddress(
            email = email,
            verificationLink = Some(EmailVerificationLink("id", now)),
            reminder = Some(Reminder(ToDo, now)),
            secondReminder = Some(Reminder(ToDo, now))
          )
        ),
        createdAt = now,
        updatedAt = now,
        userType = Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))
      )

      private val pref =
        givenOptedInUnverifiedPreference(entityId, email, EmailVerificationLink("id", now), Some(Reminder(ToDo, now)))

      individualRepo.findByEmail(email.toLowerCase).futureValue mustBe Seq(thePreference.copy(_id = pref._id))
      individualRepo.findByEmail(email.toUpperCase).futureValue mustBe Seq(thePreference.copy(_id = pref._id))
    }

    "return an empty list if the preference does not exist for an email address" in new Setup {
      givenOptedInUnverifiedPreference(entityId, "test@test.com", EmailVerificationLink("id", now))

      await(individualRepo.findByEmail("different@email")) mustBe Seq.empty
    }

    "return NotFoundException if it fails to parse the invalid preference" in new Setup {
      private val preference =
        givenOptedInUnverifiedPreference(entityId, "test@test.com", EmailVerificationLink("id", now))
      invalidatePendingEmail(entityId.value)
      assertThrows[RuntimeException] {
        await(individualRepo.repo.collection.find(MongoFilters.equal("_id", preference._id)).toFuture())
      }
      assertThrows[NotFoundException] {
        await(individualRepo.findByEmail("test@test.com"))
      }
    }
  }

  "findUnverifiedExpired" should {
    "return all preferences with an expired email verification link" in new Setup {
      private val pref2 =
        givenOptedInUnverifiedPreference(
          GenerateRandom.entityId(),
          "foo1@example.com",
          EmailVerificationLink("id", expired)
        )

      givenOptedInUnverifiedPreference(GenerateRandom.entityId(), "foo1@example.com", EmailVerificationLink("id", now))
      private val pref1 =
        givenOptedInUnverifiedPreference(entityId, "foo@example.com", EmailVerificationLink("id", expired))

      individualRepo.findUnverifiedExpired(now).futureValue mustBe Seq(pref2, pref1)
    }

    "do not return expired preference if there is a pre verified email" in new Setup {
      givenOptedInAndVerifiedPreference(entityId, validEmail.email)
      (for {
        _ <- individualRepo.setUnverifiedEmailAddress(
               entityId,
               PendingEmailAddress(
                 "bob@example.com",
                 verificationLink = Some(EmailVerificationLink(linkSentTime = expired))
               ),
               Seq.empty
             )
        p <- individualRepo.findUnverifiedExpired(now)
      } yield p).futureValue.size mustBe 0
    }
  }

  "findExpiredRecordsForDeEnrolment" should {
    "return only the preferences having flagged 'markForDeEnrolment' and expired" in new Setup {
      val p1 =
        givenOptedInUnverifiedPreference(
          GenerateRandom.entityId(),
          "foo1@example.com",
          EmailVerificationLink("id", expired),
          markForDeEnrolment = true
        )
      val p2 =
        givenOptedInUnverifiedPreference(
          GenerateRandom.entityId(),
          "foo2@example.com",
          EmailVerificationLink("id", expired),
          markForDeEnrolment = true
        )

      givenOptedInUnverifiedPreference(
        GenerateRandom.entityId(),
        "foo2@example.com",
        EmailVerificationLink("id", expired)
      )

      individualRepo.findExpiredRecordsForDeEnrolment(2, now.plusDays(28)).futureValue mustBe Seq(p1, p2)
    }
    "return no preferences, having flagged 'markForDeEnrolment' but no records are expired" in new Setup {
      givenOptedInUnverifiedPreference(
        GenerateRandom.entityId(),
        "foo1@example.com",
        EmailVerificationLink("id", expired),
        markForDeEnrolment = true
      )

      givenOptedInUnverifiedPreference(
        GenerateRandom.entityId(),
        "foo2@example.com",
        EmailVerificationLink("id", expired),
        markForDeEnrolment = true
      )

      individualRepo.findExpiredRecordsForDeEnrolment(2, now.minusDays(28)).futureValue mustBe Seq()
    }
    "return no preferences, when no records are flagged with 'markForDeEnrolment'" in new Setup {

      givenOptedInUnverifiedPreference(
        GenerateRandom.entityId(),
        "foo1@example.com",
        EmailVerificationLink("id", expired),
        markForDeEnrolment = false
      )

      givenOptedInUnverifiedPreference(
        GenerateRandom.entityId(),
        "foo2@example.com",
        EmailVerificationLink("id", expired),
        markForDeEnrolment = false
      )

      individualRepo.findExpiredRecordsForDeEnrolment(2, now).futureValue mustBe Seq()
    }
  }

  "findUnverifiedTwoEmailsExpired" should {
    "return a preference with a verified email and expired pending" in new Setup {
      givenOptedInAndVerifiedPreference(entityId, validEmail.email)
      (for {
        _ <- individualRepo.setUnverifiedEmailAddress(
               entityId,
               PendingEmailAddress(
                 "bob@example.com",
                 verificationLink = Some(EmailVerificationLink(linkSentTime = expired))
               ),
               Seq.empty
             )
        p <- individualRepo.findUnverifiedTwoEmailsExpired(now)
      } yield p).futureValue.size mustBe 1
    }

    "not return a preference with a verified email and un-expired pending" in new Setup {
      givenOptedInAndVerifiedPreference(entityId, validEmail.email)
      (for {
        _ <- individualRepo.setUnverifiedEmailAddress(
               entityId,
               PendingEmailAddress(
                 "bob@example.com",
                 verificationLink = Some(EmailVerificationLink(linkSentTime = later))
               ),
               Seq.empty
             )
        p <- individualRepo.findUnverifiedTwoEmailsExpired(now)
      } yield p).futureValue.size mustBe 0
    }

    import uk.gov.hmrc.preferences.scheduled.getEmailEvent
    "not find a preference once the pending email has been unset" in new Setup {
      givenOptedInAndVerifiedPreference(entityId, validEmail.email)
      (for {
        _ <- individualRepo.setUnverifiedEmailAddress(
               entityId,
               PendingEmailAddress(
                 "bob@example.com",
                 verificationLink = Some(EmailVerificationLink(linkSentTime = expired))
               ),
               Seq.empty
             )
        p1 <- individualRepo.findUnverifiedTwoEmailsExpired(now)
        _  <- individualRepo.unsetPendingEmail(p1.head.entityId, getEmailEvent(p1.head))
        p2 <- individualRepo.findUnverifiedTwoEmailsExpired(now)
      } yield p2).futureValue.size mustBe 0
    }

    "an unset email should have an EmailEvent with action SystemExpiredPendingEmailRemoval" in new Setup {
      givenOptedInAndVerifiedPreference(entityId, validEmail.email)
      ((for {
        _ <- individualRepo.setUnverifiedEmailAddress(
               entityId,
               PendingEmailAddress(
                 "bob@example.com",
                 verificationLink = Some(EmailVerificationLink(linkSentTime = expired))
               ),
               Seq.empty
             )
        p1 <- individualRepo.findUnverifiedTwoEmailsExpired(now)
        _  <- individualRepo.unsetPendingEmail(p1.head.entityId, getEmailEvent(p1.head))
        p2 <- individualRepo.findBy(entityId)
      } yield p2).futureValue.head.events match {
        case Some(EmailEvent(_, SystemExpiredPendingEmailRemoval, _, _, _) :: Nil) => true
        case _                                                                     => false
      }) mustBe true
    }

  }

  "findByVerificationToken" should {
    "find a preference with a pending email with the corresponding link" in new Setup {
      private val verificationLink = EmailVerificationLink(linkSentTime = Dc.instantNow().minusDays(15))
      private val preferences: Preferences = givenOptedInUnverifiedPreference(
        entityId,
        "a@b.com",
        verificationLink
      )

      individualRepo.findByVerificationToken(EmailToken(verificationLink._id)).futureValue mustBe Some(preferences)
    }

    "find a preference with a verified email with the corresponding link" in new Setup {
      private val verificationLink = EmailVerificationLink(linkSentTime = Dc.instantNow().minusDays(15))
      givenOptedInUnverifiedPreference(
        entityId,
        "a@b.com",
        verificationLink
      )
      markEmailVerified(entityId)

      private val verifiedPreferences = individualRepo.findBy(entityId).futureValue.get
      individualRepo.findByVerificationToken(EmailToken(verificationLink._id)).futureValue.get must be(
        verifiedPreferences
      )
    }

    "return None if no preference has the verification token" in new Setup {
      await(individualRepo.findByVerificationToken(EmailToken("this is not the token you are looking for"))) mustBe None
    }

    "return NotFoundException if it fails to parse the invalid preference" in new Setup {
      private val verificationLink = EmailVerificationLink(linkSentTime = Dc.instantNow().minusDays(15))
      private val preference =
        givenOptedInUnverifiedPreference(entityId, "test@test.com", verificationLink)
      invalidatePendingEmail(entityId.value)
      assertThrows[RuntimeException] {
        await(individualRepo.repo.collection.find(MongoFilters.equal("_id", preference._id)).toFuture())
      }
      assertThrows[NotFoundException] {
        await(individualRepo.findByVerificationToken(EmailToken(verificationLink._id)))
      }
    }
  }

  "countPreferencesUpdatedOn" should {

    "return total number of entries for a specified date" in new Setup {
      override lazy val later: Instant = Dc.instantNow()
      override lazy val now: Instant = later.minusDays(1)
      override lazy val earlier: Instant = later.minusDays(2)

      (0 to 3).map(acceptAndVerify(individualRepoSomeTimeEarlier))
      (4 to 6).map(acceptAndVerify(individualRepo))
      (7 to 9).map(acceptAndVerify(individualRepoSometimeLater))
      await(individualRepo.countPreferencesUpdatedOn(Some(LocalDate.now(ZoneId.of("UTC"))))) mustBe 3
    }

    "return total number of all entries if the date is not set" in new Setup {
      override lazy val later: Instant = Dc.instantNow()
      override lazy val now: Instant = later.minusDays(1)
      override lazy val earlier: Instant = later.minusDays(2)

      (0 to 3).map(acceptAndVerify(individualRepoSomeTimeEarlier))
      (4 to 6).map(acceptAndVerify(individualRepo))
      (7 to 9).map(acceptAndVerify(individualRepoSometimeLater))
      await(individualRepo.countPreferencesUpdatedOn(None)) mustBe 10
    }
  }

  "markEmailVerified" should {

    "move the pending email address and its verification link to the email address field and remove the pending address when no email address currently exists" in new Setup {

      private val verificationLink = EmailVerificationLink(linkSentTime = now)

      private val initialPreferences = givenOptedInUnverifiedPreference(entityId, "test1@test.com", verificationLink)

      individualRepo
        .markEmailVerified(initialPreferences._id, initialPreferences.pendingEmail.get, Some(Welsh), None)
        .futureValue

      private val found = await(individualRepo.findBy(entityId))

      private val expected = initialPreferences.copy(
        pendingEmail = None,
        email = Some(
          EmailAddress(
            email = "test1@test.com",
            lastBounce = None,
            verifiedOn = Some(now),
            verifiedWithLink = Some(verificationLink),
            language = Some(Welsh)
          )
        ),
        updatedAt = now
      )

      found mustBe Some(expected)
    }

    "replace an existing email address when the pending email address is validated and check the new email address is stored" in new Setup {
      private val link = EmailVerificationLink(linkSentTime = now)
      private val initialPreferences = givenOptedInUnverifiedPreference(entityId, "test1@test.com", link)

      individualRepo
        .markEmailVerified(initialPreferences._id, initialPreferences.pendingEmail.get, Some(Welsh), None)
        .futureValue

      private val found = await(individualRepo.findBy(entityId))

      private val expected = initialPreferences.copy(
        pendingEmail = None,
        email = Some(
          EmailAddress(
            email = "test1@test.com",
            lastBounce = None,
            verifiedOn = Some(now),
            verifiedWithLink = Some(link),
            language = Some(Welsh)
          )
        ),
        updatedAt = now
      )

      found mustBe Some(expected)
    }

    "throw a BrokenVerificationLinkException if there is no preference entry corresponding to the supplied ID" in new Setup {
      a[BrokenVerificationLinkException] should be thrownBy {
        await(
          individualRepo.markEmailVerified(
            ObjectId.get(),
            PendingEmailAddress("test1@test.com", lastBounce = Some(EmailBounce(Some(3234), now))),
            Some(Welsh),
            None
          )
        )
      }
    }

    "throw a BrokenVerificationLinkException if there is preference entry corresponding to the supplied ID but its pendingEmail is bounced" in new Setup {
      private val bounce = EmailBounce(Some(3234), now.minusHours(1))

      private val initialPreferences =
        givenOptedInUnverifiedPreference(entityId, "test1@test.com", EmailVerificationLink(linkSentTime = now))
      await(
        individualRepo
          .addBouncesAndClearVerificationLink(initialPreferences, None, Some(bounce), shouldIncBounce = true)
      )

      a[BrokenVerificationLinkException] should be thrownBy await(
        individualRepo.markEmailVerified(ObjectId.get(), initialPreferences.pendingEmail.get, Some(Welsh), None)
      )
    }

    "throw a BrokenVerificationLinkException if there is preference entry corresponding to the supplied ID but it is opted out" in new Setup {

      private val initialPreferences =
        givenOptedInUnverifiedPreference(entityId, "test1@test.com", EmailVerificationLink(linkSentTime = now))
      await(
        individualRepo
          .createOrUpdateTermsAndConditions(
            Preferences(entityId, declineGenericTermsAndConditions),
            credentials = Some(credentials)
          )
      )

      a[BrokenVerificationLinkException] should be thrownBy await(
        individualRepo.markEmailVerified(ObjectId.get(), initialPreferences.pendingEmail.get, Some(Welsh), None)
      )
    }
  }

  "addBouncesAndClearVerificationLink" should {

    "Mark main email as bounced in the repo if supplied" in new Setup {

      private val email = "foo@foo.com"
      private val initialPreferences = givenOptedInAndVerifiedPreference(entityId, email)

      individualRepo
        .setUnverifiedEmailAddress(
          entityId,
          PendingEmailAddress(email, verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow()))),
          Seq.empty
        )
        .futureValue

      await(
        individualRepoSometimeLater.addBouncesAndClearVerificationLink(
          initialPreferences,
          emailBounce = Some(EmailBounce(Some(1234), now)),
          pendingEmailBounce = None,
          shouldIncBounce = true
        )
      )

      private val found = await(individualRepo.findBy(entityId)).get
      found.email.get must be(Symbol("bounced"))
      found.pendingEmail.get must not be Symbol("bounced")
      found.pendingEmail.get.verificationLink must not be empty
      found.updatedAt must be(later)
    }

    "Mark email and pending email as bounced in the repo if supplied" in new Setup {
      private val email = "foo@foo.com"

      private val initialPreferences = givenOptedInAndVerifiedPreference(entityId, email)
      individualRepo
        .setUnverifiedEmailAddress(
          entityId,
          PendingEmailAddress(email, verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow()))),
          Seq.empty
        )
        .futureValue

      await(
        individualRepoSometimeLater.addBouncesAndClearVerificationLink(
          initialPreferences,
          emailBounce = Some(EmailBounce(Some(1234), now)),
          pendingEmailBounce = Some(EmailBounce(Some(1234), now)),
          shouldIncBounce = true
        )
      )

      private val found: Preferences = await(individualRepo.findBy(entityId)).get
      found.email.get must be(Symbol("bounced"))
      found.pendingEmail.get must be(Symbol("bounced"))
      found.pendingEmail.get.verificationLink must be(empty)
      found.updatedAt must be(later)
    }

    "Do not increment the bounce counter when there are no bounces" in new Setup {
      private val email = "foo@foo.com"
      givenOptedInAndVerifiedPreference(entityId, email)

      individualRepo
        .setUnverifiedEmailAddress(
          entityId,
          PendingEmailAddress(email, verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow()))),
          event = Seq.empty
        )
        .futureValue

      private val found: Preferences = await(individualRepo.findBy(entityId)).get
      found.email.get.bounceCount mustBe 0
    }

    "Increment the bounce counter when supplied with an emailBounce" in new Setup {
      private val email = "foo@foo.com"

      givenOptedInAndVerifiedPreference(entityId, email)

      individualRepo
        .setUnverifiedEmailAddress(
          entityId,
          PendingEmailAddress(email, verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow()))),
          event = Seq.empty
        )
        .futureValue

      for (i <- 1 to 5) {
        addBounces()

        val found: Preferences = await(individualRepo.findBy(entityId)).get

        found.email.get.bounceCount mustBe i
      }
    }

    "Reset the bounce counter when a use verifies their email again" in new Setup {
      private val email = "foo@foo.com"

      givenOptedInAndVerifiedPreference(entityId, email)

      individualRepo
        .setUnverifiedEmailAddress(
          entityId,
          PendingEmailAddress(email, verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow()))),
          event = Seq.empty
        )
        .futureValue

      for (i <- 1 to 3) {
        addBounces()

        val found: Preferences = await(individualRepo.findBy(entityId)).get

        found.email.get.bounceCount mustBe i
      }

      markEmailVerified(entityId)

      private val found = await(individualRepo.findBy(entityId)).get
      found.email.get.bounceCount mustBe 0
    }

    "Detect if the bounce counter is set" in new Setup {
      private val email = "foo@foo.com"
      private val initialPreferences = givenOptedInAndVerifiedPreference(entityId, email)

      individualRepo
        .setUnverifiedEmailAddress(
          entityId,
          PendingEmailAddress(email, verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow()))),
          event = Seq.empty
        )
        .futureValue

      unsetBounceCount(initialPreferences._id)
      await(individualRepo.hasBounceCount(initialPreferences._id)) mustBe false

      addBounces()
      await(individualRepo.hasBounceCount(initialPreferences._id)) mustBe true

      private val found = await(individualRepo.findBy(entityId)).get
      found.email.get.bounceCount mustBe 1
    }

    "Change the updateAt time and leave the rest of the document as-is if neither supplied" in new Setup {
      private val initialPreferences = givenOptedInUnverifiedPreference(
        entityId,
        "foo@foo.com",
        EmailVerificationLink(linkSentTime = Dc.instantNow())
      )
      markEmailVerified(entityId)

      individualRepo
        .setUnverifiedEmailAddress(entityId, PendingEmailAddress("foo@foo.com"), event = Seq.empty)
        .futureValue

      private val updatedPreferences = individualRepo.findBy(entityId).futureValue.get

      await(
        individualRepoSometimeLater.addBouncesAndClearVerificationLink(
          initialPreferences,
          emailBounce = None,
          pendingEmailBounce = None,
          shouldIncBounce = false
        )
      )

      individualRepo.findBy(entityId).futureValue must contain(updatedPreferences.copy(updatedAt = later))
    }

    "Increment/set the bounce counter only if the main email is set in preferences" in new Setup {

      private val email = "foo@foo.com"
      private val initialPreferences = givenOptedInAndVerifiedPreference(entityId, email)

      await(
        individualRepoSometimeLater.addBouncesAndClearVerificationLink(
          initialPreferences,
          emailBounce = Some(EmailBounce(Some(1234), now)),
          pendingEmailBounce = None,
          shouldIncBounce = true
        )
      )

      private val found = await(individualRepo.findBy(entityId)).get
      found.email.get must be(Symbol("bounced"))
      found.email.get.bounceCount mustBe 1
    }

    "not increment/set the bounce counter if the main email is not set in preferences" in new Setup {

      private val email = "foo@foo.com"
      private val initialPreferences = givenOptedInAndVerifiedPreference(entityId, email)

      await(
        individualRepoSometimeLater.addBouncesAndClearVerificationLink(
          initialPreferences,
          emailBounce = None,
          pendingEmailBounce = None,
          shouldIncBounce = true
        )
      )

      private val found = await(individualRepo.findBy(entityId)).get
      found.email.get.bounceCount must not be 1
    }
  }

  "verification reminders" should {

    "be in a todo state once a user has opted in" in new Setup {
      private val email = uniqueEmail
      private val preferences = givenOptedInUnverifiedPreference(
        entityId,
        email,
        EmailVerificationLink(linkSentTime = Dc.instantNow().minusDays(15))
      )
      preferences.pendingEmail.get.reminder.get.status must be(ToDo)
    }

    "pull a todo reminder that is older than the verification reminder time limit" in new Setup {
      // Given
      private val verificationLinkDetails = verificationLinkDetailsMinusDays(15)
      private val verificationLinkDetailsForEntityId = verificationLinkDetailsMinusDays(16)

      private val id = givenOptedInUnverifiedPreference(entityId, emailAddress, verificationLinkDetails)._id
      individualRepo
        .markEmailVerified(
          id,
          PendingEmailAddress(validEmail.email, reminder = Some(Reminder(ToDo, now))),
          Some(Welsh),
          event = None
        )

      private val emailForEntity = GenerateRandom.email()
      private val idWithEntity = givenOptedInUnverifiedPreference(
        GenerateRandom.entityId(),
        emailForEntity,
        verificationLinkDetailsForEntityId
      )._id
      individualRepo.markEmailVerified(
        idWithEntity,
        PendingEmailAddress(validEmail.email, reminder = Some(Reminder(ToDo, now))),
        Some(Welsh),
        event = None
      )

      // When
      private val pulledReminderItem1: Option[ReminderWorkItem] = individualRepo
        .pullReminder(unverifiedEmailsBefore = now.minusDays(14), retryIncompleteBefore = now.minusSeconds(60))
        .futureValue

      // When
      private val pulledReminderItem2: Option[ReminderWorkItem] = individualRepo
        .pullReminder(unverifiedEmailsBefore = now.minusDays(14), retryIncompleteBefore = now.minusSeconds(60))
        .futureValue

      // Then
      private val reminderWorkItems =
        Seq(pulledReminderItem1, pulledReminderItem2).flatten map (x => (x.email, x.verificationLink))
      reminderWorkItems must contain((emailForEntity, verificationLinkDetailsForEntityId))
      reminderWorkItems must contain((emailAddress, verificationLinkDetails))
    }

    "not pull a todo reminder when the verification link is not yet due for reminder email, regardless of the reminder age" in new Setup {
      private val id =
        givenOptedInUnverifiedPreference(entityId, emailAddress, verificationLinkDetailsMinusDays(13))._id
      individualRepo.markEmailVerified(
        id,
        PendingEmailAddress(validEmail.email, reminder = Some(Reminder(ToDo, now.minusSeconds(120)))),
        Some(Welsh),
        event = None
      )

      private val pulledReminderItem: Future[Option[ReminderWorkItem]] = individualRepo
        .pullReminder(unverifiedEmailsBefore = now.minusDays(14), retryIncompleteBefore = now.minusSeconds(60))

      val x = pulledReminderItem.futureValue
      x must be(empty)
    }

    "be pulled as reminder only once within the incomplete retry timeout" in new Setup {
      private val id =
        givenOptedInUnverifiedPreference(entityId, emailAddress, verificationLinkDetailsMinusDays(15))._id
      individualRepo
        .markEmailVerified(
          id,
          PendingEmailAddress(validEmail.email, reminder = Some(Reminder(ToDo, now))),
          Some(Welsh),
          event = None
        )

      private val fourteenDaysAgo = Dc.instantNow().minusDays(14)
      await(
        individualRepo.pullReminder(
          unverifiedEmailsBefore = fourteenDaysAgo,
          retryIncompleteBefore = Dc.instantNow().minusSeconds(60)
        )
      ) must not be empty

      private val pulledReminderItem = individualRepo.pullReminder(
        unverifiedEmailsBefore = fourteenDaysAgo,
        retryIncompleteBefore = Dc.instantNow().minusSeconds(60)
      )

      pulledReminderItem.futureValue must be(empty)
    }

    "be pulled as reminder after the incomplete retry timeout" in new Setup {
      private val verificationLinkDetails = verificationLinkDetailsMinusDays(15)
      private val since = Dc.instantNow().minusSeconds(65)
      private val id = givenOptedInUnverifiedPreference(entityId, emailAddress, verificationLinkDetails)._id
      individualRepo
        .markEmailVerified(id, inProgress(PendingEmailAddress(validEmail.email), since), Some(Welsh), event = None)

      private val fourteenDaysAgo = Dc.instantNow().minusDays(14)
      private val pulledReminderItem = individualRepo.pullReminder(
        unverifiedEmailsBefore = fourteenDaysAgo,
        retryIncompleteBefore = Dc.instantNow().minusSeconds(60)
      )

      pulledReminderItem.futureValue.get must have(
        Symbol("email")(emailAddress),
        Symbol("verificationLink")(verificationLinkDetails)
      )
    }

    "be marked as reminder sent and not be pulled again" in new Setup {
      private val id =
        givenOptedInUnverifiedPreference(entityId, emailAddress, verificationLinkDetailsMinusDays(15))._id
      individualRepo
        .markEmailVerified(id, inProgress(PendingEmailAddress(validEmail.email), now), Some(Welsh), event = None)

      private val pulledReminderItem = individualRepo.pullReminder(
        unverifiedEmailsBefore = Dc.instantNow().minusDays(14),
        retryIncompleteBefore = Dc.instantNow().minusSeconds(60)
      )

      individualRepo.setReminderSucceeded(pulledReminderItem.futureValue.get)

      individualRepo
        .pullReminder(
          unverifiedEmailsBefore = Dc.instantNow().minusDays(14),
          retryIncompleteBefore = Dc.instantNow().minusSeconds(60)
        )
        .futureValue must be(empty)
    }

    "be marked as reminder failed and not be pulled again" in new Setup {
      private val id =
        givenOptedInUnverifiedPreference(entityId, emailAddress, verificationLinkDetailsMinusDays(15))._id
      individualRepo
        .markEmailVerified(id, inProgress(PendingEmailAddress(validEmail.email), now), Some(Welsh), event = None)

      private val pulledReminderItem = individualRepo.pullReminder(
        unverifiedEmailsBefore = Dc.instantNow().minusDays(14),
        retryIncompleteBefore = Dc.instantNow().minusSeconds(60)
      )

      individualRepo.setReminderFailed(pulledReminderItem.futureValue.get)

      individualRepo
        .pullReminder(
          unverifiedEmailsBefore = Dc.instantNow().minusDays(14),
          retryIncompleteBefore = Dc.instantNow().minusSeconds(60)
        )
        .futureValue must be(empty)
    }

    "pull a failed reminder after the incomplete retry timeout" in new Setup {
      private val verificationLinkDetails = verificationLinkDetailsMinusDays(15)
      private val since = Dc.instantNow().minusSeconds(65)

      private val id = givenOptedInUnverifiedPreference(entityId, emailAddress, verificationLinkDetails)._id
      individualRepo
        .markEmailVerified(id, failed(PendingEmailAddress(validEmail.email), since), Some(Welsh), event = None)

      private val fourteenDaysAgo = Dc.instantNow().minusDays(14)
      private val pulledReminderItem = individualRepo.pullReminder(
        unverifiedEmailsBefore = fourteenDaysAgo,
        retryIncompleteBefore = Dc.instantNow().minusSeconds(60)
      )

      pulledReminderItem.futureValue.get must have(
        Symbol("email")(emailAddress),
        Symbol("verificationLink")(verificationLinkDetails)
      )
    }

    "not be pulled to send a reminder if it is not due" in new Setup {
      private val id =
        givenOptedInUnverifiedPreference(entityId, emailAddress, verificationLinkDetailsMinusDays(13))._id
      individualRepo
        .markEmailVerified(
          id,
          PendingEmailAddress(validEmail.email, reminder = Some(Reminder(ToDo, now))),
          Some(Welsh),
          event = None
        )

      private val fourteenDaysAgo = Dc.instantNow().minusDays(14)
      private val pulledReminderItem = individualRepo.pullReminder(
        unverifiedEmailsBefore = fourteenDaysAgo,
        retryIncompleteBefore = Dc.instantNow().minusSeconds(60)
      )

      pulledReminderItem.futureValue must be(empty)
    }

    "not be pulled for bounces" in new Setup {
      private val preferences =
        givenOptedInUnverifiedPreference(entityId, emailAddress, verificationLinkDetailsMinusDays(15))

      await(
        individualRepo.addBouncesAndClearVerificationLink(
          preferences,
          emailBounce = None,
          pendingEmailBounce = Some(EmailBounce(errorCode = Some(500), timestamp = Dc.instantNow())),
          shouldIncBounce = true
        )
      )

      private val pulledReminderItem: Future[Option[ReminderWorkItem]] = individualRepo.pullReminder(
        unverifiedEmailsBefore = Dc.instantNow().minusDays(14),
        retryIncompleteBefore = Dc.instantNow().minusSeconds(60)
      )

      pulledReminderItem.futureValue must be(empty)
    }

    "reset the reminder to todo when a new opt-in is for a different pending email address" in new Setup {
      private val id =
        givenOptedInUnverifiedPreference(entityId, emailAddress, verificationLinkDetailsMinusDays(16))._id
      individualRepo
        .markEmailVerified(
          id,
          PendingEmailAddress(validEmail.email, reminder = Some(reminder)),
          Some(Welsh),
          event = None
        )

      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(
            entityId,
            acceptGenericTermsAndConditions(),
            pendingEmail = Some(
              PendingEmailAddress(
                uniqueEmail,
                verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow())),
                reminder = Some(Reminder(ToDo, now))
              )
            )
          ),
          credentials = Some(credentials)
        )
        .futureValue

      private val optIn = individualRepo.findBy(entityId).futureValue
      optIn.get.pendingEmail.get.reminder must be(Some(Reminder(ToDo, now)))
    }

    "reset the reminder when a new opt-in is for the same pending email address (verification link resend requested) if a reminder has been sent" in new Setup {
      // Given
      private val id =
        givenOptedInUnverifiedPreference(entityId, emailAddress, verificationLinkDetailsMinusDays(16))._id
      individualRepo
        .markEmailVerified(
          id,
          PendingEmailAddress(validEmail.email, reminder = Some(reminder)),
          Some(Welsh),
          event = None
        )

      // When
      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(
            entityId,
            acceptGenericTermsAndConditions(),
            pendingEmail = Some(
              PendingEmailAddress(
                emailAddress,
                verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow())),
                reminder = Some(Reminder(ToDo, now))
              )
            )
          ),
          credentials = Some(credentials)
        )
        .futureValue

      // Then
      private val optin = individualRepo.findBy(entityId).futureValue
      optin.get.pendingEmail.get.reminder must contain(Reminder(ToDo, now))
    }

    "reset the reminder when a new opt-in is for the same pending email address (verification link resend requested) if no reminder has been sent" in new Setup {
      // Given
      private val id =
        givenOptedInUnverifiedPreference(entityId, emailAddress, verificationLinkDetailsMinusDays(16))._id
      individualRepo
        .markEmailVerified(
          id,
          PendingEmailAddress(validEmail.email, reminder = Some(reminder)),
          Some(Welsh),
          event = None
        )

      // When
      individualRepo
        .createOrUpdateTermsAndConditions(
          Preferences(
            entityId,
            acceptGenericTermsAndConditions(),
            pendingEmail = Some(
              PendingEmailAddress(
                emailAddress,
                verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow())),
                reminder = Some(Reminder(ToDo, now))
              )
            )
          ),
          credentials = Some(credentials)
        )
        .futureValue

      // Then
      private val optin = individualRepo.findBy(entityId).futureValue
      optin.get.pendingEmail.get.reminder must contain(Reminder(ToDo, now))
    }
  }

  "updated" should {
    "update the suppression status if the entity id exists" in new Setup {
      private val initialPreferences = givenOptedInAndVerifiedPreference(entityId, "test1@test.com")

      individualRepo.updated(entityId).futureValue mustBe Some(initialPreferences)

      private val found = await(individualRepo.findBy(entityId))
      private val expected = initialPreferences.copy(
        updatedAt = now
      )
      found mustBe Some(expected)
    }

    "return false if no entity id found" in new Setup {
      individualRepo.updated(entityId).futureValue mustBe None
    }
  }

  "markForDeEnrolment/unsetDeEnrolment" should {
    "update the preferences with the flag 'markForDeEnrolment'" in new Setup {
      private val initialPreferences = givenOptedInAndVerifiedPreference(entityId, "test1@test.com")

      individualRepo.markForDeEnrolment(entityId, "sa").futureValue mustBe true

      val found = await(individualRepo.findBy(entityId))
      val expected = initialPreferences.copy(
        markForDeEnrolment = Some(MarkForDeEnrolment(now, "sa"))
      )
      found mustBe Some(expected)
    }
    "remove the flag 'markForDeEnrolment'" in new Setup {
      private val initialPreferences = givenOptedInAndVerifiedPreference(entityId, "test1@test.com")

      individualRepo.markForDeEnrolment(entityId, "sa").futureValue mustBe true
      individualRepo.unsetDeEnrolment(entityId).futureValue mustBe true

      val found = await(individualRepo.findBy(entityId))
      val expected = initialPreferences
      found mustBe Some(expected)
    }
  }

  "setUnverifiedEmailAddress" should {
    "overwrite existing pending email" in new Setup {
      private val current = emailAddress
      private val initial = givenOptedInUnverifiedPreference(entityId, current, EmailVerificationLink("1", now))
      private val updated = GenerateRandom.email()

      await(
        individualRepo.setUnverifiedEmailAddress(entityId, initial.resetPending(updated, () => now), event = Seq.empty)
      )

      individualRepo.findBy(entityId).futureValue match {
        case Some(
              Preferences(_, _, _, _, Some(PendingEmailAddress(email, None, Some(link), _, _, _)), _, _, _, _, _, _)
            ) =>
          email mustBe updated
          link.linkSentTime mustBe now
        case Some(pref) =>
          fail(s"no pending email address was set $pref")
        case _ => fail("no preference exists for the given entity id. should never have happened")
      }
    }

    "set the pending email address for an existing preference with no existing pending email" in new Setup {
      private val current = emailAddress
      private val initial = givenOptedInAndVerifiedPreference(entityId, current)
      private val updated = GenerateRandom.email()

      initial.pendingEmail mustBe None

      await(
        individualRepo.setUnverifiedEmailAddress(entityId, initial.resetPending(updated, () => now), event = Seq.empty)
      )

      individualRepo.findBy(entityId).futureValue match {
        case Some(
              Preferences(_, _, _, _, Some(PendingEmailAddress(email, _, Some(link), _, _, _)), _, _, _, _, _, _)
            ) =>
          email mustBe updated
          link.linkSentTime mustBe now
        case Some(pref) =>
          fail(s"no pending email address was set $pref")
        case _ => fail("no preference exists for the given entity id. should never have happened")
      }
    }

    "fail when attempting to insert a pending email address for a non-existent preference" in new Setup {
      individualRepo
        .setUnverifiedEmailAddress(
          entityId.copy(value = value.toString() + " hello"),
          PendingEmailAddress(emailAddress, verificationLink = Some(EmailVerificationLink(linkSentTime = now))),
          Seq.empty
        )
        .failed
        .futureValue
        .getMessage must include(s"could not set email [$emailAddress]")
    }
  }

  "setUnverifiedEmailLanguage" should {
    "set pendingEmail language, respecting non-existent language" in new Setup {
      private val testEntityId = GenerateRandom.entityId()
      private val current = emailAddress
      private val initialEnglishPref =
        givenOptedInUnverifiedPreference(testEntityId, current, EmailVerificationLink("1", now))
      private val expectedWelshPref = initialEnglishPref.copy(
        pendingEmail = Some(initialEnglishPref.pendingEmail.get.copy(language = Some(Language.Welsh)))
      )
      individualRepo.setUnverifiedEmailLanguage(testEntityId, Some(Language.Welsh)).futureValue must be(
        PreferenceUpdated
      )
      private val pref = individualRepo.findBy(testEntityId).futureValue
      pref.get must be(expectedWelshPref)
      unsetPendingEmailLanguage(testEntityId)

      private val pref1 = individualRepo.findBy(testEntityId).futureValue
      pref1.get must be(initialEnglishPref)
      individualRepo.setUnverifiedEmailLanguage(testEntityId, Some(Language.Welsh)).futureValue must be(
        PreferenceUpdated
      )

      private val pref2 = individualRepo.findBy(testEntityId).futureValue
      pref2.get must be(expectedWelshPref)
    }

    "not update preference if there is no pendingEmailAddress" in new Setup {
      private val testEntityId = GenerateRandom.entityId()
      private val current = emailAddress
      private val initialPref = givenOptedInAndVerifiedPreference(testEntityId, current)

      initialPref.pendingEmail mustBe None

      individualRepo.setUnverifiedEmailLanguage(testEntityId, Some(Language.Welsh)).futureValue must be(
        LanguageNotUpdated
      )

      private val pref = individualRepo.findBy(testEntityId).futureValue
      pref.get must be(initialPref)
    }

  }
  "setVerifiedEmailLanguage" should {
    "set verified email language, respecting non-existent language" in new Setup {
      private val testEntityId = GenerateRandom.entityId()
      private val current = emailAddress
      private val initialEnglishPref = givenOptedInAndVerifiedPreference(testEntityId, current)
      private val expectedWelshPref =
        initialEnglishPref.copy(email = Some(initialEnglishPref.email.get.copy(language = Some(Language.Welsh))))

      individualRepo.setVerifiedEmailLanguage(testEntityId, Some(Language.Welsh)).futureValue must be(PreferenceUpdated)
      private val pref = individualRepo.findBy(testEntityId).futureValue
      pref.get must be(expectedWelshPref)

      unsetEmailLanguage(testEntityId)

      private val pref1 = individualRepo.findBy(testEntityId).futureValue

      pref1.get must be(initialEnglishPref)
      individualRepo.setVerifiedEmailLanguage(testEntityId, Some(Language.Welsh)).futureValue must be(PreferenceUpdated)

      private val pref2 = individualRepo.findBy(testEntityId).futureValue
      pref2.get must be(expectedWelshPref)
    }

    "not update preference if there is no verified emailAddress" in new Setup {
      private val testEntityId = GenerateRandom.entityId()
      private val current = emailAddress
      private val initialPref = givenOptedInUnverifiedPreference(testEntityId, current, EmailVerificationLink("1", now))

      initialPref.email mustBe None

      individualRepo.setVerifiedEmailLanguage(testEntityId, Some(Language.Welsh)).futureValue must be(
        LanguageNotUpdated
      )

      private val pref = individualRepo.findBy(testEntityId).futureValue
      pref.get must be(initialPref)
    }

  }

  "noc and ups remover support" should {
    implicit val as = ActorSystem.create()

    "count noc blocks" in new Setup {
      withPreference()
      withPreference(addNocBlock = false)
      val numberWithNocBlocks = individualRepo.countNocBlocks().futureValue
      numberWithNocBlocks mustBe 1
    }

    "count ups blocks" in new Setup {
      withPreference()
      withPreference(addUpsBlock = false)
      val numberWithUpsBlocks = individualRepo.countUpsBlocks().futureValue
      numberWithUpsBlocks mustBe 1
    }

    "stream noc block documents" in new Setup {
      withPreference()
      withPreference(addNocBlock = false)
      val results = ListBuffer[BsonDocument]()
      val source = individualRepo.streamNoc()
      source.runForeach(s => results.append(s)).futureValue
      results.size mustBe 1
    }

    "stream ups block documents" in new Setup {
      withPreference()
      withPreference(addUpsBlock = false)
      val results = ListBuffer[BsonDocument]()
      val source = individualRepo.streamUps()
      source.runForeach(s => results.append(s)).futureValue
      results.size mustBe 1
    }

    "remove single noc block document" in new Setup {
      withPreference()
      val items = individualRepo.repo.collection.find().toFuture().futureValue
      val docs = items.map(p => p.toBson.asDocument())
      val result = individualRepo.removeNOCBlock(docs).futureValue
      result.docsProcessed mustBe 1

      val doc =
        mongoDatabase.getCollection("saIndividualPreferences").find[BsonDocument]().first().toFuture().futureValue
      doc.containsKey("noc") mustBe false
      doc.containsKey("ups") mustBe true
    }

    "remove single ups block document" in new Setup {
      withPreference()
      val items = individualRepo.repo.collection.find().toFuture().futureValue
      val docs = items.map(p => p.toBson.asDocument())
      val result = individualRepo.removeUPSBlock(docs).futureValue
      result.docsProcessed mustBe 1
      val doc =
        mongoDatabase.getCollection("saIndividualPreferences").find[BsonDocument]().first().toFuture().futureValue
      doc.containsKey("noc") mustBe true
      doc.containsKey("ups") mustBe false
    }
  }

  case class TestRepo(currentTime: Instant) extends PreferencesMongoRepository(mongoComponent) {
    override def withCurrentTime[A](f: Instant => A): A = f(currentTime)
  }

}
