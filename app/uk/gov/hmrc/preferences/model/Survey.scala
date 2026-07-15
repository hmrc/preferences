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

import play.api.libs.json.{ Format, JsError, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes }
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

enum SurveyType {
  case StandardInterruptOptOut
}

object SurveyType {
  val standardInterruptOptOut: SurveyType = SurveyType.StandardInterruptOptOut

  given surveyTypeReads: Reads[SurveyType] = Reads {
    case JsString(value) =>
      try
        JsSuccess(SurveyType.valueOf(value))
      catch {
        case _: IllegalArgumentException => JsError(s"Invalid SurveyType: $value")
      }
    case _ => JsError("Expected SurveyType as JsString")
  }

  given surveyTypeWrites: Writes[SurveyType] = Writes { surveyType =>
    JsString(surveyType.toString)
  }

  given Format[SurveyType] = new Format[SurveyType] {
    override def reads(json: JsValue): JsResult[SurveyType] = surveyTypeReads.reads(json)

    override def writes(surveyType: SurveyType): JsValue = surveyTypeWrites.writes(surveyType)
  }

}

final case class Survey(surveyType: SurveyType, completedAt: Instant)

object Survey {
  implicit val dateReads: Reads[Instant] = MongoJavatimeFormats.instantFormat
  implicit val dateWrites: Writes[Instant] = MongoJavatimeFormats.instantFormat
  implicit val suveyFormat: Format[Survey] = Json.format

  def create(surveyType: String, completedAtMilliseconds: Long): Survey =
    Survey(SurveyType.valueOf(surveyType), Instant.ofEpochMilli(completedAtMilliseconds))
}
