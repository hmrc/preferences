/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import org.bson.types.ObjectId
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsObject, JsResultException, JsValue, Json }
import uk.gov.hmrc.preferences.model.EmailPreference.LocalDateOption
import uk.gov.hmrc.preferences.model.Language.Welsh
import uk.gov.hmrc.preferences.model.TermsAndConditions.{ Accepted, Refused, Unknown }
import uk.gov.hmrc.preferences.model.OptEventType.{ AdminOptOut, CustomerOptOut, CustomerReOptOut, SystemOptOut }
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions
import uk.gov.hmrc.preferences.util.Dc
import utils.GenerateRandom
import utils.TestData.{ TEST_EMAIL, TEST_IDENTIFIER, TEST_LOCAL_DATE, TEST_MSG, TEST_STATUS, TEST_TIME_INSTANT }

import java.time.{ Instant, LocalDate }

class PreferencesSpec extends PlaySpec with SampleMongoPreferencesJson {

  private val termsAndConditionsAcceptedForGenericOnly = TermsAndConditions(Accepted(Dc.instantNow()))
  private val termsAndConditionsRefusedForGenericOnly = TermsAndConditions(Refused(Dc.instantNow()))

  "A preference" should {
    "have paperless true if opted in and the email is verified and not bounced" in {
      val prefs =
        Preferences(entityId = GenerateRandom.entityId(), termsAndConditions = termsAndConditionsAcceptedForGenericOnly)
          .copy(
            email = Some(EmailAddress(email = "some@email.com", verifiedOn = Some(Dc.instantNow())))
          )
      prefs.isVerifiedAndHasNoBounces mustBe true
    }

    "have paperless false if the email is not verified" in {
      val prefs =
        Preferences(entityId = GenerateRandom.entityId(), termsAndConditions = termsAndConditionsAcceptedForGenericOnly)
          .copy(
            email = None,
            pendingEmail = Some(PendingEmailAddress(email = "some@email.com"))
          )
      prefs.isVerifiedAndHasNoBounces mustBe false
    }

    "have paperless false if the email is bounced & t&c are accepted" in {
      val preferencesAsJson = Json
        .parse(s"""
                  |{
                  | "entityId" : "90139b8b-2900-4485-b173-c70b9e16b008",
                  |  "termsAndConditions": {
                  |    "generic" : {
                  |      "accepted": true,
                  |      "updatedAt": {"$$date": {"$$numberLong": "1431532777713"}},
                  |      "createdAt": {"$$date": {"$$numberLong": "1431532777713"}}
                  |    }
                  |  },
                  |  "createdAt" : {"$$date": {"$$numberLong": "1431532777713"}},
                  |  "updatedAt" : {"$$date": {"$$numberLong": "1431532777713"}},
                  |  "email": {
                  |		"email" : "test@digital.hmrc.gov.uk",
                  |		"lowercaseEmail" : "test@digital.hmrc.gov.uk",
                  |		"verifiedOn" : {"$$date": {"$$numberLong": "1431532777713"}},
                  |		"bounceCount" : 1,
                  |		"verifiedWithLink" : {
                  |			"_id" : "a3189b20-e104-43f2-a772-237c73bab998",
                  |			"linkSentTime" : {"$$date": {"$$numberLong": "1431532777713"}}
                  |		},
                  |		"language" : "en",
                  |		"lastBounce" : {
                  |			"timestamp" : {"$$date": {"$$numberLong": "1431532777713"}},
                  |			"errorCode" : 9002
                  |		}
                  |  }
                  | }""".stripMargin)
        .as[JsObject] ++ Json.obj("_id" -> Json.obj("$oid" -> defaultObjectId.toString))
      val pref = preferencesAsJson.as[Preferences]

      pref.isPaperless mustBe false
    }

    "have paperless false if opted out" in {
      val prefs =
        Preferences(entityId = GenerateRandom.entityId(), termsAndConditions = termsAndConditionsRefusedForGenericOnly)
          .copy(
            email = None,
            pendingEmail = None
          )
      prefs.isVerifiedAndHasNoBounces mustBe false
    }
  }

  "form type is contactable" should {

    "be Contactable if email is valid and form type is activated" in {
      val email = EmailAddress(email = "some@email.com", verifiedOn = Some(Dc.instantNow()))
      val prefs = Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly
      ).copy(email = Some(email))
      prefs.contactabilityStatus() mustBe Contactable(email)
    }

