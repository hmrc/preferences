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
