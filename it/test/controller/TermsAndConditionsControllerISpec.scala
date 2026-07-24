/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controller

import conf.PreferencesTestRoutes.*
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{ POST, route }
import uk.gov.hmrc.paperless.controllers.model.{ EmailPreference, PreferenceResponse }
import uk.gov.hmrc.preferences.model.Event.*
import uk.gov.hmrc.preferences.model.PageType.{ AndroidOptInPage, AndroidOptOutPage, AndroidReOptInPage, AndroidReOptOutPage, CYSConfirmPage, IPage, IosOptInPage, IosOptOutPage, IosReOptInPage, IosReOptOutPage, ReOptInPage }
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import uk.gov.hmrc.preferences.util.Dc
import utils.CustomMatchers.matchPreferenceResponse
import utils.GenerateRandom
/*
G  = generic (terms and conditions)
E  = email

EV  -> Email is present
EX  -> Email is removed
 */

class TermsAndConditionsControllerISpec extends TermsAndConditionsControllerISpecBase with EntityResolverSupport {

  "opt in for generic (G V)" should {
    "save the generic terms and conditions only for the given entityId if no preference exists (GX EX -> GV EV)" in new TestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesTestRoutes
        .post(
          `/preferences/:entityId/optin`(entityId),
          readFromResource("optInGenericPayload.json", email),
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .status must be(CREATED)

      private val preferenceResponse =
        preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json.as[PreferenceResponse]

      preferenceResponse.termsAndConditions must matchPreferenceResponse(
        Predef.Map("generic" -> true),
        shouldBeUpdatedAfterThisTime
      )
      preferenceResponse.email must matchPattern {
        case Some(EmailPreference(_, false, false, false, Some(_), None, _, _, _, _)) =>
      }
    }

    "save the generic terms and conditions without specifying entityId" in new TestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesTestRoutes
        .post(
          "/preferences/optin",
          readFromResource("optInGenericPayload.json", email),
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .status must be(CREATED)

      private val item = repo
        .findBy(entityId)
        .futureValue

      item.isDefined must be(true)
      val preferenceResponse = item.map(PreferenceResponse.from(_, 100)).get

      preferenceResponse.termsAndConditions must matchPreferenceResponse(
        Predef.Map("generic" -> true),
        shouldBeUpdatedAfterThisTime
      )
      preferenceResponse.email must matchPattern {
        case Some(EmailPreference(_, false, false, false, Some(_), None, _, _, _, _)) =>
      }
    }

    "return a new entity when none exists so that the optin can continue" in new TestCase {
      val response = preferencesTestRoutes
        .post(
          "/preferences/optin",
          readFromResource("optInGenericPayload.json", email),
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
      response.status must be(CREATED)
    }

    "return a new entity when none exists so that the optout can continue" in new TestCase {
      val response = preferencesTestRoutes
        .post(
          "/preferences/optout",
          readFromResource("optOutGenericPayload.json", email),
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
      response.status must be(CREATED)
    }

    "fail with unauthorised when auth token is missing" in new TestCase {
      val response = preferencesTestRoutes
        .post(
          "/preferences/optin",
          readFromResource("optInGenericPayload.json", email)
        )
      response.status must be(UNAUTHORIZED)
      response.responseString must be("Bearer token not supplied")
    }
  }

  "opt in for generic (G V) with language option" should {
    "save the generic terms and conditions only for the given entityId if no preference exists (GX EX -> GV EV)" in new TestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesTestRoutes
        .post(
          `/preferences/:entityId/optin`(entityId),
          readFromResource("optInGenericPayloadWelsh.json", email),
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .status must be(CREATED)

      private val preferenceResponse =
        preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json.as[PreferenceResponse]

      preferenceResponse.termsAndConditions must matchPreferenceResponse(
        Predef.Map("generic" -> true),
        shouldBeUpdatedAfterThisTime
      )
      preferenceResponse.email must matchPattern {
        case Some(EmailPreference(_, false, false, false, Some(_), None, _, _, Some(Language.Welsh), _)) =>
      }
    }
  }

  "opt in and change email address" should {
    "successfully be changed whilst opting-in" in new TestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesTestRoutes
        .post(
          `/preferences/:entityId/optin`(entityId),
          readFromResource("optInGenericPayload.json", email),
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .status must be(CREATED)

      preferencesTestRoutes
        .post(
          `/preferences/:entityId/optin`(entityId),
          readFromResource("optInGenericChangeEmailPayload.json", email),
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .status must be(OK)

      private val preferenceResponse =
        preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json.as[PreferenceResponse]

      preferenceResponse.termsAndConditions must matchPreferenceResponse(
        Predef.Map("generic" -> true),
        shouldBeUpdatedAfterThisTime
      )
      preferenceResponse.email must matchPattern {
        case Some(EmailPreference(_, false, false, false, Some(_), None, _, _, _, Some("test+1234567@test.com"))) =>
      }
    }
  }

  "/preferences/:entityid/optin" should {
    "return BadRequest if language is missing for opt in with IPage" in new TcTestCase {
      val response = testRequest(accepted = true, IPage)
      response.status must be(BAD_REQUEST)
      response.responseString must include("missing language in OptIn request")
    }

    "return BadRequest if language is missing for opt in with ReOptInPage" in new TcTestCase {
      val response = testRequest(accepted = true, ReOptInPage)
      response.status must be(BAD_REQUEST)
      response.responseString must include("missing language in ReOptIn request")
    }

    "return BadRequest if language is missing for opt out with IPage" in new TcTestCase {
      val response = testRequest(accepted = false, IPage)
      response.status must be(BAD_REQUEST)
      response.responseString must include("missing language in CustomerOptOut request")
    }

    "return BadRequest if language is missing for opt out with CYSConfirmPage" in new TcTestCase {
      val response = testRequest(accepted = false, CYSConfirmPage)
      response.status must be(BAD_REQUEST)
      response.responseString must include("missing language in CustomerOptOut request")
    }

    "return BadRequest if language is missing for re-opt in with ReOptInPage" in new TcTestCase {
      val response = testRequest(accepted = true, ReOptInPage)
      response.status must be(BAD_REQUEST)
      response.responseString must include("missing language in ReOptIn request")
    }

    "return BadRequest if language is missing for opt in with AndroidOptInPage" in new TcTestCase {
      val response = testRequest(accepted = true, AndroidOptInPage)
      response.status must be(BAD_REQUEST)
      response.responseString must include("missing language in OptIn request")
    }

    "return BadRequest if language is missing for opt out with AndroidOptOutPage" in new TcTestCase {
      val response = testRequest(accepted = false, AndroidOptOutPage)
      response.status must be(BAD_REQUEST)
      response.responseString must include("missing language in CustomerOptOut request")
    }

    "return BadRequest if language is missing for re opt in with AndroidReOptInPage" in new TcTestCase {
      val response = testRequest(accepted = true, AndroidReOptInPage)
      response.status must be(BAD_REQUEST)
      response.responseString must include("missing language in ReOptIn request")
    }

    "return BadRequest if language is missing for re opt out with AndroidReOptOutPage" in new TcTestCase {
      val response = testRequest(accepted = false, AndroidReOptOutPage)
      response.status must be(BAD_REQUEST)
      response.responseString must include("missing language in CustomerReOptOut request")
    }

    "return BadRequest if language is missing for opt in with IosOptInPage" in new TcTestCase {
      val response = testRequest(accepted = true, IosOptInPage)
      response.status must be(BAD_REQUEST)
      response.responseString must include("missing language in OptIn request")
    }

    "return BadRequest if language is missing for opt out with IosOptOutPage" in new TcTestCase {
      val response = testRequest(accepted = false, IosOptOutPage)
      response.status must be(BAD_REQUEST)
      response.responseString must include("missing language in CustomerOptOut request")
    }

    "return BadRequest if language is missing for re opt in with IosReOptInPage" in new TcTestCase {
      val response = testRequest(accepted = true, IosReOptInPage)
      response.status must be(BAD_REQUEST)
      response.responseString must include("missing language in ReOptIn request")
    }

    "return BadRequest if language is missing for re opt out with IosReOptOutPage" in new TcTestCase {
      val response = testRequest(accepted = false, IosReOptOutPage)
      response.status must be(BAD_REQUEST)
      response.responseString must include("missing language in CustomerReOptOut request")
    }

    "/preferences/regime/ optin, optout, email for itsa" should {
      import play.api.test.Helpers.writeableOf_AnyContentAsJson

      "optin" in new TestCase {
        val request = FakeRequest(POST, s"/preferences/regime/optin")
          .withJsonBody(readFromResource("optInGenericPayload.json", email))
          .withHeaders(authHelper.authHeader(nino, ggAuthPort))
        val result: Result = route(app, request).get.futureValue
        result.header.status must be(CREATED)
      }

      "optout" in new TestCase {
        val request = FakeRequest(POST, s"/preferences/regime/optout")
          .withJsonBody(readFromResource("optOutGenericPayload.json", email))
          .withHeaders(authHelper.authHeader(nino, ggAuthPort))
        val result: Result = route(app, request).get.futureValue
        result.header.status must be(CREATED)
      }

      "set email-language" in new TestCase {
        withEntity(entityId.toString, Option(nino.toString()), Option(itsa.value))

        val preferences = Preferences(
          entityId = entityId,
          termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow())),
          pendingEmail = Some(PendingEmailAddress(email = "a@a.co"))
        )

        preferencesRepository.createOrUpdateTermsAndConditions(preferences, None).futureValue

        val request = FakeRequest(POST, s"/preferences/regime/email-language")
          .withJsonBody(readFromResource("changeLanguageRequestPayload.json", ""))
          .withHeaders(authHelper.authHeader(nino, ggAuthPort))
        val result: Result = route(app, request).get.futureValue
        result.header.status must be(OK)
      }
    }

    trait TcTestCase extends TestCase {
      def testRequest(accepted: Boolean, pageType: PageType) = {
        val optInPageJson = Json.toJson(OptInPage(Version(2, 1), 1, pageType))

        val payload =
          Json.parse(
            s"""{"generic":{"accepted":$accepted, "optInPage":$optInPageJson}, "email": "foo@bar.com"}""".stripMargin
          )
        val entityId = GenerateRandom.entityId()
        preferencesTestRoutes
          .post(
            `/preferences/:entityId/optin`(entityId),
            payload,
            Some(authHelper.authHeader(nino, ggAuthPort))
          )
      }
    }
  }
}
