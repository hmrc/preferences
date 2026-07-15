/*
 * Copyright 2020 HM Revenue & Customs
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

package emailverification

import conf.{ CleanMongoCollection, ISpec }
import play.api.libs.json.Json
import play.api.libs.json.Json.*
import uk.gov.hmrc.paperless.controllers.model.PreferenceResponse
import utils.GenerateRandom
import conf.PreferencesTestRoutes.*
import play.api.http.HeaderNames
import play.api.http.Status.*
import uk.gov.hmrc.preferences.controllers.ApiVersion
import uk.gov.hmrc.preferences.util.Dc
import uk.gov.hmrc.preferences.model.Language
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.time.Instant

class EmailVerificationISpec extends ISpec with EntityResolverSupport {

  val mustBeUpdatedAfterThisTime: Instant = Dc.instantNow()

  "verifying an email address" should {

    "fail when an invalid token is passed in" in new TestSetup {
      preferencesTestRoutes.put(`/preferences/email`, Json.obj("token" -> "foo")).status mustBe BAD_REQUEST
    }

    "fail when an unknown id is passed in" in new TestSetup {
      preferencesTestRoutes
        .put(`/preferences/email`, Json.obj("token" -> "f955e3aa-1b90-4d02-8f77-d725de0d9b9z"))
        .status mustBe BAD_REQUEST
    }

    "successfully send and process verification link if the verification link is clicked before expiration" in new TestSetup {
      eventually {
        val verificationLink = testEmailService.findEmailsFor(emailAddress).verificationLinks().head
        preferencesTestRoutes.put(`/preferences/email`, Json.obj("token" -> verificationLink)).status mustBe 204
      }

      val printPreferences: PreferenceResponse =
        preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json.as[PreferenceResponse]
      printPreferences.email.get.isVerified mustBe true
      printPreferences.email.get.verifiedOn.get.isAfter(mustBeUpdatedAfterThisTime) mustBe true
    }

    "try to verify twice" in new TestSetup {
      val acceptHeader = Some(HeaderNames.ACCEPT -> ApiVersion.v2.header)
      eventually {
        val verificationLink = testEmailService.findEmailsFor(emailAddress).verificationLinks().head
        preferencesTestRoutes.put(`/preferences/email`, Json.obj("token" -> verificationLink)).status mustBe 204
        preferencesTestRoutes
          .put(`/preferences/email`, Json.obj("token" -> verificationLink), acceptHeader)
          .status mustBe 200
      }

      val printPreferences: PreferenceResponse =
        preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json.as[PreferenceResponse]
      printPreferences.email.get.isVerified mustBe true
      printPreferences.email.get.verifiedOn.get.isAfter(mustBeUpdatedAfterThisTime) mustBe true
    }

    "successfully send and process verification link if the verification link is clicked before expiration (with language)" in new TestSetup {
      eventually {
        val verificationLink = testEmailService.findEmailsFor(emailAddress).verificationLinks().head
        preferencesTestRoutes.put(`/preferences/email`, Json.obj("token" -> verificationLink)).status mustBe 204
      }
      val printPreferences: PreferenceResponse =
        preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json.as[PreferenceResponse]
      printPreferences.email.get.isVerified mustBe true
      printPreferences.email.get.verifiedOn.get.isAfter(mustBeUpdatedAfterThisTime) mustBe true
      printPreferences.email.get.language mustBe Some(Language.Welsh)
    }

    "return BAD_REQUEST when request body is empty" in new ISpecTestCase {
      preferencesTestRoutes
        .put(`/preferences/email`, Json.obj())
        .status mustBe BAD_REQUEST
    }

    "return BAD_REQUEST when request body is invalid" in new ISpecTestCase {
      val invalidPayloads = Seq(
        Json.obj("some_field" -> "some_value")
      )
      invalidPayloads.foreach { payload =>
        preferencesTestRoutes
          .put(`/preferences/email`, payload)
          .status mustBe BAD_REQUEST
      }
    }

    "return success when user clicks on verification link twice" in new TestSetup {
      eventually {
        val verificationLink = testEmailService.findEmailsFor(emailAddress).verificationLinks().head
        val futures = (1 to 2).map { _ =>
          Future {
            preferencesTestRoutes.put(`/preferences/email`, Json.obj("token" -> verificationLink)).status
          }
        }
        val results = Future.sequence(futures).futureValue
        results must contain(200)
        results.forall(status => Seq(200).contains(status)) mustBe true
      }
    }

    "return 409 error for verification token doesn't exist" in new ISpecTestCase {
      val nonExistentToken = "a1b2c3d4-e5f6-4789-9abc-def012345678"
      val response = preferencesTestRoutes.put(`/preferences/email`, Json.obj("token" -> nonExistentToken))
      response.status mustBe CONFLICT
    }
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]

  class TestSetup extends ISpecTestCase {
    val entityId = GenerateRandom.entityId()
    val emailAddress = GenerateRandom.email()
    withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
    val testCase = new ISpecTestCase {}
    testCase.preferencesBuilder.acceptGenericTermsPendingVerification(
      entityId,
      emailAddress,
      Some(authHelper.authHeader(nino, ggAuthPort))
    )
    (entityId, emailAddress)

  }
}
