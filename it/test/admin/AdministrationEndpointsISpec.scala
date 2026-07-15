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

package admin

import conf.PreferencesTestRoutes.*
import conf.{ CleanMongoCollection, ISpec }
import org.scalatest.LoneElement
import play.api.http.Status.*
import play.api.libs.json.{ JsNull, Json }
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import utils.GenerateRandom

class AdministrationEndpointsISpec extends ISpec with LoneElement with EntityResolverSupport {

  "Delete preferences by entityId" should {

    "return OK if there is no record to delete" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      preferencesTestRoutes.delete(`/preferences-admin/:entityId`(entityId)).status must be(OK)
    }

    "return OK if preference record is successfully deleted" in new ISpecTestCase {
      private val entityIdToRemove = GenerateRandom.entityId()
      private val entityIdToKeep = GenerateRandom.entityId()

      withEntity(entityIdToRemove.toString, Option(nino.toString()), Option(utr.value))
      withEntity(entityIdToKeep.toString, Option(nino.toString()), Option(utr.value))

      preferencesBuilder.acceptGenericTermsPendingVerification(
        entityIdToRemove,
        headers = Some(authHelper.authHeader(nino, ggAuthPort))
      )
      preferencesBuilder
        .acceptGenericTermsPendingVerification(entityIdToKeep, headers = Some(authHelper.authHeader(nino, ggAuthPort)))

      preferencesTestRoutes.delete(`/preferences-admin/:entityId`(entityIdToRemove)).status must be(OK)

      preferencesTestRoutes.get(`/preferences/:entityId`(entityIdToRemove)).status must be(NOT_FOUND)
      preferencesTestRoutes.get(`/preferences/:entityId`(entityIdToKeep)).status must be(OK)
    }
  }

  "Get verification-token by entityId" should {
    "retrieve the verification token if it exists in preferences" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

      preferencesBuilder
        .acceptGenericTermsPendingVerification(entityId, headers = Some(authHelper.authHeader(nino, ggAuthPort)))

      private val response =
        preferencesTestRoutes.get(`/preferences-admin/:entityId/verification-token`(entityId))
      response.status must be(OK)
      response.json.toString() must not be empty
    }

    "return NotFound if the email was already verified" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .acceptGenericTermsAndVerifyEmail(entityId, headers = Some(authHelper.authHeader(nino, ggAuthPort)))

      private val response =
        preferencesTestRoutes.get(`/preferences-admin/:entityId/verification-token`(entityId))
      response.status must be(NOT_FOUND)
      response.responseString mustBe "No verification link found"
    }

    "return NotFound if no preference is found for the given entityId" in new ISpecTestCase {
      preferencesTestRoutes
        .get(`/preferences-admin/:entityId/verification-token`(GenerateRandom.entityId()))
        .status must be(NOT_FOUND)
    }
  }

  "Post to set bounce flag in preference by email" should {
    "return NoContent if successfully sets the bounce flag for preferences with the matching email " in new ISpecTestCase {
      private val email = GenerateRandom.email()
      private val payload = Json.parse(s"""{"emailAddress": "$email"}""")
      preferencesTestRoutes.post(`/preferences-admin/bounce-email`, payload).status must be(NO_CONTENT)
    }
  }

  "Post to verify an email in the preference by entityId" should {
    "return NoContent if it is successfully marked as verified" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .acceptGenericTermsPendingVerification(entityId, headers = Some(authHelper.authHeader(nino, ggAuthPort)))

      preferencesTestRoutes.post(`/preferences-admin/:entityId/verify-email`(entityId), JsNull).status must be(
        NO_CONTENT
      )
    }

    "return NotFound if preference does not exist" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesTestRoutes.post(`/preferences-admin/:entityId/verify-email`(entityId), JsNull).status must be(
        NOT_FOUND
      )
    }

    "return NotFound if preference exist with verified email" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

      preferencesBuilder
        .acceptGenericTermsAndVerifyEmail(entityId, headers = Some(authHelper.authHeader(nino, ggAuthPort)))

      preferencesTestRoutes.post(`/preferences-admin/:entityId/verify-email`(entityId), JsNull).status must be(
        NOT_FOUND
      )
    }
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]
}
