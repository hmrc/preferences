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

package uk.gov.hmrc.preferences.controllers

import conf.PreferencesTestRoutes._
import conf.{ CleanMongoCollection, ISpec }
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.json.Json._
import uk.gov.hmrc.paperless.controllers.model.PreferenceResponse
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import utils.GenerateRandom

class SaIndividualPreferencesControllerISpec extends ISpec with EntityResolverSupport {

  "Get preferences for Account Details" should {
    "return the email when a opted-in user comes from verify" in new ISpecTestCase {
      private val emailAddress = GenerateRandom.email()

      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

      preferencesBuilder
        .withEntityId(entityId)
        .thenAcceptGenericTermsAndConditions(emailAddress, CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))
        .thenVerifyEmail()
        .entityId

      private val preferences =
        preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json.as[PreferenceResponse]

      preferences.digital mustBe true
      preferences.termsAndConditions("generic").accepted mustBe true
      preferences.email.get.email mustBe emailAddress
    }
  }

  "Update preference" should {
    "return 400 with invalid payload" in new ISpecTestCase {
      private val invalidPreferences = Json.obj()
      preferencesTestRoutes
        .put(`/preferences/:entityId/pending-email`(GenerateRandom.entityId()), invalidPreferences)
        .status must be(BAD_REQUEST)
    }
  }

  "get enrolment status" should {

    "return ok and preference for enrolled and pending verification" in new ISpecTestCase {
      private val email = GenerateRandom.email()

      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

      preferencesBuilder
        .withEntityId(entityId)
        .thenAcceptGenericTermsAndConditions(email, CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))
        .entityId

      private val response = preferencesTestRoutes.get(`/preferences/:entityId`(entityId))
      response.status mustBe OK

      private val preference = response.json.as[PreferenceResponse]
      preference.digital mustBe true
      preference.termsAndConditions("generic").accepted mustBe true
      private val emailPreference = preference.email.get
      emailPreference.email mustBe email
      emailPreference.isVerified mustBe false
      emailPreference.hasBounces mustBe false
    }

    "return not found when the utr has no preferences" in new ISpecTestCase {
      preferencesTestRoutes.get(`/preferences/:entityId`(GenerateRandom.entityId())).status mustBe NOT_FOUND
    }
  }

  "update preference" should {
    "overwrite the existing verified email with the new one which is pending verification" in new ISpecTestCase {
      private val (oldEmail, newEmail) = (GenerateRandom.email(), GenerateRandom.email())

      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

      private val prefBuilder =
        preferencesBuilder
          .withEntityId(entityId)
          .thenAcceptGenericTermsAndConditions(oldEmail, CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))
          .thenVerifyEmail()

      private val preference =
        preferencesTestRoutes.get(`/preferences/:entityId`(prefBuilder.entityId)).json.as[PreferenceResponse]
      private val emailPreference = preference.email.get
      emailPreference.email mustBe oldEmail
      emailPreference.isVerified mustBe true

      prefBuilder.thenChangeEmailAddress(newEmail)
      private val newEmailPreference =
        preferencesTestRoutes.get(`/preferences/:entityId`(prefBuilder.entityId)).json.as[PreferenceResponse].email.get
      newEmailPreference.email mustBe newEmail
      newEmailPreference.isVerified mustBe false
    }

    "return 400 with invalid payload" in new ISpecTestCase {
      private val invalidPreferences = toJson(Map[String, String]())
      preferencesTestRoutes
        .put(`/preferences/:entityId/pending-email`(GenerateRandom.entityId()), invalidPreferences)
        .status must be(BAD_REQUEST)
    }

    "return ok when opting out for a new user" in new ISpecTestCase {
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .withEntityId(entityId)
        .thenDeclineGenericTermsAndConditions(
          shouldReturnStatus = CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .entityId

      private val response = preferencesTestRoutes.get(`/preferences/:entityId`(entityId))
      response.status mustBe 200
      private val preference = response.json.as[PreferenceResponse]
      preference.digital mustBe false
      preference.termsAndConditions("generic").accepted mustBe false
    }
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]
}
