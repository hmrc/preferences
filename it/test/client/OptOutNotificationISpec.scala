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

import conf.{ CleanMongoCollection, ISpec }
import play.api.http.Status._
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import utils.GenerateRandom

class OptOutNotificationISpec extends ISpec with EntityResolverSupport {

  "User opting out" should {
    "send an email to the user with opt-out notification" in new ISpecTestCase {

      private val email = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

      preferencesBuilder
        .withEntityId(entityId)
        .thenAcceptGenericTermsAndConditions(email, CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))
        .thenVerifyEmail()
        .thenStopEmailRemindersFromManageAccount(Some(authHelper.authHeader(nino, ggAuthPort)))

      eventually {
        testEmailService.findEmailsFor(email).optOutNotifications() must have(size(1))
      }
    }
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]
}
