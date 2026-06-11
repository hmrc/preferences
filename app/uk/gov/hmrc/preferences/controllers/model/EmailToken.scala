/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers.model

import play.api.libs.json.{ Json, OFormat }

case class EmailToken(token: String) {

  private val regex = "(^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}$)"

  def isValid: Boolean = token.matches(regex)

  override def toString: String = token
}

object EmailToken {
  implicit val formats: OFormat[EmailToken] = Json.format[EmailToken]
}
