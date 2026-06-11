/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import play.api.libs.json._

sealed abstract class MessageDeliveryFormat(val name: String)

object MessageDeliveryFormat {
  case object Paper extends MessageDeliveryFormat(name = "paper")
  case object Digital extends MessageDeliveryFormat(name = "digital")

  implicit val reads: Reads[MessageDeliveryFormat] =
    Reads[MessageDeliveryFormat] {
      case JsString(value) if value == Paper.name   => JsSuccess(Paper)
      case JsString(value) if value == Digital.name => JsSuccess(Digital)
      case _                                        => JsError("Invalid message delivery format")
    }

  implicit val writes: Writes[MessageDeliveryFormat] =
    (e: MessageDeliveryFormat) => JsString(e.name)

  implicit val format: Format[MessageDeliveryFormat] = Format(reads, writes)
}
