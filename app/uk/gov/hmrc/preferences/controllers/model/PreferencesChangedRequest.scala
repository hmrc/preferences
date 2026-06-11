/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers.model

import play.api.libs.json.{ Format, Json, OWrites }
import uk.gov.hmrc.preferences.model.MessageDeliveryFormat
import uk.gov.hmrc.preferences.util.DateFormats

import java.time.Instant

case class PreferencesChangedRequest(
  changedValue: MessageDeliveryFormat,
  preferenceId: String,
  entityId: String,
  updatedAt: Instant,
  taxIds: Map[String, String],
  bounced: Boolean
)

object PreferencesChangedRequest {
  implicit val mdFormat: Format[MessageDeliveryFormat] = MessageDeliveryFormat.format
  implicit val dateTimeWrites: Format[Instant] = DateFormats.instantFormats
  implicit val writes: OWrites[PreferencesChangedRequest] = Json.writes[PreferencesChangedRequest]
}
