/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Reads._
import uk.gov.hmrc.auth.core.{ AffinityGroup, ConfidenceLevel }

final case class UserType(affinityGroup: Option[AffinityGroup] = None, confidenceLevel: Option[ConfidenceLevel] = None)

object UserType {

  private val logger = Logger(getClass)

  implicit val userTypeReads: Reads[UserType] = (
    (__ \ "affinityGroup").readNullable[AffinityGroup] and
      (__ \ "confidenceLevel").readNullable[ConfidenceLevel]
  ) { (affinityGroup, confidenceLevel) =>
    if (affinityGroup.isEmpty) {
      logger.warn("Reads - AffinityGroup is missing")
    }
    UserType(affinityGroup, confidenceLevel)
  }

  implicit val userTypeWrites: Writes[UserType] = Json.writes[UserType]
}
