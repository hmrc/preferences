/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers

import org.scalatestplus.play.PlaySpec
import play.api.mvc.Results.{ BadRequest, InternalServerError, NotFound, Unauthorized }
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{ InProgress, ToDo }
import uk.gov.hmrc.preferences.exceptions.{ EntityBadRequest, EntityNotFound, EntityRequestServerError, EntityResolverResponse, EntityUnauthorised }
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.model.{ EmailAddress, EmailBounce, PendingEmailAddress, Preferences, Reminder, TermsAndConditions }
import uk.gov.hmrc.preferences.util.Dc
import utils.TestData.{ FIVE, FOURTEEN, TEST_CODE, TEST_EMAIL, TEST_EMAIL_VERIFICATION_LINK, TEST_ENTITY_ID, TEST_ID, TEST_TERMS_AND_CONDITIONS }

import java.time.Instant

class PackageSpec extends PlaySpec {

  "prefsToAuditDetails" should {
    "return the correct map" in {

      val email = EmailAddress(email = "some@email.com", verifiedOn = Some(Dc.instantNow()))
      val pendingEmail =
        PendingEmailAddress(
          email = TEST_EMAIL,
          lastBounce = Some(EmailBounce(errorCode = Some(TEST_CODE), timestamp = Dc.instantNow())),
          verificationLink = Some(TEST_EMAIL_VERIFICATION_LINK),
          reminder = Some(Reminder(status = InProgress, updatedAt = Dc.instantNow()))
        )

      val prefs: Preferences =
        Preferences(
          entityId = TEST_ENTITY_ID,
          termsAndConditions = TEST_TERMS_AND_CONDITIONS,
          email = Some(email),
          pendingEmail = Some(pendingEmail)
        )

      val resultMap: Map[String, String] = prefsToAuditDetails(prefs)

      resultMap.size mustBe FOURTEEN
      resultMap("entityId") mustBe TEST_ID
      resultMap("genericTermsAndConditions") mustBe "accepted"
      resultMap("lastBounceErrorCode") mustBe TEST_CODE.toString
      resultMap("emailVerificationLinkreminderstatus") mustBe InProgress.name
      resultMap("emailVerificationLinksecondReminderstatus") mustBe ToDo.name
      resultMap("pendingEmail") mustBe TEST_EMAIL
      resultMap("updatedAt") must not be empty
      resultMap("createdAt") must not be empty
      resultMap("genericTermsAndConditionsacceptedAt") must not be empty
      resultMap("emailVerificationLinkreminderupdatedAt") must not be empty
      resultMap("emailVerificationLinksecondReminderupdatedAt") must not be empty
      resultMap("emailVerificationLinkSentTime") must not be empty
      resultMap("lastBounceTimestamp") must not be empty
    }
  }

  "httpErrorResultForEntity" should {
    "return the correct output for the input response" in {
      httpErrorResultForEntity(EntityNotFound) mustBe NotFound("Entity not found")
      httpErrorResultForEntity(EntityBadRequest("bad request")) mustBe BadRequest("bad request")
      httpErrorResultForEntity(EntityRequestServerError("server error")) mustBe InternalServerError(
        "Error, server error"
      )
      httpErrorResultForEntity(EntityUnauthorised("unauthorized")) mustBe Unauthorized("unauthorized")
    }
  }
}
