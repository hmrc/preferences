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

package uk.gov.hmrc.preferences.service
import javax.inject.Inject
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.controllers.model.TermsAndConditionsRequest.ManualOptOut
import uk.gov.hmrc.preferences.controllers.model.{ Credentials, TermsAndConditionsRequest }
import uk.gov.hmrc.preferences.model._
import uk.gov.hmrc.preferences.repository.{ NoEmailForPreference, PreferenceUpdateResult }

import scala.concurrent.Future

class TermsAndConditionsService @Inject() (
  optInService: OptInService,
  optOutService: OptOutService
) {

  def handleTermsAndConditionsRequest(
    entityId: EntityId,
    termsAndConditionsRequest: TermsAndConditionsRequest,
    credentials: Option[Credentials]
  )(implicit hc: HeaderCarrier): Future[PreferenceUpdateResult] =
    termsAndConditionsRequest match {
      case t @ TermsAndConditionsRequest(Some(generic), email, _, _, lang, _) =>
        storeTermsAs(entityId, generic, t, credentials, email, TermsAndConditions.GENERIC, lang)

      case TermsAndConditionsRequest(_, _, _, _, lang, _) => optInService.setLanguage(entityId, lang)
    }

  private def storeTermsAs(
    entityId: EntityId,
    termsAcceptance: TermsAndConditionsRequest.Acceptance,
    termsAndConditionsRequest: TermsAndConditionsRequest,
    credentials: Option[Credentials],
    email: Option[String],
    terms: String,
    lang: Option[Language]
  )(implicit hc: HeaderCarrier): Future[PreferenceUpdateResult] =
    (termsAcceptance, email) match {
      case (TermsAndConditionsRequest.ManualOptOut, _) =>
        optOutService
          .optOutOfDigital(
            entityId,
            Some(ManualOptOut.reason),
            terms,
            credentials,
            OptInBundle(None, TermsAndConditionsRequest.eventType(termsAcceptance)),
            lang,
            None
          )
      case (TermsAndConditionsRequest.UserAcceptance(false, optInPage, surveyType), _) =>
        optOutService
          .optOutOfDigital(
            entityId,
            None,
            terms,
            credentials,
            OptInBundle(optInPage, TermsAndConditionsRequest.eventType(termsAcceptance)),
            lang,
            surveyType
          )
      case (TermsAndConditionsRequest.UserAcceptance(true, optInPage, _), Some(emailAddress)) =>
        optInService
          .optInToDigital(
            entityId,
            emailAddress,
            terms,
            termsAndConditionsRequest,
            credentials,
            OptInBundle(optInPage, TermsAndConditionsRequest.eventType(termsAcceptance))
          )
      case (TermsAndConditionsRequest.UserAcceptance(true, _, _), None) =>
        Future.successful(NoEmailForPreference)
    }
}
