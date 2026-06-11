/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.util

import play.api.libs.json.{ Format, Reads, Writes }

import java.time.{ Instant, LocalDate, ZoneOffset }
import java.time.format.DateTimeFormatter

object DateFormats {

  // Format Instant non-mongo
  implicit val instantFormats: Format[Instant] = {
    val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    val dateTimeWithMillis: DateTimeFormatter =
      DateTimeFormatter.ofPattern(dateFormat).withZone(ZoneOffset.UTC)

    Format(Reads.DefaultInstantReads, Writes.temporalWrites[Instant, DateTimeFormatter](dateTimeWithMillis))
  }

  // Format LocalDate, non-mongo
  implicit val localDateFormats: Format[LocalDate] =
    Format(Reads.DefaultLocalDateReads, Writes.DefaultLocalDateWrites)
}