    "be PendingVerification if preference has valid form type, no verified email and pending email address" in {
      val prefs =
        Preferences(entityId = GenerateRandom.entityId(), termsAndConditions = termsAndConditionsAcceptedForGenericOnly)
          .copy(
            email = None,
            pendingEmail = Some(PendingEmailAddress(email = "some@email.com"))
          )
      prefs.contactabilityStatus() must be(PendingVerification)
    }

    "be PendingVerification if preference has valid form type, no verified email and pending email is bounced" in {
      val prefs =
        Preferences(entityId = GenerateRandom.entityId(), termsAndConditions = termsAndConditionsAcceptedForGenericOnly)
          .copy(
            email = None,
            pendingEmail = Some(
              PendingEmailAddress(
                email = "some@email.com",
                lastBounce = Some(EmailBounce(errorCode = None, timestamp = Dc.instantNow()))
              )
            )
          )
      prefs.contactabilityStatus() must be(PendingVerification)
    }

    "be OptedOut if preference has valid form type but no verified or unverified emails" in {
      val prefs =
        Preferences(entityId = GenerateRandom.entityId(), termsAndConditions = termsAndConditionsRefusedForGenericOnly)
          .copy(
            email = None,
            pendingEmail = None
          )
      prefs.contactabilityStatus() must be(OptedOut)
    }

