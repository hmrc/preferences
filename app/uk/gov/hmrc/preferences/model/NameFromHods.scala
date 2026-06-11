/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model
import play.api.libs.json.{ Json, OFormat }

case class NameFromHods(name: Option[TaxpayerName])

object NameFromHods {

  implicit val taxpayerNameFormat: OFormat[TaxpayerName] = TaxpayerName.formats
  implicit val format: OFormat[NameFromHods] = Json.format[NameFromHods]
}
