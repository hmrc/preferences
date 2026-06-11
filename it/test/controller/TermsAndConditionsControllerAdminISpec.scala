/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package controller

import conf.{ ISpec, PreferencesTestRoutes }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.Result
import play.api.test.{ FakeHeaders, FakeRequest }
import play.api.test.Helpers.POST
import uk.gov.hmrc.preferences.controllers.model.EmailToken
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.test.EntityResolverSupport

class TermsAndConditionsControllerAdminISpec
    extends TermsAndConditionsControllerISpecBase with ISpec with EntityResolverSupport {

  // Generic version of optin - for test preparation
  def optIn(replaceEmail: String): Option[Preferences] = {
    val _ = withEntity(entityId.value, Some(nino.value), Some(utr.value))

    val (bearerToken, _) = authHelper
      .authorisedTokenFor(ggAuthPort, nino, utr)
      .futureValue

    val _ = preferencesTestRoutes
      .post(
        PreferencesTestRoutes.optin,
        readFromResource("optInGenericPayload.json", replaceEmail),
        Some(("Authorization", bearerToken))
      )

    getPreference
  }

  def verify(p: PendingEmailAddress): Option[Preferences] = {
    val token: EmailToken = EmailToken(p.verificationLink.get._id)
    val fakeRequest = FakeRequest(POST, "", FakeHeaders(), Json.toJson(token))
    val _ = emailVerificationController.verifyEmail().apply(fakeRequest).futureValue

    getPreference
  }

  def optInAndVerify(replaceEmail: String): Option[Preferences] = {
    val p = optIn(replaceEmail)
    assert(p.isDefined, "preference was not found")
    assert(!p.get.isPaperless, "isPaperless should be false; unverified")

    val v = verify(p.get.pendingEmail.get)
    assert(v.get.isPaperless, "isPaperless should be true; verified")
    v
  }

  def adminOptout(query: String, replaceEmail: String): preferencesTestRoutes.FakeResponse =
    preferencesTestRoutes
      .post(
        PreferencesTestRoutes.adminOptOut(query),
        readFromResource("manualOptOutRequest.json", replaceEmail),
        None
      )

  def getPreference: Option[Preferences] =
    repo
      .findBy(entityId)
      .futureValue
}
