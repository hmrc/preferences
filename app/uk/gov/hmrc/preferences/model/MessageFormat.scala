/*
 * Copyright 2023 HM Revenue & Customs
 *
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
