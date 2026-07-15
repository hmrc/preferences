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

package uk.gov.hmrc.preferences

import uk.gov.hmrc.auth.core.{ AffinityGroup, ConfidenceLevel }
import uk.gov.hmrc.preferences.model.TermsAndConditions.{ Acceptance, Accepted, Refused }
import uk.gov.hmrc.preferences.model.{ EmailBounce, EmailVerificationLink, Language, OptEventType, OptInPage, PendingEmailAddress, Preferences, Reminder, TermsAndConditions, UserName, UserType }
import play.api.mvc.Result
import play.api.mvc.Results.{ BadRequest, InternalServerError, NotFound, Unauthorized }
import uk.gov.hmrc.preferences.exceptions.{ EntityBadRequest, EntityNotFound, EntityRequestServerError, EntityResolverResponse, EntityUnauthorised }

import java.time.Instant

package object controllers {
  val failedItsaSignupAuditType = "IncompleteCustomerEnrolmentCapture"

  def httpErrorResultForEntity: PartialFunction[EntityResolverResponse, Result] = {
    case EntityBadRequest(message)     => BadRequest(message)
    case EntityNotFound                => NotFound("Entity not found")
    case EntityUnauthorised(msg)       => Unauthorized(msg)
    case EntityRequestServerError(msg) => InternalServerError(s"Error, $msg")
  }

  def prefsToAuditDetails(prefs: Preferences): Map[String, String] =
    Map("entityId" -> prefs.entityId.toString) ++
      termsAndConditionsToMap(prefs.termsAndConditions) ++
      pendingEmailOptionToMap(prefs.pendingEmail) ++
      Map("createdAt" -> prefs.createdAt.toString, "updatedAt" -> prefs.updatedAt.toString) ++
      userTypeOptionToMap(prefs.userType)

  private val emptyMap = Map.empty[String, String]

  private def termsAndConditionsToMap(t: TermsAndConditions): Map[String, String] =
    acceptanceToMap("generic", t.generic)

  private def acceptanceToMap(accType: String, acc: Acceptance): Map[String, String] =
    acc match {
      case Accepted(a, b, c) => accRefHelperToMap(s"${accType}TermsAndConditions", "accepted", a, b, c)
      case Refused(a, b, c)  => accRefHelperToMap(s"${accType}TermsAndConditions", "refused", a, b, c)
      case _                 => Map(s"${accType}TermsAndConditions" -> "Unknown")
    }

  def accRefHelperToMap(
    name: String,
    accOrRefused: String,
    updatedAt: Instant,
    eventType: Option[OptEventType],
    optInPage: Option[OptInPage]
  ): Map[String, String] =
    Map(name -> accOrRefused, s"$name${accOrRefused}At" -> updatedAt.toString) ++
      optionToMap(eventType, (a: OptEventType) => Map(s"${name}OptEventType" -> a.toString)) ++
      optInPageOptionToMap(name, optInPage)

  def optInPageOptionToMap(name: String, t: Option[OptInPage]): Map[String, String] =
    optionToMap(t, optInPageToMap(name))

  def optInPageToMap(name: String)(t: OptInPage): Map[String, String] =
    Map(
      s"${name}Version"  -> t.version.toString,
      s"${name}Cohort"   -> t.cohort.toString,
      s"${name}PageType" -> t.pageType.toString
    )

  def pendingEmailOptionToMap(t: Option[PendingEmailAddress]): Map[String, String] =
    optionToMap(t, pendingEmailToMap)

  def pendingEmailToMap(t: PendingEmailAddress): Map[String, String] =
    Map("pendingEmail" -> t.email) ++
      emailBounceOptionToMap(t.lastBounce) ++
      emailVerificationLinkOptionToMap(t.verificationLink) ++
      reminderOptionToMap("reminder", t.reminder) ++
      optionToMap(t.language, (a: Language) => Map("language" -> a.toString)) ++
      reminderOptionToMap("secondReminder", t.secondReminder)

  def emailBounceOptionToMap(t: Option[EmailBounce]): Map[String, String] =
    optionToMap(t, emailBounceToMap)

  def emailBounceToMap(t: EmailBounce): Map[String, String] =
    optionToMap(t.errorCode, (a: Int) => Map("lastBounceErrorCode" -> a.toString)) ++
      Map("lastBounceTimestamp" -> t.timestamp.toString)

  def emailVerificationLinkOptionToMap(t: Option[EmailVerificationLink]): Map[String, String] =
    optionToMap(t, emailVerificationLinkToMap)

  def emailVerificationLinkToMap(t: EmailVerificationLink): Map[String, String] =
    Map("emailVerificationLinkId" -> t._id, "emailVerificationLinkSentTime" -> t.linkSentTime.toString) ++
      optionToMap(t.returnText, (a: String) => Map("emailVerificationLinkReturnText" -> a)) ++
      optionToMap(t.returnUrl, (a: String) => Map("emailVerificationLinkReturnUrl" -> a))

  def reminderOptionToMap(s: String, t: Option[Reminder]): Map[String, String] =
    optionToMap(t, reminderToMap(s))

  def reminderToMap(s: String)(t: Reminder): Map[String, String] =
    Map(
      s"emailVerificationLink${s}status"    -> t.status.name,
      s"emailVerificationLink${s}updatedAt" -> t.updatedAt.toString
    )

  def userTypeOptionToMap(t: Option[UserType]): Map[String, String] =
    optionToMap(t, userTypeToMap)

  def userTypeToMap(t: UserType): Map[String, String] =
    optionToMap(t.affinityGroup, (a: AffinityGroup) => Map("affinityGroup" -> a.toString)) ++
      optionToMap(t.confidenceLevel, (a: ConfidenceLevel) => Map("confidenceLevel" -> a.toString))

  def optionToMap[T](v: Option[T], f: T => Map[String, String]): Map[String, String] =
    v match {
      case None    => emptyMap
      case Some(a) => f(a)
    }
}
