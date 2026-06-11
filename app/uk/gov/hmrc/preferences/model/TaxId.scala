/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import play.api.libs.functional.syntax._
import play.api.libs.json.{ Format, Json, Reads, __ }

case class TaxId(_id: String, sautr: Option[String], nino: Option[String], hmrcMtdItsa: Option[String] = None)

object TaxId {
  implicit val formats: Format[TaxId] = {
    val taxIdReads: Reads[TaxId] = ((__ \ "_id").read[String] and
      (__ \ "sautr").readNullable[String] and
      (__ \ "nino").readNullable[String] and
      (__ \ "HMRC-MTD-IT").readNullable[String])((_id, sautr, nino, hmrcMtdItsa) =>
      TaxId(_id, sautr, nino, hmrcMtdItsa)
    )
    Format(taxIdReads, Json.writes[TaxId])
  }

}
