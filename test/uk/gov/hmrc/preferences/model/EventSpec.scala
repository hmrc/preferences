/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsNumber, JsResultException, JsString, Json }
import uk.gov.hmrc.preferences.model.EmailEventType.*
import uk.gov.hmrc.preferences.model.Language.English
import uk.gov.hmrc.preferences.model.OptEventType.{ AdminOptOut, CustomerOptOut, OptIn, ReOptIn, SystemOptOut }
import uk.gov.hmrc.preferences.model.PageType.*
import utils.TestData.{ FIVE, TEST_EMAIL, TEST_EMAIL_ADDRESS, TEST_ENTITY_ID, TEST_TIME_INSTANT, TWO }

import java.time.Instant

class EventSpec extends PlaySpec {

  "EventType" should {
    import EventType.eventTypeReads

    "read the json correctly" in new Setup {
      JsString(emailVerifiedString).as[EventType] mustBe EmailVerified
      JsString(emailBouncedString).as[EventType] mustBe EmailBounced
      JsString(emailChangedString).as[EventType] mustBe EmailChanged
      JsString(emailReVerifyJourneyString).as[EventType] mustBe EmailReVerifyJourney
      JsString(emailBounceJourneyString).as[EventType] mustBe EmailBounceJourney
      JsString(optInString).as[OptEventType] mustBe OptIn
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        JsString("unknown").as[EventType]
      }

      intercept[JsResultException] {
        JsNumber(1).as[EventType]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(EmailVerified) mustBe JsString(emailVerifiedString)
      Json.toJson(EmailBounced) mustBe JsString(emailBouncedString)
      Json.toJson(EmailChanged) mustBe JsString(emailChangedString)
      Json.toJson(EmailReVerifyJourney) mustBe JsString(emailReVerifyJourneyString)
      Json.toJson(EmailBounceJourney) mustBe JsString(emailBounceJourneyString)
      Json.toJson(OptIn) mustBe JsString(optInString)
    }
  }

  "OptEventType.eventTypeFormat" should {
    import OptEventType.eventTypeFormat

    "read the json correctly" in new Setup {
      JsString(optInString).as[OptEventType] mustBe OptIn
      JsString(reOptInString).as[OptEventType] mustBe ReOptIn
      JsString(customerOptOutString).as[OptEventType] mustBe CustomerOptOut
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        JsString("unknown").as[OptEventType]
      }

      intercept[JsResultException] {
        JsNumber(1).as[OptEventType]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(OptIn) mustBe JsString(optInString)
      Json.toJson(ReOptIn) mustBe JsString(reOptInString)
      Json.toJson(CustomerOptOut) mustBe JsString(customerOptOutString)
    }
  }

  "EmailEventType.eventTypeFormat" should {
    import EmailEventType.emailActionFormat

    "read the json correctly" in new Setup {
      JsString(emailVerifiedString).as[EmailEventType] mustBe EmailVerified
      JsString(emailBouncedString).as[EmailEventType] mustBe EmailBounced
      JsString(emailChangedString).as[EmailEventType] mustBe EmailChanged
      JsString(emailReVerifyJourneyString).as[EmailEventType] mustBe EmailReVerifyJourney
      JsString(emailBounceJourneyString).as[EmailEventType] mustBe EmailBounceJourney
      JsString(sysExpiredPendingEmailRemovalString).as[EmailEventType] mustBe SystemExpiredPendingEmailRemoval
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        JsString("unknown").as[EmailEventType]
      }

      intercept[JsResultException] {
        JsNumber(1).as[EmailEventType]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(EmailVerified) mustBe JsString(emailVerifiedString)
      Json.toJson(EmailBounced) mustBe JsString(emailBouncedString)
      Json.toJson(EmailChanged) mustBe JsString(emailChangedString)
      Json.toJson(EmailReVerifyJourney) mustBe JsString(emailReVerifyJourneyString)
      Json.toJson(EmailBounceJourney) mustBe JsString(emailBounceJourneyString)
      Json.toJson(SystemExpiredPendingEmailRemoval) mustBe JsString(sysExpiredPendingEmailRemovalString)
    }
  }