    "be Bounced if preference has valid form types but current email is bounced" in {
      val prefs = basePreference()
        .as[Preferences]
        .copy(
          email = Some(
            EmailAddress(
              email = "some@email.com",
              lastBounce = Some(EmailBounce(timestamp = Dc.instantNow(), errorCode = None)),
              verifiedOn = Some(Dc.instantNow())
            )
          ),
          pendingEmail = Some(PendingEmailAddress(email = "someOther@email.com"))
        )
      prefs.contactabilityStatus() must be(Bounced)
    }
  }

  "Preferences" should {
    "successfully deserialize from json" in {
      val entityId = GenerateRandom.entityId()
      val preferencesJsonObj = basePreference(entityId)
      preferencesJsonObj.as[Preferences] must have(
        Symbol("entityId")(entityId),
        Symbol("email")(
          Some(
            EmailAddress(
              "bob@example.com",
              verifiedOn = Some(date),
              verifiedWithLink = Some(EmailVerificationLink("id", date)),
              language = Some(Language.English)
            )
          )
        ),
        Symbol("pendingEmail")(None),
        Symbol("termsAndConditions")(TermsAndConditions(generic = Unknown)),
        Symbol("createdAt")(date),
        Symbol("updatedAt")(date)
      )
    }
  }

  "terms and conditions" should {

    val stableBasePreferenceJson = basePreference()

    "not be set when no T&C's are present" in {
      val pref = stableBasePreferenceJson.as[Preferences]

      pref.termsAndConditions must be(TermsAndConditions(generic = Unknown))
      pref.isOptedOut("generic") mustBe true

      Json.toJson(pref) must be(stableBasePreferenceJson)
    }

    "be accepted when Generic T&C's are accepted" in {
      val pref = (stableBasePreferenceJson ++ termsAndConditionsJson(accepted = true)).as[Preferences]

      pref.termsAndConditions must be(TermsAndConditions(generic = Accepted(updatedAt = termsAndConditionsUpdatedAt)))
      pref.isOptedOut("generic") mustBe false

      Json.toJson(pref) must be(stableBasePreferenceJson ++ termsAndConditionsJson(accepted = true))
    }

    "not be accepted when Generic T&C's are not accepted" in {
      val pref = (stableBasePreferenceJson ++ termsAndConditionsJson(accepted = false)).as[Preferences]

      pref.termsAndConditions must be(TermsAndConditions(generic = Refused(updatedAt = termsAndConditionsUpdatedAt)))
      pref.isOptedOut("generic") mustBe true

      Json.toJson(pref) must be(stableBasePreferenceJson ++ termsAndConditionsJson(accepted = false))
    }

    "set eventType in T&C on SystemOptOut" in {
      val pref =
        (stableBasePreferenceJson ++ termsAndConditionsJson(accepted = false, evenType = SystemOptOut)).as[Preferences]

      pref.termsAndConditions must be(
        TermsAndConditions(generic = Refused(updatedAt = termsAndConditionsUpdatedAt, eventType = Some(SystemOptOut)))
      )

      Json.toJson(pref) must be(
        stableBasePreferenceJson ++ termsAndConditionsJson(accepted = false, evenType = SystemOptOut)
      )
    }
    "set eventType in T&C on AdminOptOut" in {
      val pref =
        (stableBasePreferenceJson ++ termsAndConditionsJson(accepted = false, evenType = AdminOptOut)).as[Preferences]

      pref.termsAndConditions must be(
        TermsAndConditions(generic = Refused(updatedAt = termsAndConditionsUpdatedAt, eventType = Some(AdminOptOut)))
      )

      Json.toJson(pref) must be(
        stableBasePreferenceJson ++ termsAndConditionsJson(accepted = false, evenType = AdminOptOut)
      )
    }
    "not be set eventType in T&C on CustomerOptOut" in {
      val pref = (stableBasePreferenceJson ++ termsAndConditionsJson(accepted = false, evenType = CustomerOptOut))
        .as[Preferences]

      pref.termsAndConditions must be(
        TermsAndConditions(generic = Refused(updatedAt = termsAndConditionsUpdatedAt, eventType = Some(CustomerOptOut)))
      )

      Json.toJson(pref) must be(stableBasePreferenceJson ++ termsAndConditionsJson(accepted = false))
    }
    "not be set eventType in T&C on CustomerReOptOut" in {
      val pref = (stableBasePreferenceJson ++ termsAndConditionsJson(accepted = false, evenType = CustomerReOptOut))
        .as[Preferences]

      pref.termsAndConditions must be(
        TermsAndConditions(
          generic = Refused(updatedAt = termsAndConditionsUpdatedAt, eventType = Some(CustomerReOptOut))
        )
      )

      Json.toJson(pref) must be(stableBasePreferenceJson ++ termsAndConditionsJson(accepted = false))
    }

  }

  "reset pending" should {
    "create a new pending email if one doesn't exist" in new ResetPendingTestCase {
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsRefusedForGenericOnly,
        pendingEmail = None
      ).resetPending(pendingEmail, timeSource, language = Some(Welsh)) match {
        case PendingEmailAddress(email, _, Some(EmailVerificationLink(_, sent, _, _)), _, language, _) =>
          email mustBe pendingEmail
          sent mustBe now
          language.get mustBe Welsh
        case _ => fail("PendingEmailAddress was not created as expected")
      }
    }

    "create a new pending email, with return link text and url, if one doesn't exist" in new ResetPendingTestCase {
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsRefusedForGenericOnly,
        pendingEmail = None
      )
        .resetPending(pendingEmail, timeSource, Some("Return Text"), Some("Return Url"), Some(Welsh)) match {
        case PendingEmailAddress(
              email,
              _,
              Some(EmailVerificationLink(_, sent, Some(returnText), Some(returnUrl))),
              _,
              language,
              _
            ) =>
          email mustBe pendingEmail
          returnText mustBe "Return Text"
          returnUrl mustBe "Return Url"
          sent mustBe now
          language.get mustBe Welsh
        case aaaaa => fail(s"PendingEmailAddress was not created as expected $aaaaa")
      }
    }

    "update the time sent on the verification link if the email address is unchanged and the link is not expired" in new ResetPendingTestCase {
      private val verificationLink = EmailVerificationLink(linkSentTime = now.minusDays(1))
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        pendingEmail = Some(
          PendingEmailAddress(email = pendingEmail, verificationLink = Some(verificationLink))
        )
      ).resetPending(pendingEmail, timeSource) match {
        case PendingEmailAddress(email, _, Some(updated @ EmailVerificationLink(_, _, _, _)), _, _, _)
            if email == pendingEmail =>
          updated mustBe verificationLink.copy(linkSentTime = now)
        case _ => fail("PendingEmailAddress was not created as expected")
      }
    }

    "set a new verification link if the email has changed" in new ResetPendingTestCase {
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        pendingEmail = Some(
          PendingEmailAddress(
            email = GenerateRandom.email(),
            verificationLink = Some(EmailVerificationLink("1234", linkSentTime = now.minusDays(1)))
          )
        )
      ).resetPending(pendingEmail, timeSource, language = Some(Welsh)) match {
        case PendingEmailAddress(email, _, Some(EmailVerificationLink(id, sent, _, _)), _, language, _) =>
          email mustBe pendingEmail
          sent mustBe now
          id must not be "1234"
          language.get mustBe Welsh
        case _ => fail("PendingEmailAddress was not created as expected")
      }
    }

    "set a new verification link if the email address is unchanged and the existing link has expired" in new ResetPendingTestCase {
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        pendingEmail = Some(
          PendingEmailAddress(
            email = pendingEmail,
            verificationLink = Some(
              EmailVerificationLink(
                "1234",
                now.minusDays(EmailVerificationLink.verificationLinkTimeout.toDays.toInt + 1)
              )
            )
          )
        )
      ).resetPending(pendingEmail, timeSource) match {
        case PendingEmailAddress(email, _, Some(EmailVerificationLink(id, sent, _, _)), _, _, _) =>
          email mustBe pendingEmail
          sent mustBe now
          id must not be "1234"
        case _ => fail("PendingEmailAddress was not created as expected")
      }
    }

    "set a new verification link if the email address is unchanged and there is no previous link" in new ResetPendingTestCase {
      Preferences(
        entityId = GenerateRandom.entityId(),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        pendingEmail = Some(PendingEmailAddress(email = pendingEmail, verificationLink = None))
      ).resetPending(pendingEmail, timeSource) match {
        case PendingEmailAddress(email, _, Some(EmailVerificationLink(_, sent, _, _)), _, _, _)
            if email == pendingEmail =>
          sent mustBe now
        case _ => fail("PendingEmailAddress was not created as expected")
      }
    }
  }

  "EmailPreference.formats" should {
    import EmailPreference.formats

    "read the valid json" in new Setup {
      Json.parse(emailPrefJsonString).as[EmailPreference] mustBe emailPreference
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(emailPrefInvalidJsonString).as[EmailPreference]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(emailPreference) mustBe Json.parse(emailPrefJsonString)
    }
  }

  "LocalDateOption.formatLocalDateOption" should {
    import EmailPreference.formatLocalDateOption

    "read the valid json" in new Setup {
      Json.parse(localDateOptionJsonString).as[LocalDateOption] mustBe localDateOption
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(localDateOptionInvalidJsonString).as[LocalDateOption]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(localDateOption) mustBe Json.parse(localDateOptionJsonString)
    }
  }

  "MarkForDeEnrolment.formatsDe" should {
    import MarkForDeEnrolment.formatsDe

    "read the valid json" in new Setup {
      Json.parse(markDeEnrolJsonString).as[MarkForDeEnrolment] mustBe markDeEnrol
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(markDeEnrolInvalidJsonString).as[MarkForDeEnrolment]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(markDeEnrol) mustBe Json.parse(markDeEnrolJsonString)
    }
  }

  "MarkForDeEnrolmentOption.deFormat" should {
    import MarkForDeEnrolmentOption.deFormat

    "read the valid json" in new Setup {
      Json.parse(markDeEnrolOptJsonString).as[MarkForDeEnrolmentOption] mustBe markDeEnrolOpt
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(markDeEnrolOptInvalidJsonString).as[MarkForDeEnrolmentOption]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(markDeEnrolOpt) mustBe Json.parse(markDeEnrolOptJsonString)
    }
  }

  trait ResetPendingTestCase {
    val pendingEmail: String = GenerateRandom.email()
    val now: Instant = Dc.instantNow()
    val timeSource: () => Instant = () => now
  }
}

