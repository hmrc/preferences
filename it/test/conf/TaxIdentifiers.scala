/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package conf

import uk.gov.hmrc.domain.{ Nino, SaUtr, TaxIdentifier }

case class TaxIdentifiers(utr: SaUtr, nino: Option[Nino]) {
  lazy val toList: List[TaxIdentifier] = List(utr) ++ nino
}

object TaxIdentifiers {
  def apply(utr: SaUtr, nino: Nino): TaxIdentifiers = TaxIdentifiers(utr, Some(nino))
  def apply(utr: SaUtr): TaxIdentifiers = TaxIdentifiers(utr, None)
}
