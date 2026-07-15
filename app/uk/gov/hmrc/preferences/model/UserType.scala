/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
