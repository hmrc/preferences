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

package conf

import com.google.inject.Inject
import conf.PreferencesTestRoutes._

import javax.inject.Singleton
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status.*
import play.api.libs.json.{ JsObject, JsValue, Json }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.preferences.model.Event.*
import uk.gov.hmrc.preferences.model.PageType.IPage
import uk.gov.hmrc.preferences.model.{ EntityId, OptInPage, Version }
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import utils.GenerateRandom

import scala.annotation.unused
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class PreferencesBuilder @Inject() (testRoutes: PreferencesTestRoutes, preferencesRepository: PreferencesRepository)
    extends ScalaFutures with Matchers with MongoSupport {

  type Header = (String, String)

  def withEntityId(entityId: EntityId): Builder =
    Builder(entityId = entityId, maybeEmail = None, maybeVerificationLink = None)

  def withEntityIdAndEmail(entityId: EntityId, email: String): Builder =
    Builder(entityId = entityId, maybeEmail = Some(email), maybeVerificationLink = None)

  def withRandomEntityId(): Builder =
    withEntityId(GenerateRandom.entityId())

  def acceptGenericTermsPendingVerification(
    entityId: EntityId,
    email: String = GenerateRandom.email(),
    headers: Option[Header]
  )(implicit hc: HeaderCarrier): Builder =
    withEntityId(entityId)
      .thenAcceptGenericTermsAndConditions(email, CREATED, headers, optInPage = OptInPage(Version(2, 1), 1, IPage))

  def acceptGenericTermsAndVerifyEmail(
    entityId: EntityId,
    email: String = GenerateRandom.email(),
    headers: Option[Header],
    optInPage: OptInPage = OptInPage(Version(2, 1), 1, IPage)
  )(implicit hc: HeaderCarrier): Builder =
    withEntityId(entityId)
      .thenAcceptGenericTermsAndConditions(email, CREATED, headers, optInPage)
      .thenVerifyEmail()

  case class Builder(entityId: EntityId, maybeEmail: Option[String], maybeVerificationLink: Option[String]) {

    def thenAcceptGenericTermsAndConditions(
      email: String,
      shouldReturnStatus: Int = OK,
      headers: Option[Header],
      optInPage: OptInPage = OptInPage(Version(2, 1), 1, IPage)
    )(implicit hc: HeaderCarrier): Builder = {
      val optInPageJson = Json.toJson(optInPage)
      val payload = Json.parse(
        s"""{"generic":{"accepted":true, "optInPage":$optInPageJson}, "email": "$email", "language": "cy"}""".stripMargin
      )
      postOptin(payload)(shouldReturnStatus, headers)
      Builder(entityId, Some(email), findVerificationLink)
    }

    def thenInvalidAcceptGenericTermsAndConditions(shouldReturnStatus: Int = BAD_REQUEST, headers: Option[Header])(
      implicit hc: HeaderCarrier
    ): Builder = {
      val payload = Json.parse(s"""{"generic":{"accepted":true}}""")
      postOptin(payload)(shouldReturnStatus, headers)
      Builder(entityId, None, findVerificationLink)
    }

    def thenDeclineGenericTermsAndConditions(shouldReturnStatus: Int = OK, headers: Option[Header]): Builder = {
      val payload = Json.parse(
        """{"generic":{"accepted":false,"optInPage":{"version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}},"language": "en"}"""
      )
      postOptin(payload)(shouldReturnStatus, headers)
      Builder(entityId = entityId, maybeEmail = None, maybeVerificationLink = None)
    }

    def thenDeclineGenericTermsAndConditionsWithSurvey(
      shouldReturnStatus: Int = OK,
      headers: Option[Header]
    ): Builder = {
      val payload =
        Json.parse(
          """{"generic":{"accepted":false,"optInPage":{"version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"},"surveyType": "StandardInterruptOptOut" },"language": "en"}"""
        )
      postOptout(payload)(shouldReturnStatus, headers)
      Builder(entityId = entityId, maybeEmail = None, maybeVerificationLink = None)
    }

    def thenVerifyEmail(@unused shouldReturnStatus: Int = NO_CONTENT)(implicit hc: HeaderCarrier): Builder = {
      val link = maybeVerificationLink orElse findVerificationLink getOrElse (throw new IllegalStateException(
        "No verification link available"
      ))
      testRoutes.put(`/preferences/email`, Json.obj("token" -> link))
      Builder(entityId = entityId, maybeEmail = maybeEmail, maybeVerificationLink = maybeVerificationLink)
    }

    def findVerificationLink(implicit hc: HeaderCarrier): Option[String] =
      (preferencesRepository.findBy(entityId) map { maybePreferences =>
        for {
          prefs            <- maybePreferences
          pendingEmail     <- prefs.pendingEmail
          verificationLink <- pendingEmail.verificationLink
        } yield verificationLink._id
      }).futureValue

    def bounceFor(emailAddress: String): JsObject = Json.obj("emailAddress" -> emailAddress)

    def thenBounceEmail(): Builder = {
      testRoutes.post(`/preferences-admin/bounce-email`, bounceFor(maybeEmail.get)).status should be(NO_CONTENT)
      Builder(entityId = entityId, maybeEmail = maybeEmail, maybeVerificationLink = maybeVerificationLink)
    }

    def optInPayload(email: String): JsObject = Json.obj("digital" -> true, "email" -> email)

    def thenRequestNewVerificationLink()(implicit hc: HeaderCarrier): Builder =
      thenChangeEmailAddress(maybeEmail.get)

    def thenChangeEmailAddress(newEmail: String)(implicit hc: HeaderCarrier): Builder = {
      testRoutes.put(`/preferences/:entityId/pending-email`(entityId), optInPayload(email = newEmail)).status should be(
        OK
      )
      Builder(entityId, Some(newEmail), findVerificationLink)
    }

    def thenStopEmailRemindersFromManageAccount(headers: Option[Header]): Builder = {
      val stopRemindersPayload = Json.obj(
        "generic" -> Json.obj(
          "accepted" -> false,
          "optInPage" -> Json
            .obj("version" -> Json.obj("major" -> 2, "minor" -> 1), "cohort" -> 1, "pageType" -> IPage)
        ),
        "language" -> "en"
      )
      testRoutes
        .post(`/preferences/:entityId/optout`(entityId), stopRemindersPayload, headers)
        .status should be(OK)
      Builder(entityId = entityId, maybeEmail = maybeEmail, maybeVerificationLink = maybeVerificationLink)
    }

    private def postOptin(payload: JsValue)(shouldReturnStatus: Int, headers: Option[Header]) =
      testRoutes
        .post(`/preferences/:entityId/optin`(entityId), payload, headers)
        .status shouldBe shouldReturnStatus

    private def postOptout(payload: JsValue)(shouldReturnStatus: Int, headers: Option[Header]) =
      testRoutes
        .post(`/preferences/:entityId/optout`(entityId), payload, headers)
        .status shouldBe shouldReturnStatus
  }

}
