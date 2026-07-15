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

package client

import java.util.UUID
import conf.PreferencesTestRoutes._
import conf.{ CleanMongoCollection, ISpec }
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import utils.GenerateRandom

class PreferencesFrontEndClientVerifyEmailISpec extends ISpec with EntityResolverSupport {

  "verify email" should {
    def generateToken(): String = UUID.randomUUID().toString

    "successfully send and process verification link if the verification link is clicked before expiration" in new ISpecTestCase {
      private val emailAddress = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .withEntityId(entityId)
        .thenAcceptGenericTermsAndConditions(emailAddress, CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))

      eventually {
        val verificationLink = testEmailService.findEmailsFor(emailAddress).verificationLinks().head
        preferencesTestRoutes
          .put(`/preferences/email`, Json.obj("token" -> verificationLink))
          .status mustBe NO_CONTENT
      }
    }

    "return bad request when an invalid token is passed in" in new ISpecTestCase {
      preferencesTestRoutes.put(`/preferences/email`, Json.obj("token" -> "foo")).status mustBe BAD_REQUEST
    }

    "return bad request when an unknown id is passed in" in new ISpecTestCase {
      preferencesTestRoutes.put(`/preferences/email`, Json.obj("token" -> generateToken())).status mustBe CONFLICT
    }

    "return a 400 when the verification link is used for the second time" in new ISpecTestCase {
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .withEntityId(entityId)
        .thenAcceptGenericTermsAndConditions(
          GenerateRandom.email(),
          CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .thenVerifyEmail(shouldReturnStatus = NO_CONTENT)
        .thenVerifyEmail(shouldReturnStatus = BAD_REQUEST)
    }

    "return a 409 when the verification link is not valid" in new ISpecTestCase {
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .withEntityId(entityId)
        .thenAcceptGenericTermsAndConditions(
          GenerateRandom.email(),
          CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )

      preferencesTestRoutes.put(`/preferences/email`, Json.obj("token" -> generateToken())).status mustBe CONFLICT
    }
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]
}
