/*
 * Copyright 2023 HM Revenue & Customs
 *
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
