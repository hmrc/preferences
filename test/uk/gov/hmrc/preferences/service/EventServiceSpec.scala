/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ times, verify, when }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.model.EmailEventType.{ EmailBounceJourney, EmailBounced, EmailVerified }
import uk.gov.hmrc.preferences.util.Dc
import uk.gov.hmrc.preferences.model.PageType.{ AndroidOptInPage, IPage }
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.model.{ AdminOptOutEvent, CustomerOptOutEvent, EmailAddress, EmailEvent, EmailEventType, EntityId, Event, EventsResponse, Language, OptEventType, OptInEvent, OptInPage, PageType, Preferences, SystemOptOutEvent, TermsAndConditions, Version }
import uk.gov.hmrc.preferences.repository.PreferencesRepository

import java.time.Instant
import scala.collection.immutable
import scala.concurrent.Future

class EventServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  "event service" should {

    "get events" in new TestCase {
      val entityId = EntityId("entity-id")
      val prefs = Preferences(
        entityId,
        TermsAndConditions(Accepted(Dc.instantNow(), None)),
        ObjectId.get(),
        events = Some(
          List(
            OptInEvent(
              OptEventType.OptIn,
              OptInPage(Version(1, 2), 1, IPage),
              EntityId("entity-id-2"),
              Dc.instantNow(),
              Language.English,
              None,
              None
            )
          )
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(prefs)))
      val events: Seq[EventsResponse] = eventService.getEvents(entityId).futureValue
      events.size must be(1)
      verify(mockPreferencesRepository, times(1)).findBy(any[EntityId])(any[HeaderCarrier])
    }

    "get opt-in event" in new TestCase {
      val entityId = EntityId("entity-id")
      val timeStamp = Dc.instantNow()
      val prefs = Preferences(
        entityId,
        TermsAndConditions(Accepted(Dc.instantNow(), None)),
        ObjectId.get(),
        events = Some(
          List(
            OptInEvent(
              OptEventType.OptIn,
              OptInPage(Version(1, 2), 1, IPage),
              EntityId("entity-id-2"),
              timeStamp,
              Language.English,
              None,
              None
            )
          )
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(prefs)))
      val events: Seq[EventsResponse] = eventService.getEvents(entityId).futureValue

      events.head mustBe (EventsResponse(
        OptEventType.OptIn,
        None,
        timeStamp
      ))
    }

