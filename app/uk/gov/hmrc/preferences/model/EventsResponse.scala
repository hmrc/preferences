/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import play.api.libs.json.*

import java.time.{ Instant, ZoneOffset }
import java.time.format.DateTimeFormatter

case class EventsResponse(
  eventType: EventType,
  emailAddress: Option[String],
  timestamp: Instant,
  viaMobileApp: Boolean = false
)

object EventsResponse {
  given instantFormats: Format[Instant] = {
    val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    val dateTimeWithMillis: DateTimeFormatter =
      DateTimeFormatter.ofPattern(dateFormat).withZone(ZoneOffset.UTC)

    Format(Reads.DefaultInstantReads, Writes.temporalWrites[Instant, DateTimeFormatter](dateTimeWithMillis))
  }

  given format: Format[EventsResponse] = Json.format[EventsResponse]
}