trait SampleMongoPreferencesJson {

  val defaultObjectId: ObjectId = ObjectId.get()
  val date: Instant = Instant.parse("2015-05-13T00:00:00Z")

  def entityIdOnlyPreference(entityId: EntityId): JsValue =
    Json.parse(
      s"""{
         |    "createdAt" : {"$$date": {"$$numberLong": "${date.getMillis}"}},
         |    "updatedAt" : {"$$date": {"$$numberLong": "${date.getMillis}"}},
         |    "pendingEmail" : {
         |        "verificationLink" : {
         |            "linkSentTime" : {"$$date": {"$$numberLong": "${date.getMillis}"}},
         |            "_id" : "b827c56c-c411-4dca-aa31-a8c766fa8faa"
         |        },
         |        "email" : "test@test.com",
         |        "lowercaseEmail" : "test@test.com",
         |        "reminder" : {
         |            "status" : "todo",
         |            "updatedAt" :  {"$$date": {"$$numberLong": "${date.getMillis}"}}
         |        },
         |        "language": "cy"
         |    },
         |    "termsAndConditions" : {
         |        "generic" : {
         |            "accepted" : true,
         |            "updatedAt" :  {"$$date": {"$$numberLong": "${date.getMillis}"}}
         |        }
         |    },
         |    "entityId" : "${entityId.value}"
         |}
       """.stripMargin
    )

