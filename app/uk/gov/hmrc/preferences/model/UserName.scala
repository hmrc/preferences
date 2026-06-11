/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import play.api.libs.json.{ Format, Json }

final case class UserName(name: Option[String], lastName: Option[String])

object UserName {
  implicit val userNameFormat: Format[UserName] = Json.format
}
