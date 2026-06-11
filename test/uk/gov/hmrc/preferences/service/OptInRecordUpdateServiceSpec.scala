/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import com.codahale.metrics.SharedMetricRegistries
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.mongo.lock.{ Lock, MongoLockRepository }
import uk.gov.hmrc.preferences.jobs.RunModeBridge
import uk.gov.hmrc.preferences.model.EmailEventType.{ EmailBounced, EmailChanged, EmailVerified }
import uk.gov.hmrc.preferences.model.OptEventType.{ CustomerOptOut, OptIn }
import uk.gov.hmrc.preferences.model.Language.English
import uk.gov.hmrc.preferences.model.PageType.IPage
import uk.gov.hmrc.preferences.model.TermsAndConditions.{ Accepted, Refused }
import uk.gov.hmrc.preferences.model._
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions
import uk.gov.hmrc.preferences.util.Dc
import utils.GenerateRandom

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{ Duration, MINUTES }

class OptInRecordUpdateServiceSpec
    extends PlaySpec with MockitoSugar with ScalaFutures with IntegrationPatience with BeforeAndAfterEach {

  private val mockLockRespository = mock[MongoLockRepository]
  private val mockPreferencesRepository = mock[PreferencesRepository]
  private val mockRunModeBridge = mock[RunModeBridge]

  when(mockRunModeBridge.getOptionalMillisForScheduling(any[String], any[String]))
    .thenReturn(Some(Duration(1, MINUTES)))
//  lazy val batchSize: Int = runModeBridge.getBatchSize(name, "batchSize")

  private val optInRecordUpdateService =
    new OptInRecordUpdateService(mockLockRespository, mockPreferencesRepository, mockRunModeBridge)

  override def beforeEach(): Unit = {
    super.beforeEach()
    SharedMetricRegistries.clear()
  }

  "OptInRecordUpdateJob" should {
    "process preference with opt-out" in {
      val actionTimeStamp = Dc.instantNow()
      val preference = new Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = TermsAndConditions(Refused(actionTimeStamp))
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)

      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        CustomerOptOutEvent(
          CustomerOptOut,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          actionTimeStamp,
          English,
          Some(false)
        ),
        List(
          CustomerOptOutEvent(
            CustomerOptOut,
            OptInPage(Version(1, 0), 8, IPage),
            preference.entityId,
            actionTimeStamp,
            English,
            Some(false)
          )
        )
      )
    }

    "do not process preference with opt-out with same time stamp" in {
      val actionTimeStamp = Dc.instantNow()
      val entityId = GenerateRandom.entityId()

      val preference = new Preferences(
        entityId,
        termsAndConditions = TermsAndConditions(Refused(actionTimeStamp)),
        events = Some(
          List(
            CustomerOptOutEvent(
              CustomerOptOut,
              OptInPage(Version(1, 0), 8, IPage),
              entityId,
              actionTimeStamp,
              English,
              Some(false)
            )
          )
        )
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)

      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        CustomerOptOutEvent(
          CustomerOptOut,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          actionTimeStamp,
          English,
          Some(false)
        ),
        List.empty
      )
    }

    "process opt-out of opt-in is recorded" in {}

    "process preference with opt-in" in {
      val actionTimeStamp = Dc.instantNow()
      val preference = new Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = TermsAndConditions(Accepted(actionTimeStamp))
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(
          OptIn,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          actionTimeStamp,
          English,
          Some(false)
        ),
        List(
          OptInEvent(
            OptIn,
            OptInPage(Version(1, 0), 8, IPage),
            preference.entityId,
            actionTimeStamp,
            English,
            Some(false)
          )
        )
      )
    }

    "do not process preference with opt-in with same timestamp" in {
      val actionTimeStamp = Dc.instantNow()
      val entityId = GenerateRandom.entityId()
      val preference = new Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = TermsAndConditions(Accepted(actionTimeStamp)),
        events = Some(
          List(OptInEvent(OptIn, OptInPage(Version(1, 0), 8, IPage), entityId, actionTimeStamp, English, Some(false)))
        )
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(
          OptIn,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          actionTimeStamp,
          English,
          Some(false)
        ),
        List()
      )
    }

    "process preference with opt-in and verify" in {
      val actionTimeStamp = Dc.instantNow()
      val email = GenerateRandom.email()
      val acceptedTime = actionTimeStamp.minusDays(1)

      val preference = new Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = TermsAndConditions(Accepted(acceptedTime)),
        email = Some(EmailAddress(email = email, verifiedOn = Some(actionTimeStamp), language = Some(English)))
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(OptIn, OptInPage(Version(1, 0), 8, IPage), preference.entityId, acceptedTime, English, None),
        List(
          OptInEvent(OptIn, OptInPage(Version(1, 0), 8, IPage), preference.entityId, acceptedTime, English, None),
          EmailEvent(preference.entityId, EmailVerified, email, Some(true), actionTimeStamp)
        )
      )
    }

    "doesn't process email verified event again when preference has this event with same timestamp" in {
      val actionTimeStamp = Dc.instantNow()
      val email = GenerateRandom.email()
      val entityId = GenerateRandom.entityId()
      val acceptedTime = actionTimeStamp.minusDays(1)

      val preference = new Preferences(
        entityId,
        termsAndConditions = TermsAndConditions(Accepted(acceptedTime)),
        email = Some(EmailAddress(email = email, verifiedOn = Some(actionTimeStamp), language = Some(English))),
        events = Some(List(EmailEvent(entityId, EmailVerified, email, None, actionTimeStamp)))
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(OptIn, OptInPage(Version(1, 0), 8, IPage), preference.entityId, acceptedTime, English, None),
        List(OptInEvent(OptIn, OptInPage(Version(1, 0), 8, IPage), entityId, acceptedTime, English, None, None))
      )
    }

    "process email verified event again when preference has this event with different timestamp" in {
      val actionTimeStamp = Dc.instantNow()
      val email = GenerateRandom.email()
      val entityId = GenerateRandom.entityId()

      val preference = new Preferences(
        entityId,
        termsAndConditions = TermsAndConditions(Accepted(actionTimeStamp)),
        email = Some(EmailAddress(email = email, verifiedOn = Some(actionTimeStamp), language = Some(English))),
        events = Some(List(EmailEvent(entityId, EmailVerified, email, None, actionTimeStamp.minusDays(5))))
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(
          OptIn,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          actionTimeStamp,
          English,
          Some(true)
        ),
        List(
          OptInEvent(
            OptIn,
            OptInPage(Version(1, 0), 8, IPage),
            preference.entityId,
            actionTimeStamp,
            English,
            Some(true)
          ),
          EmailEvent(preference.entityId, EmailVerified, email, Some(true), actionTimeStamp)
        )
      )
    }

    "process preference with opt-in and email bounced" in {
      val actionTimeStamp = Dc.instantNow().minusDays(50)
      val bounceTimestamp = Dc.instantNow().minusDays(100)
      val email = GenerateRandom.email()

      val preference = new Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = TermsAndConditions(Accepted(actionTimeStamp)),
        email = Some(
          EmailAddress(email = email, language = Some(English), lastBounce = Some(EmailBounce(None, bounceTimestamp)))
        )
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(
          OptIn,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          actionTimeStamp,
          English,
          Some(false),
          None
        ),
        List(
          OptInEvent(
            OptIn,
            OptInPage(Version(1, 0), 8, IPage),
            preference.entityId,
            actionTimeStamp,
            English,
            Some(false),
            None
          ),
          EmailEvent(preference.entityId, EmailBounced, email, None, bounceTimestamp)
        )
      )
    }

    "do not process preference if email bounced already with same timestamp" in {
      val actionTimeStamp = Dc.instantNow().minusDays(50)
      val bounceTimestamp = Dc.instantNow().minusDays(100)
      val email = GenerateRandom.email()
      val entityId = GenerateRandom.entityId()

      val preference = new Preferences(
        entityId = entityId,
        termsAndConditions = TermsAndConditions(Accepted(actionTimeStamp)),
        email = Some(
          EmailAddress(email = email, language = Some(English), lastBounce = Some(EmailBounce(None, bounceTimestamp)))
        ),
        events = Some(List(EmailEvent(entityId, EmailBounced, email, None, bounceTimestamp)))
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(
          OptIn,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          actionTimeStamp,
          English,
          Some(false),
          None
        ),
        List(
          OptInEvent(
            OptIn,
            OptInPage(Version(1, 0), 8, IPage),
            preference.entityId,
            actionTimeStamp,
            English,
            Some(false),
            None
          )
        )
      )
    }

    "process preference with email bounced event already and has different timestamp" in {
      val actionTimeStamp = Dc.instantNow().minusDays(50)
      val bounceTimestamp = Dc.instantNow().minusDays(100)
      val email = GenerateRandom.email()
      val entityId = GenerateRandom.entityId()

      val preference = new Preferences(
        entityId = entityId,
        termsAndConditions = TermsAndConditions(Accepted(actionTimeStamp)),
        email = Some(
          EmailAddress(email = email, language = Some(English), lastBounce = Some(EmailBounce(None, bounceTimestamp)))
        ),
        events = Some(List(EmailEvent(entityId, EmailBounced, email, None, bounceTimestamp.minusDays(5))))
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(
          OptIn,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          actionTimeStamp,
          English,
          Some(false),
          None
        ),
        List(
          OptInEvent(
            OptIn,
            OptInPage(Version(1, 0), 8, IPage),
            preference.entityId,
            actionTimeStamp,
            English,
            Some(false),
            None
          ),
          EmailEvent(preference.entityId, EmailBounced, email, None, bounceTimestamp)
        )
      )
    }

    "process preference with opt-in and pending email bounced" in {
      val actionTimeStamp = Dc.instantNow().minusDays(50)
      val bounceTimestamp = Dc.instantNow().minusDays(100)
      val email = GenerateRandom.email()

      val preference = new Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = TermsAndConditions(Accepted(actionTimeStamp)),
        pendingEmail = Some(
          PendingEmailAddress(
            email = email,
            language = Some(English),
            lastBounce = Some(EmailBounce(None, bounceTimestamp))
          )
        )
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(
          OptIn,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          actionTimeStamp,
          English,
          Some(false),
          None
        ),
        List(
          OptInEvent(
            OptIn,
            OptInPage(Version(1, 0), 8, IPage),
            preference.entityId,
            actionTimeStamp,
            English,
            Some(false),
            None
          ),
          EmailEvent(preference.entityId, EmailBounced, email, None, bounceTimestamp)
        )
      )
    }

    "do not process preference with pending email bounced already with same timestamp" in {
      val actionTimeStamp = Dc.instantNow().minusDays(50)
      val bounceTimestamp = Dc.instantNow().minusDays(100)
      val email = GenerateRandom.email()
      val entityId = GenerateRandom.entityId()

      val preference = new Preferences(
        entityId,
        termsAndConditions = TermsAndConditions(Accepted(actionTimeStamp)),
        pendingEmail = Some(
          PendingEmailAddress(
            email = email,
            language = Some(English),
            lastBounce = Some(EmailBounce(None, bounceTimestamp))
          )
        ),
        events = Some(List(EmailEvent(entityId, EmailBounced, email, None, bounceTimestamp)))
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(
          OptIn,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          actionTimeStamp,
          English,
          Some(false),
          None
        ),
        List(
          OptInEvent(
            OptIn,
            OptInPage(Version(1, 0), 8, IPage),
            preference.entityId,
            actionTimeStamp,
            English,
            Some(false),
            None
          )
        )
      )
    }

    "process preference with pending email bounced already and has different timestamp" in {
      val actionTimeStamp = Dc.instantNow().minusDays(50)
      val bounceTimestamp = Dc.instantNow().minusDays(100)
      val email = GenerateRandom.email()
      val entityId = GenerateRandom.entityId()

      val preference = new Preferences(
        entityId,
        termsAndConditions = TermsAndConditions(Accepted(actionTimeStamp)),
        pendingEmail = Some(
          PendingEmailAddress(
            email = email,
            language = Some(English),
            lastBounce = Some(EmailBounce(None, bounceTimestamp))
          )
        ),
        events = Some(List(EmailEvent(entityId, EmailBounced, email, None, actionTimeStamp.minusDays(5))))
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(
          OptIn,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          actionTimeStamp,
          English,
          Some(false),
          None
        ),
        List(
          OptInEvent(
            OptIn,
            OptInPage(Version(1, 0), 8, IPage),
            preference.entityId,
            actionTimeStamp,
            English,
            Some(false),
            None
          ),
          EmailEvent(preference.entityId, EmailBounced, email, None, bounceTimestamp)
        )
      )
    }

    "process preference with email changed" in {
      val actionTimeStamp = Dc.instantNow().minusDays(50)
      val email = GenerateRandom.email()
      val entityId = GenerateRandom.entityId()
      val preference = new Preferences(
        entityId = entityId,
        email = Some(EmailAddress(email, verifiedOn = None, None, 0, None, Some(Language.English))),
        pendingEmail = Some(
          PendingEmailAddress(
            "pendingemail@test.com",
            None,
            verificationLink = Some(EmailVerificationLink(entityId.toString, actionTimeStamp)),
            language = Some(Language.English)
          )
        ),
        termsAndConditions =
          TermsAndConditions(Accepted(actionTimeStamp, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(
          OptIn,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          actionTimeStamp,
          English,
          Some(false),
          None
        ),
        List(
          OptInEvent(
            OptIn,
            OptInPage(Version(1, 0), 8, IPage),
            preference.entityId,
            actionTimeStamp,
            English,
            Some(false),
            None
          ),
          EmailEvent(preference.entityId, EmailChanged, "pendingemail@test.com", Some(false), actionTimeStamp)
        )
      )
    }

    "do not process preference with email changed when an event with same time stamp exists" in {
      val actionTimeStamp = Dc.instantNow().minusDays(50)
      val email = GenerateRandom.email()
      val entityId = GenerateRandom.entityId()
      val preference = new Preferences(
        entityId = entityId,
        email = Some(EmailAddress(email, verifiedOn = None, None, 0, None, Some(Language.English))),
        pendingEmail = Some(
          PendingEmailAddress(
            "pendingemail@test.com",
            None,
            verificationLink = Some(EmailVerificationLink(entityId.toString, actionTimeStamp)),
            language = Some(Language.English)
          )
        ),
        termsAndConditions =
          TermsAndConditions(Accepted(actionTimeStamp, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage)))),
        events = Some(List(EmailEvent(entityId, EmailChanged, email, None, actionTimeStamp)))
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(
          OptIn,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          actionTimeStamp,
          English,
          Some(false),
          None
        ),
        List(
          OptInEvent(
            OptIn,
            OptInPage(Version(1, 0), 8, IPage),
            preference.entityId,
            actionTimeStamp,
            English,
            Some(false),
            None
          )
        )
      )
    }

    "process preference with opt-in, verify and also bounce" in {
      val actionTimeStamp = Dc.instantNow().minusDays(50)
      val bounceTimestamp = Dc.instantNow().minusDays(100)
      val email = GenerateRandom.email()

      val preference = new Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = TermsAndConditions(Accepted(actionTimeStamp)),
        email = Some(
          EmailAddress(
            email = email,
            verifiedOn = Some(actionTimeStamp),
            language = Some(English),
            lastBounce = Some(EmailBounce(None, bounceTimestamp))
          )
        )
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(
          OptIn,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          actionTimeStamp,
          English,
          Some(false),
          None
        ),
        List(
          OptInEvent(
            OptIn,
            OptInPage(Version(1, 0), 8, IPage),
            preference.entityId,
            actionTimeStamp,
            English,
            Some(false),
            None
          ),
          EmailEvent(preference.entityId, EmailVerified, email, Some(false), actionTimeStamp),
          EmailEvent(preference.entityId, EmailBounced, email, None, bounceTimestamp)
        )
      )
    }

    "process preference with opt-in, verified, email bounce and pending email bounce records 2 bounces and is paperless only for latest event" in {
      val actionTimeStamp = Dc.instantNow().minusDays(100)
      val bounceTimestamp = Dc.instantNow().minusDays(50)
      val bounceTimestampPending = Dc.instantNow().minusDays(10)
      val email = GenerateRandom.email()

      val preference = new Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = TermsAndConditions(Accepted(actionTimeStamp)),
        email = Some(
          EmailAddress(
            email = email,
            verifiedOn = Some(actionTimeStamp),
            language = Some(English),
            lastBounce = Some(EmailBounce(None, bounceTimestamp))
          )
        ),
        pendingEmail = Some(
          PendingEmailAddress(
            email = email,
            language = Some(English),
            lastBounce = Some(EmailBounce(None, bounceTimestampPending))
          )
        )
      )
      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      when(
        mockPreferencesRepository
          .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
      )
        .thenReturn(Future(true))

      val optInUpdateResult = OptInUpdateResult(1, 0)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference)

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(
          OptIn,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          actionTimeStamp,
          English,
          None,
          None
        ),
        List(
          OptInEvent(
            OptIn,
            OptInPage(Version(1, 0), 8, IPage),
            preference.entityId,
            actionTimeStamp,
            English,
            None,
            None
          ),
          EmailEvent(preference.entityId, EmailVerified, email, None, actionTimeStamp),
          EmailEvent(preference.entityId, EmailBounced, email, None, bounceTimestamp),
          EmailEvent(preference.entityId, EmailBounced, email, Some(false), bounceTimestampPending)
        )
      )
    }
  }

  "isPaperless function only for latest Event" should {
    val entityId = GenerateRandom.entityId()

    "when optIn" in {
      val optInDate = Dc.instantNow().minusMonths(20)
      val preference = new Preferences(
        entityId = entityId,
        pendingEmail = Some(PendingEmailAddress("pendingemail@test.com", None, language = Some(Language.English))),
        termsAndConditions = TermsAndConditions(Accepted(optInDate, Some(OptIn)))
      )
      optInRecordUpdateService.isPaperless(optInDate, preference) must be(Some(false))
    }

    "when OptOut" in {
      val optInDate = Dc.instantNow().minusMonths(20)
      val preference = new Preferences(
        entityId = entityId,
        pendingEmail = Some(PendingEmailAddress("pendingemail@test.com", None, language = Some(Language.English))),
        termsAndConditions = TermsAndConditions(Refused(optInDate, Some(OptIn)))
      )
      optInRecordUpdateService.isPaperless(optInDate, preference) must be(Some(false))
    }

    "when verified date is latest event" in {
      val verifiedDate = Instant.now.minusDays(100)
      val emailChangedDate = Instant.now.minusDays(200)
      val acceptedDate = Instant.now.minusDays(200)

      val preference = new Preferences(
        entityId = entityId,
        email =
          Some(EmailAddress("test@test.com", verifiedOn = Some(verifiedDate), None, 0, None, Some(Language.English))),
        pendingEmail = Some(
          PendingEmailAddress(
            "pendingemail@test.com",
            None,
            verificationLink = Some(EmailVerificationLink(entityId.toString, emailChangedDate)),
            language = Some(Language.English)
          )
        ),
        termsAndConditions =
          TermsAndConditions(Accepted(acceptedDate, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))
      )

      optInRecordUpdateService.isPaperless(verifiedDate, preference) must be(Some(true))
      optInRecordUpdateService.isPaperless(emailChangedDate, preference) must be(None)
      optInRecordUpdateService.isPaperless(acceptedDate, preference) must be(None)
    }

    "when email changed date is latest)" in {
      val verifiedDate = Instant.now.minusDays(200)
      val emailChangedDate = Instant.now.minusDays(100)
      val acceptedDate = Instant.now.minusDays(200)

      val preference = new Preferences(
        entityId = entityId,
        email =
          Some(EmailAddress("test@test.com", verifiedOn = Some(verifiedDate), None, 0, None, Some(Language.English))),
        pendingEmail = Some(
          PendingEmailAddress(
            "pendingemail@test.com",
            None,
            verificationLink = Some(EmailVerificationLink(entityId.toString, emailChangedDate)),
            language = Some(Language.English)
          )
        ),
        termsAndConditions =
          TermsAndConditions(Accepted(acceptedDate, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))
      )

      optInRecordUpdateService.isPaperless(verifiedDate, preference) must be(None)
      optInRecordUpdateService.isPaperless(emailChangedDate, preference) must be(Some(true))
      optInRecordUpdateService.isPaperless(acceptedDate, preference) must be(None)
    }

    "when pendingEmailChangedDate is latest to verifiedDate)" in {
      val verifiedDate = Instant.now.minusDays(200)
      val pendingEmailChangedDate = Instant.now.minusDays(100)
      val acceptedDate = Instant.now.minusDays(200)

      val preference = new Preferences(
        entityId = entityId,
        email = Some(EmailAddress("test@test.com", None, None, 0, None, Some(Language.English))),
        pendingEmail = Some(
          PendingEmailAddress(
            "pendingemail@test.com",
            None,
            verificationLink = Some(EmailVerificationLink(entityId.toString, pendingEmailChangedDate)),
            language = Some(Language.English)
          )
        ),
        termsAndConditions =
          TermsAndConditions(Accepted(acceptedDate, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))
      )

      optInRecordUpdateService.isPaperless(verifiedDate, preference) must be(None)
      optInRecordUpdateService.isPaperless(pendingEmailChangedDate, preference) must be(Some(false))
      optInRecordUpdateService.isPaperless(acceptedDate, preference) must be(None)
    }
  }
}
