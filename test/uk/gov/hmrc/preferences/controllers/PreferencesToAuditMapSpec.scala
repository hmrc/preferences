/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.ConfidenceLevel.L200
import uk.gov.hmrc.preferences.model.OptEventType.OptIn
import uk.gov.hmrc.preferences.model.PageType.IPage
import uk.gov.hmrc.preferences.model.TermsAndConditions.{ Accepted, Unknown }
import uk.gov.hmrc.preferences.model.{ EntityId, OptInPage, Preferences, TermsAndConditions, UserType, Version }
import uk.gov.hmrc.preferences.util.Dc
import utils.GenerateRandom

import java.time.Instant

class PreferencesToAuditMapSpec extends PlaySpec {
  val entId: EntityId = GenerateRandom.entityId()

  lazy val now: Instant = Dc.instantNow()

  val pendingEmailAddress = "bob@example.com"
  val pref = Preferences(
    entId,
    TermsAndConditions(Accepted(now)),
    createdAt = now,
    updatedAt = now
  )
  val unknowTandC = TermsAndConditions(Unknown)
  val fullTandC = TermsAndConditions(Accepted(now, Some(OptIn), Some(OptInPage(Version(1, 2), 9, IPage))))

  "Preferences to Audit Map" should {
    "have Unknown for generic Terms and conditions" in {
      val m = prefsToAuditDetails(pref.copy(termsAndConditions = unknowTandC))
      m("genericTermsAndConditions") mustBe "Unknown"
    }
    "have all terms qualified by acceptance type" in {
      val m = prefsToAuditDetails(pref.copy(termsAndConditions = fullTandC))
      m("genericTermsAndConditions") mustBe "accepted"
      m("genericTermsAndConditionsacceptedAt") mustBe now.toString
      m("genericTermsAndConditionsOptEventType") mustBe "OptIn"
      m("genericTermsAndConditionsVersion") mustBe "Version(1,2)"
      m("genericTermsAndConditionsCohort") mustBe "9"
      m("genericTermsAndConditionsPageType") mustBe "IPage"
    }
    "have affinity group and confidence level for user type" in {
      val m = prefsToAuditDetails(pref.copy(userType = Some(UserType(Some(Individual), Some(L200)))))
      m("affinityGroup") mustBe "Individual"
      m("confidenceLevel") mustBe "200"
    }
  }
}
