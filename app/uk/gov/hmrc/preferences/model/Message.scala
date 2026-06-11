/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import uk.gov.hmrc.domain.TaxIds.TaxIdWithName

case class Content(lang: Language, subject: String, body: String)

case class ExternalRef(id: String, source: String)

case class Recipient(taxIdentifier: TaxIdWithName, email: String, name: Option[TaxpayerName] = Option.empty)

case class AlertDetails(templateId: String, recipientName: Option[TaxpayerName], data: Map[String, String])

case class TaxEntity(
  regime: String,
  taxIdentifier: TaxIdWithName,
  email: Option[String] = None,
  name: Option[TaxpayerName] = None
)

object TaxEntity {
  val idToRegime: TaxIdWithName => String = _.name match {
    case "nino"        => "paye"
    case "sautr"       => "sa"
    case "HMRC-MTD-IT" => "itsa"
  }
}
