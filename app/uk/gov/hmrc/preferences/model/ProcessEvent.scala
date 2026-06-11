/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import play.api.libs.json.{ JsValue, Json, Reads }
import java.time.LocalDateTime
import java.util.UUID

final case class ProcessEvent(eventId: UUID, subject: String, groupId: String, timestamp: LocalDateTime, event: JsValue)

object ProcessEvent {
  implicit val updateEventReads: Reads[ProcessEvent] = Json.reads[ProcessEvent]
}
