/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import play.api.libs.json.{ Json, OFormat }

case class TaxpayerName(
  title: Option[String] = None,
  forename: Option[String] = None,
  secondForename: Option[String] = None,
  surname: Option[String] = None,
  honours: Option[String] = None,
  line1: Option[String] = None,
  line2: Option[String] = None,
  line3: Option[String] = None
)

object TaxpayerName {

  implicit val formats: OFormat[TaxpayerName] = Json.format[TaxpayerName]
}
