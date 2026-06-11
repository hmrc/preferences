/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.repository

import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.result.InsertOneResult
import org.mongodb.scala.SingleObservableFuture
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.auth.core.{ AffinityGroup, ConfidenceLevel }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.model.OptEventType.CustomerReOptOut
import uk.gov.hmrc.preferences.model.PageType.{ IPage, ReOptInPage }
import uk.gov.hmrc.preferences.model.TermsAndConditions._
import uk.gov.hmrc.preferences.model._
import utils.GenerateRandom
import play.api.test.Helpers._
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.preferences.util.Dc
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions
import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class StatsRepositorySpec
    extends PlaySpec with MongoSupport with ScalaFutures with IntegrationPatience with BeforeAndAfterAll
    with MockitoSugar {

  val lockRepository: MongoLockRepository = mock[MongoLockRepository]
  override def afterAll(): Unit = prepareDatabase()

  val random = new Random()
  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup {

    implicit val now: Instant = Dc.instantNow()

    val termsAndConditionsRefusedForGenericOnly: TermsAndConditions = TermsAndConditions(Refused(Dc.instantNow()))
    val termsAndConditionsAcceptedForGenericOnly: TermsAndConditions = TermsAndConditions(Accepted(Dc.instantNow()))

    val termsAndConditionsGenericReOptedIn: TermsAndConditions =
      TermsAndConditions(
        Accepted(Dc.instantNow(), Some(OptEventType.ReOptIn), Some(OptInPage(Version(1, 0), 10, ReOptInPage)))
      )

    val termsAndConditionsGenericRefusedForCustomerReOptedOut =
      TermsAndConditions(
        Refused(Dc.instantNow(), Some(CustomerReOptOut), Some(OptInPage(Version(1, 0), 10, ReOptInPage)))
      )

    implicit lazy val preferencesRepo: PreferencesMongoRepository =
      new PreferencesMongoRepository(mongoComponent = mongoComponent) {
        override def withCurrentTime[A](f: Instant => A): A = f(now)
      }
    await(preferencesRepo.repo.collection.deleteMany(Document()).toFuture())

    lazy val statsCounter = new StatsCounter(mongoComponent)

    lazy val statsRepo: StatsRepository = new StatsRepository(mongoComponent) {
      override def withCurrentTime[A](f: Instant => A): A = f(now)
    }

    def insert(preferences: Preferences*): Seq[InsertOneResult] =
      await {
        Future.sequence(
          preferences.map(p => preferencesRepo.repo.collection.insertOne(p).toFuture())
        )
      }
  }

  "computeStatistics" should {
    "count number of opted-out users" in new Setup {
      // not opted-in or opted-out cases
      insert(
        Preferences(
          entityId = GenerateRandom.entityId(),
          termsAndConditions = termsAndConditionsRefusedForGenericOnly,
          email = None,
          pendingEmail = None
        ),
        Preferences(
          entityId = GenerateRandom.entityId(),
          termsAndConditions = termsAndConditionsRefusedForGenericOnly,
          email = None,
          pendingEmail = None
        ),
        Preferences(
          entityId = GenerateRandom.entityId(),
          termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
          email = None,
          pendingEmail = None
        )
      )

      statsCounter.runStatsQuery().futureValue

      statsRepo.findAllWithDefaults().futureValue.apply("generic.optedOut") must be(DatedCount(2))

      // not opted-in or opted-out cases
      insert(
        Preferences(entityId = GenerateRandom.entityId(), termsAndConditions = termsAndConditionsRefusedForGenericOnly)
      )

      statsCounter.runStatsQuery().futureValue
      statsRepo.findAllWithDefaults().futureValue.apply("generic.optedOut") must be(DatedCount(3))
    }
  }

  "count unique number of verified users" in new Setup {
    insert(
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress(email = "test1@test.com", verifiedOn = Some(Dc.instantNow())))
      ),
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress(email = "test1@test.com", verifiedOn = Some(Dc.instantNow())))
      ),
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress(email = "test3@test.com", verifiedOn = None))
      )
    )

    statsCounter.runStatsQuery().futureValue
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedInAndVerified") must be(DatedCount(2))

    insert(
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("test4@test.com"))
      )
    )

    statsCounter.runStatsQuery().futureValue
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedInAndVerified") must be(DatedCount(3))
  }

  "count number of users that selected only welsh" in new Setup {
    insert(
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("testwelsh@test.com", language = Some(Language.Welsh)))
      ),
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("testwelsh2@test.com", language = Some(Language.Welsh)))
      ),
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("testenglish@test.com", language = Some(Language.English)))
      )
    )

    statsCounter.runStatsQuery().futureValue
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedInAndVerifiedAndWelsh") must be(DatedCount(2))
  }

  "return count 0 if welsh is not selected as language preference" in new Setup {
    Preferences(
      entityId = GenerateRandom.entityId(),
      termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
      email = Some(EmailAddress("testenglish@test.com", language = Some(Language.English)))
    )
    statsCounter.runStatsQuery().futureValue
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedInAndVerifiedAndWelsh") must be(DatedCount(0))
  }

  "count number of opted-out users for generic with verified or pending email - investigate" in new Setup {
    insert(
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsRefusedForGenericOnly,
        email = None,
        pendingEmail = Some(PendingEmailAddress("myemail@test.com", None))
      ),
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsRefusedForGenericOnly,
        email = Some(EmailAddress("myemail1@test.com", Some(now))),
        pendingEmail = None
      ),
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsRefusedForGenericOnly,
        email = None,
        pendingEmail = None
      ),
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsRefusedForGenericOnly,
        email = Some(EmailAddress("myemail2@test.com", Some(now))),
        pendingEmail = None
      ),
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("myemail3@test.com", Some(now))),
        pendingEmail = None
      )
    )

    statsCounter.runStatsQuery().futureValue
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedOutWithVerifiedEmail") must be(DatedCount(2))
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedOutWithPendingEmail") must be(DatedCount(1))
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedIn") must be(DatedCount(1))
  }
  "count number of opted-out users for generic" in new Setup {
    // not opted-in or opted-out cases
    insert(
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsRefusedForGenericOnly,
        email = None,
        pendingEmail = Some(PendingEmailAddress("myemail@test.com", None))
      ),
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsRefusedForGenericOnly,
        email = Some(EmailAddress("myemail@test.com", Some(now))),
        pendingEmail = None
      )
    )

    statsCounter.runStatsQuery().futureValue
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedOut") must be(DatedCount(2))
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedIn") must be(DatedCount(0))
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedInAndVerified") must be(DatedCount(0))

    insert(
      Preferences(entityId = GenerateRandom.entityId(), termsAndConditions = termsAndConditionsRefusedForGenericOnly)
    )

    statsCounter.runStatsQuery().futureValue
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedOut") must be(DatedCount(3))
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedIn") must be(DatedCount(0))
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedInAndVerified") must be(DatedCount(0))

    statsCounter.runStatsQuery().futureValue
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedOut") must be(DatedCount(3))
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedIn") must be(DatedCount(0))
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedInAndVerified") must be(DatedCount(0))
  }

  "count number of opted-in users for generic" in new Setup {
    // not opted-in or opted-out cases
    insert(
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsRefusedForGenericOnly,
        email = None,
        pendingEmail = Some(PendingEmailAddress("myemail@test.com", None))
      ),
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = None,
        pendingEmail = Some(PendingEmailAddress("myemail@test.com", None))
      ),
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("myemail@test.com", Some(now))),
        pendingEmail = None
      )
    )

    statsCounter.runStatsQuery().futureValue
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedIn") must be(DatedCount(2))
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedInAndVerified") must be(DatedCount(1))

    insert(
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly
      )
    )

    statsCounter.runStatsQuery().futureValue
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedIn") must be(DatedCount(3))
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedInAndVerified") must be(DatedCount(1))

    statsCounter.runStatsQuery().futureValue
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedIn") must be(DatedCount(3))
    statsRepo.findAllWithDefaults().futureValue.apply("generic.optedInAndVerified") must be(DatedCount(1))
  }

  "count number of users verified and in a bounced state" in new Setup {
    insert(
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("test@test.com", lastBounce = Some(EmailBounce(Some(550), Dc.instantNow()))))
      ),
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("test@test.com"))
      )
    )

    statsCounter.runStatsQuery().futureValue
    statsRepo.findAllWithDefaults().futureValue.apply("verifiedButBounced") must be(DatedCount(1))

    insert(
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("test@test.com", lastBounce = Some(EmailBounce(Some(550), Dc.instantNow()))))
      )
    )

    statsCounter.runStatsQuery().futureValue
    statsRepo.findAllWithDefaults().futureValue.apply("verifiedButBounced") must be(DatedCount(2))
  }

  "update metrics" in new Setup {

    insert(
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("test@test.com"))
      )
    )
    statsCounter.runStatsQuery().futureValue

    private val metrics: Map[String, Int] = statsRepo.metrics.futureValue
    metrics("statistics.generic.optedOut") must be(0)

    insert(
      Preferences(entityId = GenerateRandom.entityId(), termsAndConditions = termsAndConditionsRefusedForGenericOnly)
    )

    statsCounter.runStatsQuery().futureValue

    private val updatedMetrics: Map[String, Int] = statsRepo.metrics.futureValue
    updatedMetrics("statistics.generic.optedOut") must be(1)
  }
}
