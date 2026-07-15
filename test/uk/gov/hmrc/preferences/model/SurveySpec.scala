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
