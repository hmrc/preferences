/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import play.api.libs.json.{ Format, Json, OFormat }
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.preferences.util.DateFormats

import java.time.{ LocalDate, ZoneId }

case class Entry(_id: String, value: DatedCount)

object Entry {
  val formats: OFormat[Entry] = {
    implicit val ldf: Format[LocalDate] = MongoJavatimeFormats.localDateFormat
    implicit val dcf: OFormat[DatedCount] = Json.format[DatedCount]
    Json.format[Entry]
  }
}

case class DatedCount(count: Int, date: LocalDate = LocalDate.now(ZoneId.of("UTC")))

object DatedCount {
  implicit val datedCountFormats: OFormat[DatedCount] = {
    implicit val localDateFormats: Format[LocalDate] = DateFormats.localDateFormats
    Json.format[DatedCount]
  }
}
