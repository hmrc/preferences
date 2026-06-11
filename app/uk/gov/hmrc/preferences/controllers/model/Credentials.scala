/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers.model
import play.api.libs.json.{ Json, OFormat, OWrites }
import uk.gov.hmrc.auth.core.{ AffinityGroup, ConfidenceLevel }
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.auth.core.retrieve.Name.reads

case class Credentials(affinityGroup: Option[AffinityGroup], confidenceLevel: ConfidenceLevel)

case object Credentials {
  implicit val credentialsFormat: OFormat[Credentials] = Json.format[Credentials]
}
