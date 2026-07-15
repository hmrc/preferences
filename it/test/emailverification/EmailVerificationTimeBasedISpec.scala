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

package emailverification

import conf.{ CleanMongoCollection, ISpec, Tardis }
import play.api.http.Status._
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import utils.GenerateRandom

class EmailVerificationTimeBasedISpec extends ISpec with Tardis with EntityResolverSupport {

  "verifying an email address" should {
    "fail when the verification link has expired (at least 30 days old)" in new ISpecTestCase {
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      atTime(daysAgo(31)) {
        preferencesBuilder
          .withEntityId(entityId)
          .thenAcceptGenericTermsAndConditions(
            GenerateRandom.email(),
            CREATED,
            Some(authHelper.authHeader(nino, ggAuthPort))
          )
      }

      preferencesBuilder.withEntityId(entityId).thenVerifyEmail(shouldReturnStatus = GONE)
    }

    "succeed when the verification link is within the valid period (less than 30 days old)" in new ISpecTestCase {
      val email = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      atTime(daysAgo(29)) {
        preferencesBuilder
          .withEntityId(entityId)
          .thenAcceptGenericTermsAndConditions(
            email,
            CREATED,
            Some(authHelper.authHeader(nino, ggAuthPort))
          )
      }

      preferencesBuilder.withEntityId(entityId).thenVerifyEmail(shouldReturnStatus = OK)
    }
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]
}
