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
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.preferences.jobs.RunModeBridge
import uk.gov.hmrc.preferences.model.OptEventType.OptIn
import uk.gov.hmrc.preferences.model.Language.English
import uk.gov.hmrc.preferences.model.PageType.IPage
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.model._
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import utils.GenerateRandom

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{ Duration, MINUTES }

class EventsAndPreferenceUpdateServiceSpec
    extends PlaySpec with MockitoSugar with ScalaFutures with IntegrationPatience with BeforeAndAfterEach {

  private val mockLockRepository = mock[MongoLockRepository]
  private val mockPreferencesRepository = mock[PreferencesRepository]
  private val mockRunModeBridge = mock[RunModeBridge]

  override def beforeEach(): Unit = {
    super.beforeEach()
    SharedMetricRegistries.clear()
  }

  when(
    mockPreferencesRepository
      .updatePreferenceEventTypeAndOptInPage(any[ObjectId], any[OptPageEvent], any[List[Event]])
  )
    .thenReturn(Future(true))

  when(mockRunModeBridge.getOptionalMillisForScheduling(any[String], any[String]))
    .thenReturn(Some(Duration(1, MINUTES)))

  "Preference" should {

    "be updated with cohort 8 after switch date" in {

      val entityId = GenerateRandom.entityId()
      val optInUpdateResult = OptInUpdateResult(1, 0)

      val dateTime = Instant.parse("2020-01-16T15:00:00Z")
      val preference = new Preferences(
        entityId = entityId,
        pendingEmail = Some(PendingEmailAddress("pendingemail@email.com", None, None)),
        termsAndConditions =
          TermsAndConditions(Accepted(dateTime, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))
      )

      when(mockRunModeBridge.getEnabledFlag(any[String], any[String])).thenReturn(false)

      val optInRecordUpdateService =
        new OptInRecordUpdateService(mockLockRepository, mockPreferencesRepository, mockRunModeBridge)

      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference).futureValue

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(
          OptIn,
          OptInPage(Version(1, 0), 8, IPage),
          preference.entityId,
          dateTime,
          Language.English,
          Some(false),
          None
        ),
        List(OptInEvent(OptIn, OptInPage(Version(1, 0), 8, IPage), entityId, dateTime, English, Some(false), None))
      )
    }

    "process preference and optInEvent with cohort 7 before switch date" in {
      val entityId = GenerateRandom.entityId()
      val optInUpdateResult = OptInUpdateResult(1, 0)

      val dateTime = Instant.parse("2019-01-16T15:00:00Z")
      val preference = new Preferences(
        entityId = entityId,
        pendingEmail = Some(PendingEmailAddress("pendingemail@email.com", None, None)),
        termsAndConditions =
          TermsAndConditions(Accepted(dateTime, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))
      )

      val optInRecordUpdateService =
        new OptInRecordUpdateService(mockLockRepository, mockPreferencesRepository, mockRunModeBridge)
      optInRecordUpdateService.updatePreferences(optInUpdateResult, preference).futureValue

      verify(mockPreferencesRepository, times(1)).updatePreferenceEventTypeAndOptInPage(
        preference._id,
        OptInEvent(
          OptIn,
          OptInPage(Version(0, 0), 7, IPage),
          preference.entityId,
          dateTime,
          Language.English,
          Some(false),
          None
        ),
        List(OptInEvent(OptIn, OptInPage(Version(0, 0), 7, IPage), entityId, dateTime, English, Some(false), None))
      )
    }
  }
}