  "PageType.pageTypeFormat" should {
    import PageType.pageTypeFormat

    "read the json correctly" in new Setup {
      JsString(iPageString).as[PageType] mustBe IPage
      JsString(tcPageString).as[PageType] mustBe TCPage
      JsString(uPageString).as[PageType] mustBe UPage
      JsString(reOptInPageString).as[PageType] mustBe ReOptInPage
      JsString(cysConfirmPageString).as[PageType] mustBe CYSConfirmPage
      JsString(androidOptInPageString).as[PageType] mustBe AndroidOptInPage
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        JsString("unknown").as[PageType]
      }

      intercept[JsResultException] {
        JsNumber(1).as[PageType]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(IPage) mustBe JsString(iPageString)
      Json.toJson(TCPage) mustBe JsString(tcPageString)
      Json.toJson(UPage) mustBe JsString(uPageString)
      Json.toJson(ReOptInPage) mustBe JsString(reOptInPageString)
      Json.toJson(CYSConfirmPage) mustBe JsString(cysConfirmPageString)
      Json.toJson(AndroidOptInPage) mustBe JsString(androidOptInPageString)
    }
  }

  "Event.eventFormats" should {
    import Event.eventFormats

    "read the json correctly" in new Setup {
      Json.parse(adminOptOutEventJsonStringWithType).as[Event] mustBe adminOptOutEvent
      Json.parse(customerOptOutEventJsonStringWithType).as[Event] mustBe customerOptOutEvent
      Json.parse(systemOptOutEventJsonStringWithType).as[Event] mustBe systemOptOutEvent
      Json.parse(optInEventJsonStringWithType).as[Event] mustBe optInEvent
      Json.parse(emailEventJsonStringWithType).as[Event] mustBe emailEvent
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(invalidEventJsonString).as[Event]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(adminOptOutEvent) mustBe Json.parse(adminOptOutEventJsonString)
      Json.toJson(customerOptOutEvent) mustBe Json.parse(customerOptOutEventJsonString)
      Json.toJson(systemOptOutEvent) mustBe Json.parse(systemOptOutEventJsonString)
      Json.toJson(optInEvent) mustBe Json.parse(optInEventJsonString)
      Json.toJson(emailEvent) mustBe Json.parse(emailEventJsonString)
    }
  }

  trait Setup {
    val emailVerifiedString = "email-verified"
    val emailBouncedString = "email-bounced"
    val emailChangedString = "email-changed"
    val emailReVerifyJourneyString = "email-re-verify-journey"
    val emailBounceJourneyString = "email-bounce-journey"
    val sysExpiredPendingEmailRemovalString = "system-expired-pending-email-removal"

    val iPageString = "IPage"
    val tcPageString = "TCPage"
    val uPageString = "UPage"
    val reOptInPageString = "ReOptInPage"
    val cysConfirmPageString = "CYSConfirmPage"
    val androidOptInPageString = "AndroidOptInPage"

    val optInString = "opt-in"
    val reOptInString = "re-opt-in"
    val customerOptOutString = "customer-opt-out"

    val version: Version = Version(TWO, FIVE)
    val optInPage: OptInPage = OptInPage(version = version, cohort = TWO, pageType = IPage)

    val adminOptOutEvent: AdminOptOutEvent =
      AdminOptOutEvent(
        eventType = AdminOptOut,
        entityId = TEST_ENTITY_ID,
        time = TEST_TIME_INSTANT,
        paperless = Some(true)
      )

    val adminOptOutEventJsonString: String =
      """{
        |"eventType":"admin-opt-out",
        |"entityId":"test_id",
        |"time":{"$date":{"$numberLong":"67813456000"}},
        |"paperless":true
        |}""".stripMargin

    val adminOptOutEventJsonStringWithType: String =
      """{
        |"_type": "uk.gov.hmrc.preferences.model.AdminOptOutEvent",
        |"eventType":"admin-opt-out",
        |"entityId":"test_id",
        |"time":{"$date":{"$numberLong":"67813456000"}},
        |"paperless":true
        |}""".stripMargin

    val customerOptOutEvent: CustomerOptOutEvent = CustomerOptOutEvent(
      eventType = OptIn,
      optInPage = optInPage,
      entityId = TEST_ENTITY_ID,
      time = TEST_TIME_INSTANT,
      language = English,
      paperless = Some(true)
    )

    val customerOptOutEventJsonString: String =
      """{
        |"eventType":"opt-in",
        |"optInPage":{"version":{"major":2,"minor":5},"cohort":2,"pageType":"IPage"},
        |"entityId":"test_id",
        |"time":{"$date":{"$numberLong":"67813456000"}},
        |"language":"en",
        |"paperless":true
        |}""".stripMargin

