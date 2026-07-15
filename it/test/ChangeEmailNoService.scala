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

import conf.PreferencesTestRoutes._
import conf._
import play.api.http.Status._
import uk.gov.hmrc.preferences.model.EntityId
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import utils.GenerateRandom

class ChangeEmailNoService extends ISpec with EntityResolverSupport {

  "changing email address when email service is unavailable" should {
    "save the pending email regardless" in new ISpecTestCase {
      val entityId: EntityId = GenerateRandom.entityId()
      val emailAddress: String = GenerateRandom.email()
      val newEmailAddress: String = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

      preferencesBuilder
        .withEntityId(entityId)
        .thenAcceptGenericTermsAndConditions(emailAddress, CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))
        .thenChangeEmailAddress(newEmailAddress)

      (preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json \ "email" \ "email")
        .as[String] mustBe newEmailAddress
    }
  }

  override val cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]

}
