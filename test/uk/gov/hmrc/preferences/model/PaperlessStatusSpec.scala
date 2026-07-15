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

package uk.gov.hmrc.preferences.model

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.auth.core.{ AffinityGroup, ConfidenceLevel }
import uk.gov.hmrc.paperless.controllers.model.Category.{ ActionRequired, Info, OptInRequired, ReOptInRequired }
import uk.gov.hmrc.paperless.controllers.model.StatusName.{ Alright, EmailNotVerified, NoEmail, OldVersion, Paper, ReOptInModified }
import uk.gov.hmrc.paperless.controllers.model.{ AcceptanceResponse, PaperlessStatus, PreferenceResponse }
import uk.gov.hmrc.preferences.controllers.model.Credentials
import uk.gov.hmrc.paperless.controllers.model.{ EmailPreference => CMEmailPreference }
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions
import uk.gov.hmrc.preferences.util.Dc

import java.time.Instant

class PaperlessStatusSpec extends PlaySpec with SampleMongoPreferencesJson {

  "PaperlessStatus" should {
    "calculate PaperlessStatus(ReOptInModified, ReOptInRequired) when preference is on old T&Cs," +
      " and there is no pending email" in new TestCase {
        val response = preferenceResponse(hasBounce = true, versionBehind = true, genericTermsAccepted = true)
        val credentials: Option[Credentials] = None

        val status = PaperlessStatus(response, credentials, reoptinMajor, gracePeriod)

        status.name mustBe ReOptInModified
        status.category mustBe ReOptInRequired
      }

    "calculate PaperlessStatus(OldVersion, ReOptInRequired) when the version is behind," +
      "the email is not verified" in new TestCase {

        val response = preferenceResponse(isVerified = false, genericTermsAccepted = true, versionBehind = true)
        val credentials: Option[Credentials] = None

        val status = PaperlessStatus(response, credentials, reoptinMajor, gracePeriod)

        status.name mustBe EmailNotVerified
        status.category mustBe ActionRequired
      }

    "calculate PaperlessStatus(EmailNotVerified, ActionRequired) when the email is not verified" in new TestCase {

      val response = preferenceResponse(isVerified = false, genericTermsAccepted = true, versionBehind = true)
      val credentials: Option[Credentials] = None

      val status = PaperlessStatus(response, credentials, reoptinMajor, gracePeriod)

      status.name mustBe EmailNotVerified
      status.category mustBe ActionRequired
    }

    "calculate PaperlessStatus(EmailNotVerified, ActionRequired) when the email is not verified," +
      " and on the latest on the latest T&Cs" in new TestCase {

        val response = preferenceResponse(isVerified = false, genericTermsAccepted = true, versionBehind = false)
        val credentials: Option[Credentials] = None

        val status = PaperlessStatus(response, credentials, reoptinMajor, gracePeriod)

        status.name mustBe EmailNotVerified
        status.category mustBe ActionRequired
      }

    "calculate PaperlessStatus(NoEmail, ActionRequired) when the there is noEmail" in new TestCase {

      val response = preferenceResponse(noEmail = true)
      val credentials: Option[Credentials] = None

      val status = PaperlessStatus(response, credentials, reoptinMajor, gracePeriod)

      status.name mustBe NoEmail
      status.category mustBe ActionRequired
    }

    "calculate PaperlessStatus(OldVersion, ReOptInRequired) when " +
      "the there is no pending email, the version is behind and it is paperless and has some credentials" in new TestCase {

        val response = preferenceResponse(versionBehind = true, isPaperless = true, noPendingEmail = true)
        val name = Name(Some("Some"), Some("User"))
        val credentials =
          Some(Credentials(affinityGroup = Some(AffinityGroup.Individual), ConfidenceLevel.L200))

        val status = PaperlessStatus(response, credentials, reoptinMajor, gracePeriod)

        status.name mustBe OldVersion
        status.category mustBe ReOptInRequired
      }

    "calculate PaperlessStatus(Alright, Info) when " +
      "there is a pending email, the version is behind and it is paperless and has some credentials" in new TestCase {

        val response = preferenceResponse(versionBehind = true, isPaperless = true, noPendingEmail = false)
        val name = Name(Some("Some"), Some("User"))
        val credentials =
          Some(Credentials(affinityGroup = Some(AffinityGroup.Individual), ConfidenceLevel.L200))

        val status = PaperlessStatus(response, credentials, reoptinMajor, gracePeriod)

        status.name mustBe Alright
        status.category mustBe Info
      }

    "calculate PaperlessStatus(Alright, Info) when " +
      "the there is no pending email, the version is behind and it is paperless and has no credentials" in new TestCase {

        val response = preferenceResponse(versionBehind = true, isPaperless = true, noPendingEmail = true)
        val credentials = None

        val status = PaperlessStatus(response, credentials, reoptinMajor, gracePeriod)

        status.name mustBe Alright
        status.category mustBe Info
      }

    "calculate PaperlessStatus(Alright, Info) when " +
      "the there is no pending email, the version is Not behind and it is paperless and has some credentials" in new TestCase {

        val response = preferenceResponse(versionBehind = false, isPaperless = true, noPendingEmail = true)
        val name = Name(Some("Some"), Some("User"))
        val credentials =
          Some(Credentials(affinityGroup = Some(AffinityGroup.Individual), ConfidenceLevel.L200))

        val status = PaperlessStatus(response, credentials, reoptinMajor, gracePeriod)

        status.name mustBe Alright
        status.category mustBe Info
      }

    "calculate PaperlessStatus(Alright, Info) when " +
      "the there is no pending email, the version is behind and it is NOT paperless and has some credentials" in new TestCase {

        val response = preferenceResponse(versionBehind = true, isPaperless = false, noPendingEmail = true)
        val name = Name(Some("Some"), Some("User"))
        val credentials =
          Some(Credentials(affinityGroup = Some(AffinityGroup.Individual), ConfidenceLevel.L200))

        val status = PaperlessStatus(response, credentials, reoptinMajor, gracePeriod)

        status.name mustBe Alright
        status.category mustBe Info
      }

    "calculate PaperlessStatus Paper, Info when generatedAccepted is false, and updated is after the grace period" in new TestCase {
      val response =
        preferenceResponse(genericTermsAccepted = false, updatedAt = Some(Dc.instantNow().plusMinutes(5000)))
      val credentials: Option[Credentials] = None

      val status = PaperlessStatus(response, credentials, reoptinMajor, gracePeriod)

      status.name mustBe Paper
      status.category mustBe Info
    }

    "calculate PaperlessStatus(Paper, Info) when generatedAccepted is false, and updated is before the grace period" in new TestCase {
      val updatedAtBeforeGracePeriod: Option[Instant] =
        Some(Dc.instantNow().plusMinutes(1000))
      val response = preferenceResponse(genericTermsAccepted = false, updatedAt = updatedAtBeforeGracePeriod)
      val credentials: Option[Credentials] = None

      val status = PaperlessStatus(response, credentials, reoptinMajor, gracePeriod)

      status.name mustBe Paper
      status.category mustBe Info
    }

    "calculate PaperlessStatus Paper, Info when generatedAccepted is false, and updated is not set" in new TestCase {
      val response = preferenceResponse(genericTermsAccepted = false, updatedAt = None)
      val credentials: Option[Credentials] = None

      val status = PaperlessStatus(response, credentials, reoptinMajor, gracePeriod)

      status.name mustBe Paper
      status.category mustBe OptInRequired
    }

    trait TestCase {

      val reoptinMajor = 1
      val gracePeriod = 2880

      def preferenceResponse(
        isVerified: Boolean = true,
        hasBounce: Boolean = false,
        versionBehind: Boolean = false,
        genericTermsAccepted: Boolean = true,
        noEmail: Boolean = false,
        noPendingEmail: Boolean = true,
        isPaperless: Boolean = false,
        updatedAt: Option[Instant] = None
      ): PreferenceResponse = {

        val majorVersion = if (versionBehind) Some(0) else Some(1)
        val paperless = if (isPaperless) Some(true) else None
        val termsAndConditions =
          Map(
            "generic" ->
              AcceptanceResponse(
                accepted = genericTermsAccepted,
                updatedAt = updatedAt,
                majorVersion = majorVersion,
                paperless = paperless,
                eventType = None
              )
          )

        val email =
          if (noEmail) None
          else {
            val pendingEmail =
              if (noPendingEmail) None
              else Some("pendingEmail")
            Some(
              CMEmailPreference(
                email = "email@test.com",
                isVerified = isVerified,
                hasBounces = hasBounce,
                mailboxFull = true,
                linkSent = None,
                verifiedOn = None,
                status = "status",
                pendingEmail = pendingEmail
              )
            )
          }

        PreferenceResponse(
          termsAndConditions,
          email
        )
      }
    }
  }

}
