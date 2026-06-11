/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsObject, JsString, Json }
import uk.gov.hmrc.preferences.model.SurveyType.{ StandardInterruptOptOut, standardInterruptOptOut }
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions

import java.time.Instant

class SurveySpec extends PlaySpec {

  "SurveyType" should {
    """deserialize SandardInterruptOptOut from string "StandardInterrupOptOut" """ in {
      JsString("StandardInterruptOptOut").as[SurveyType] must be(SurveyType.StandardInterruptOptOut)
    }

    """serialize StandardInterruptOptOut to JsString("StandardInterruptOptOut")""" in {
      Json.toJson(standardInterruptOptOut) must be(JsString("StandardInterruptOptOut"))
    }

    "serialize and deserialize Survey" in {
      val date = Instant.parse("2015-05-13T00:00:00Z")
      val fixture = Json
        .parse(s"""
                  |{
                  |  "surveyType": "StandardInterruptOptOut",
                  |  "completedAt": {"$$date": {"$$numberLong": "${date.getMillis}"}}
                  |}""".stripMargin)
        .as[JsObject]
      fixture.as[Survey] must be(Survey(StandardInterruptOptOut, date))
      Json.toJson(Survey(StandardInterruptOptOut, date)) must be(fixture)
    }

  }

}
