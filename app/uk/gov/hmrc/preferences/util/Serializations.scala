/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.util

object Serializations {
  def snakeToPascalCase(s: String): String =
    s.toLowerCase.split('_').map(_.capitalize).mkString

  def pascalToSnakeCase(s: String): String =
    s.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase

}