    val customerOptOutEventJsonStringWithType: String =
      """{
        |"_type": "uk.gov.hmrc.preferences.model.CustomerOptOutEvent",
        |"eventType":"opt-in",
        |"optInPage":{"version":{"major":2,"minor":5},"cohort":2,"pageType":"IPage"},
        |"entityId":"test_id",
        |"time":{"$date":{"$numberLong":"67813456000"}},
        |"language":"en",
        |"paperless":true
        |}""".stripMargin

    val systemOptOutEvent: SystemOptOutEvent =
      SystemOptOutEvent(
        eventType = SystemOptOut,
        entityId = TEST_ENTITY_ID,
        time = TEST_TIME_INSTANT,
        paperless = Some(true)
      )

    val systemOptOutEventJsonString: String =
      """{"eventType":"system-opt-out",
        |"entityId":"test_id",
        |"time":{"$date":{"$numberLong":"67813456000"}},
        |"paperless":true
        |}""".stripMargin

    val systemOptOutEventJsonStringWithType: String =
      """{
        |"_type": "uk.gov.hmrc.preferences.model.SystemOptOutEvent",
        |"eventType":"system-opt-out",
        |"entityId":"test_id",
        |"time":{"$date":{"$numberLong":"67813456000"}},
        |"paperless":true
        |}""".stripMargin

    val optInEvent: OptInEvent = OptInEvent(
      eventType = ReOptIn,
      optInPage = optInPage,
      entityId = TEST_ENTITY_ID,
      time = TEST_TIME_INSTANT,
      language = English,
      paperless = Some(true),
      emailAddress = Some(TEST_EMAIL_ADDRESS)
    )

    val optInEventJsonString: String =
      """{
        |"eventType":"re-opt-in",
        |"optInPage":{"version":{"major":2,"minor":5},"cohort":2,"pageType":"IPage"},
        |"entityId":"test_id",
        |"time":{"$date":{"$numberLong":"67813456000"}},
        |"language":"en",
        |"paperless":true,
        |"emailAddress":{"email":"test@test.com","lowercaseEmail":"test@test.com","bounceCount":0}
        |}""".stripMargin

    val optInEventJsonStringWithType: String =
      """{
        |"_type": "uk.gov.hmrc.preferences.model.OptInEvent",
        |"eventType":"re-opt-in",
        |"optInPage":{"version":{"major":2,"minor":5},"cohort":2,"pageType":"IPage"},
        |"entityId":"test_id",
        |"time":{"$date":{"$numberLong":"67813456000"}},
        |"language":"en",
        |"paperless":true,
        |"emailAddress":{"email":"test@test.com","lowercaseEmail":"test@test.com","bounceCount":0}
        |}""".stripMargin

    val emailEvent: EmailEvent = EmailEvent(
      entityId = TEST_ENTITY_ID,
      eventType = EmailVerified,
      emailAddress = TEST_EMAIL,
      paperless = Some(true),
      time = TEST_TIME_INSTANT
    )

    val emailEventJsonString: String =
      """{
        |"entityId":"test_id",
        |"eventType":"email-verified",
        |"emailAddress":"test@test.com",
        |"paperless":true,
        |"time":{"$date":{"$numberLong":"67813456000"}}
        |}""".stripMargin

    val emailEventJsonStringWithType: String =
      """{
        |"_type": "uk.gov.hmrc.preferences.model.EmailEvent",
        |"entityId":"test_id",
        |"eventType":"email-verified",
        |"emailAddress":"test@test.com",
        |"paperless":true,
        |"time":{"$date":{"$numberLong":"67813456000"}}
        |}""".stripMargin

    val invalidEventJsonString: String =
      """{
        |"_type": "uk.gov.hmrc.models.EmailEvent",
        |"eventType":"re-opt-in",
        |"optInPage":{"version":{"major":2,"minor":5},"cohort":2,"pageType":"IPage"},
        |"time1":{"$date":{"$numberLong":"67813456000"}},
        |"language":"en",
        |"paperless":true,
        |"emailAddress":{"email":"test@test.com","lowercaseEmail":"test@test.com","bounceCount":0}
        |}""".stripMargin
  }
}