  def basePreference(entityId: EntityId = GenerateRandom.entityId(), id: ObjectId = defaultObjectId): JsObject =
    Json
      .parse(s"""
                |{
                |  "termsAndConditions": {},
                |  "createdAt" : {"$$date": {"$$numberLong": "${date.getMillis}"}},
                |  "updatedAt" : {"$$date": {"$$numberLong": "${date.getMillis}"}},
                |  "email" : {
                |    "email" : "bob@example.com",
                |    "lowercaseEmail" : "bob@example.com",
                |    "verifiedOn" : {"$$date": {"$$numberLong": "${date.getMillis}"}},
                |    "bounceCount": 0,
                |    "verifiedWithLink" : {
                |      "linkSentTime" : {"$$date": {"$$numberLong": "${date.getMillis}"}},
                |      "_id" : "id"
                |    },
                |    "language": "en"
                |  }
                |}
    """.stripMargin)
      .as[JsObject] ++
      Json.obj("entityId" -> entityId.value) ++
      Json.obj("_id" -> Json.obj("$oid" -> id.toString))

  val termsAndConditionsUpdatedAt: Instant = Instant.parse("2015-05-13T15:59:37.713Z")

  def termsAndConditionsJson(accepted: Boolean): JsObject =
    Json
      .parse(s"""
                |{
                | "termsAndConditions": {
                |   "generic" : {
                |     "accepted": $accepted,
                |     "updatedAt": {"$$date": {"$$numberLong": "1431532777713"}}
                |   }
                | }
                |}
    """.stripMargin)
      .as[JsObject]

  def termsAndConditionsJson(accepted: Boolean, evenType: OptEventType): JsObject =
    Json
      .parse(s"""
                |{
                | "termsAndConditions": {
                |   "generic" : {
                |     "accepted": $accepted,
                |     "updatedAt": {"$$date": {"$$numberLong": "1431532777713"}},
                |     "eventType": "${evenType.entryName}"
                |   }
                | }
                |}
    """.stripMargin)
      .as[JsObject]

}

trait Setup {
  val emailPreference: EmailPreference =
    EmailPreference(
      email = TEST_EMAIL,
      status = TEST_STATUS,
      mailboxFull = false,
      message = Some(TEST_MSG),
      linkSent = Some(TEST_LOCAL_DATE)
    )

  val markDeEnrol: MarkForDeEnrolment = MarkForDeEnrolment(time = TEST_TIME_INSTANT, identifier = TEST_IDENTIFIER)
  val markDeEnrolOpt: MarkForDeEnrolmentOption = MarkForDeEnrolmentOption(Some(markDeEnrol))

  val emailPrefJsonString: String =
    """{
      |"email":"test@test.com",
      |"status":"verified",
      |"mailboxFull":false,
      |"message":"test_message",
      |"linkSent":"2025-12-10"
      |}""".stripMargin

  val emailPrefInvalidJsonString: String =
    """{
      |"email":"test@test.com",
      |"mailboxFull":false,
      |"message":"test_message",
      |"linkSent":"2025-12-10"
      |}""".stripMargin

  val localDateOption: LocalDateOption = LocalDateOption(Some(TEST_LOCAL_DATE))

  val localDateOptionJsonString: String = """{"date":"2025-12-10"}""".stripMargin
  val localDateOptionInvalidJsonString: String = """{"date":""}""".stripMargin

  val markDeEnrolJsonString: String =
    """{"time":{"$date":{"$numberLong":"67813456000"}},"identifier":"test_12345"}""".stripMargin

  val markDeEnrolInvalidJsonString: String =
    """{"time":{"$date":{"$numberLong":"67813456000"}}}""".stripMargin

  val markDeEnrolOptJsonString: String =
    """{"deEnrolmentOption":{"time":{"$date":{"$numberLong":"67813456000"}},"identifier":"test_12345"}}""".stripMargin

  val markDeEnrolOptInvalidJsonString: String =
    """{"deEnrolmentOption":{"time":{"$date":{"$numberLong":"67813456000"}}}}""".stripMargin
}
