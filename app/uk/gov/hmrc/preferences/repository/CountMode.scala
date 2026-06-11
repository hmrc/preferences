/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.repository

import org.bson.conversions.Bson

sealed trait CountMode
object CountMode {
  case object Total extends CountMode
  final case class Distinct(field: String) extends CountMode
}

final case class QuerySpec(
  name: String,
  filters: Seq[Bson],
  mode: CountMode
)