    "get customer opt-out event" in new TestCase {
      val entityId: EntityId = EntityId("entity-id")
      val timeStamp: Instant = Dc.instantNow()

      val prefs: Preferences = Preferences(
        entityId,
        TermsAndConditions(Accepted(Dc.instantNow(), None)),
        ObjectId.get(),
        events = Some(
          List(
            CustomerOptOutEvent(
              OptEventType.CustomerOptOut,
              OptInPage(Version(1, 2), 1, IPage),
              EntityId("entity-id-2"),
              timeStamp,
              Language.English,
              None
            )
          )
        )
      )

      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(prefs)))

      val events: Seq[EventsResponse] = eventService.getEvents(entityId).futureValue

      events.head mustBe (EventsResponse(
        OptEventType.CustomerOptOut,
        None,
        timeStamp
      ))
    }

    "get admin opt-out event" in new TestCase {
      val entityId: EntityId = EntityId("entity-id")
      val timeStamp: Instant = Dc.instantNow()

      val prefs: Preferences = Preferences(
        entityId,
        TermsAndConditions(Accepted(Dc.instantNow(), None)),
        ObjectId.get(),
        events = Some(
          List(
            AdminOptOutEvent(
              OptEventType.AdminOptOut,
              EntityId("entity-id-2"),
              timeStamp,
              Some(true)
            )
          )
        )
      )

      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(prefs)))
      val events: Seq[EventsResponse] = eventService.getEvents(entityId).futureValue

      events.head mustBe (EventsResponse(
        OptEventType.AdminOptOut,
        None,
        timeStamp
      ))
    }

    "get system opt-out event" in new TestCase {
      val entityId: EntityId = EntityId("entity-id")
      val timeStamp: Instant = Dc.instantNow()

      val prefs: Preferences = Preferences(
        entityId,
        TermsAndConditions(Accepted(Dc.instantNow(), None)),
        ObjectId.get(),
        events = Some(
          List(
            SystemOptOutEvent(
              OptEventType.SystemOptOut,
              EntityId("entity-id-2"),
              timeStamp,
              Some(true)
            )
          )
        )
      )

      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(prefs)))

      val events: Seq[EventsResponse] = eventService.getEvents(entityId).futureValue

      events.head mustBe (EventsResponse(
        OptEventType.SystemOptOut,
        None,
        timeStamp
      ))
    }

    "check opt-out done by mobile " in new TestCase {
      val entityId = EntityId("entity-id")
      val timeStamp = Dc.instantNow()
      val prefs = Preferences(
        entityId,
        TermsAndConditions(
          Accepted(Dc.instantNow(), None, optInPage = Some(OptInPage(Version(0, 0), cohort = 1, AndroidOptInPage)))
        ),
        ObjectId.get(),
        events = Some(
          List(
            CustomerOptOutEvent(
              OptEventType.CustomerOptOut,
              OptInPage(Version(1, 2), 1, AndroidOptInPage),
              EntityId("entity-id-2"),
              timeStamp,
              Language.English,
              None
            )
          )
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(prefs)))
      val events: Seq[EventsResponse] = eventService.getEvents(entityId).futureValue

      events.head mustBe (EventsResponse(
        OptEventType.CustomerOptOut,
        None,
        timeStamp,
        true
      ))
    }

    "get email-verified event" in new TestCase {
      val entityId = EntityId("entity-id")
      val timeStamp = Dc.instantNow()
      val prefs = Preferences(
        entityId,
        TermsAndConditions(Accepted(Dc.instantNow(), None)),
        ObjectId.get(),
        events = Some(
          List(
            EmailEvent(
              EntityId("entity-id-2"),
              EmailVerified,
              "test@gmail.com",
              None,
              timeStamp
            )
          )
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(prefs)))
      val events: Seq[EventsResponse] = eventService.getEvents(entityId).futureValue

      events.head mustBe EventsResponse(EmailEventType.EmailVerified, Some("test@gmail.com"), timeStamp)
    }

    "EmailBounced event should be ignored" in new TestCase {
      val entityId = EntityId("entity-id")
      val timeStamp = Dc.instantNow()
      val prefs = Preferences(
        entityId,
        TermsAndConditions(Accepted(Dc.instantNow(), None)),
        ObjectId.get(),
        events = Some(
          List(
            EmailEvent(
              EntityId("entity-id-2"),
              EmailBounced,
              "test@gmail.com",
              None,
              timeStamp
            )
          )
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(prefs)))
      val events: Seq[EventsResponse] = eventService.getEvents(entityId).futureValue

      events mustBe (Seq.empty)
    }

    "EmailBounceJourney event should be ignored" in new TestCase {
      val entityId = EntityId("entity-id")
      val timeStamp = Dc.instantNow()
      val prefs = Preferences(
        entityId,
        TermsAndConditions(Accepted(Dc.instantNow(), None)),
        ObjectId.get(),
        events = Some(
          List(
            EmailEvent(
              EntityId("entity-id-2"),
              EmailBounceJourney,
              "test@gmail.com",
              None,
              timeStamp
            )
          )
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(prefs)))
      val events: Seq[EventsResponse] = eventService.getEvents(entityId).futureValue

      events mustBe (Seq.empty)
    }

    "get no events" in new TestCase {
      val entityId = EntityId("entity-id")
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier])).thenReturn(Future.successful(None))
      val events: Seq[EventsResponse] = eventService.getEvents(entityId).futureValue
      events.size must be(0)
      verify(mockPreferencesRepository, times(1)).findBy(any[EntityId])(any[HeaderCarrier])
    }
  }

  trait TestCase {
    import scala.concurrent.ExecutionContext.Implicits.global
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockPreferencesRepository: PreferencesRepository = mock[PreferencesRepository]
    lazy val eventService: EventService = new EventService(mockPreferencesRepository)
  }

}
