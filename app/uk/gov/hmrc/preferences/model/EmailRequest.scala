/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import play.api.libs.json.{ Json, OFormat }

case class EmailRequest(email: String)

object EmailRequest {
  implicit val formats: OFormat[EmailRequest] = Json.format[EmailRequest]
}
