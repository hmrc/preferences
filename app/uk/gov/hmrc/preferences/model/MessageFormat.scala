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

import play.api.libs.json._
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.preferences.util.DateFormats

import java.time.LocalDate

object MessageFormat {

  implicit val identifierWrites: Writes[TaxIdWithName] = new Writes[TaxIdWithName] {
    override def writes(taxId: TaxIdWithName): JsValue =
      JsObject(Seq("name" -> JsString(taxId.name), "value" -> JsString(taxId.value)))
  }
  implicit val taxpayerNameWrites: Writes[TaxpayerName] = Json.writes[TaxpayerName]
  implicit val recipientWrites: Writes[Recipient] = Json.writes[Recipient]
  implicit val externalRefWrites: Writes[ExternalRef] = Json.writes[ExternalRef]
  implicit val dateWrites: Writes[LocalDate] = DateFormats.localDateFormats
  implicit val alertDetailsWrites: Writes[AlertDetails] = Json.writes[AlertDetails]
  implicit val contentFormat: OFormat[Content] = Json.format[Content]
  implicit val taxEntityWrites: Writes[TaxEntity] = Json.writes[TaxEntity]
}
