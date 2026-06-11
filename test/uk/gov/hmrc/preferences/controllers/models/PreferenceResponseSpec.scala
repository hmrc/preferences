/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers.models

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsError, JsPath, JsString, Json, JsonValidationError, _ }
import uk.gov.hmrc.paperless.controllers.model.Category.{ ActionRequired, Info, OptInRequired, ReOptInRequired, actionRequired, info => infoCategory, optInRequired, reOptInRequired }
import uk.gov.hmrc.paperless.controllers.model.{ Category, StatusName }
import uk.gov.hmrc.paperless.controllers.model.StatusName.{ Alright, BouncedEmail, EmailNotVerified, NewCustomer, NoEmail, OldVersion, Paper, alright, bouncedEmail, emailNotVerified, newCustomer, noEmail, oldVersion, paper }

class PreferenceResponseSpec extends PlaySpec {

  "StatusName deserialisation" must {

    "work with valid Paper value" in {
      JsString("PAPER").asOpt[StatusName].value mustBe Paper
    }
    "work with valid EmailNotVerified value" in {
      JsString("EMAIL_NOT_VERIFIED").asOpt[StatusName].value mustBe EmailNotVerified
    }
    "work with valid BouncedEmail value" in {
      JsString("BOUNCED_EMAIL").asOpt[StatusName].value mustBe BouncedEmail
    }
    "work with valid Alright value" in {
      JsString("ALRIGHT").asOpt[StatusName].value mustBe Alright
    }
    "work with valid NewCustomer value" in {
      JsString("NEW_CUSTOMER").asOpt[StatusName].value mustBe NewCustomer
    }
    "work with valid NoEmail value" in {
      JsString("NO_EMAIL").asOpt[StatusName].value mustBe NoEmail
    }
    "work with valid OldVersion value" in {
      JsString("OLD_VERSION").asOpt[StatusName].value mustBe OldVersion
    }
    "work with invalid value" in {
      implicitly[Reads[StatusName]].reads(JsString("invalid-status-name")) mustBe JsError(
        Seq(JsPath() -> Seq(JsonValidationError("error.expected.validenumvalue")))
      )
    }

  }

  "StatusName Json serialisation" must {

    "serialise Paper to JsString" in {
      Json.toJson(paper) mustBe JsString("PAPER")
    }
    "serialise  EmailNotVerified to JsString" in {
      Json.toJson(emailNotVerified) mustBe JsString("EMAIL_NOT_VERIFIED")
    }
    "serialise BouncedEmail to JsString" in {
      Json.toJson(bouncedEmail) mustBe JsString("BOUNCED_EMAIL")
    }
    "serialise Alright to JsString" in {
      Json.toJson(alright) mustBe JsString("ALRIGHT")
    }
    "serialise NewCustomer to JsString" in {
      Json.toJson(newCustomer) mustBe JsString("NEW_CUSTOMER")
    }
    "serialise NoEmail to JsString" in {
      Json.toJson(noEmail) mustBe JsString("NO_EMAIL")
    }
    "serialise OldVersion to JsString" in {
      Json.toJson(oldVersion) mustBe JsString("OLD_VERSION")
    }
  }

  "Category deserialisation" must {

    "work with valid ActionRequired value" in {
      JsString("ACTION_REQUIRED").asOpt[Category].value mustBe ActionRequired
    }
    "work with valid Info value" in {
      JsString("INFO").asOpt[Category].value mustBe Info
    }
    "work with valid ReOptInRequired value" in {
      JsString("RE_OPT_IN_REQUIRED").asOpt[Category].value mustBe ReOptInRequired
    }
    "work with valid OptInRequired value" in {
      JsString("OPT_IN_REQUIRED").asOpt[Category].value mustBe OptInRequired
    }
    "work with invalid value" in {
      implicitly[Reads[Category]].reads(JsString("invalid-category-name")) mustBe JsError(
        Seq(JsPath() -> Seq(JsonValidationError("error.expected.validenumvalue")))
      )
    }
  }

  "Category Json serialisation" must {
    "serialise ActionRequired to JsString" in {
      Json.toJson(actionRequired) mustBe JsString("ACTION_REQUIRED")
    }
    "serialise Info to JsString" in {
      Json.toJson(infoCategory) mustBe JsString("INFO")
    }
    "serialise ReOptInRequired to JsString" in {
      Json.toJson(reOptInRequired) mustBe JsString("RE_OPT_IN_REQUIRED")
    }
    "serialise OptInRequired to JsString" in {
      Json.toJson(optInRequired) mustBe JsString("OPT_IN_REQUIRED")
    }
  }
}
