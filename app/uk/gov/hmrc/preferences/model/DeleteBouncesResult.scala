/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import play.api.libs.json.{ Json, OFormat }

case class DeleteBouncesResult(
  preferenceNotFound: Seq[EntityId] = Seq.empty,
  noVerifiedEmail: Seq[EntityId] = Seq.empty,
  notBounced: Seq[EntityId] = Seq.empty,
  auditFailed: Seq[EntityId] = Seq.empty,
  failed: Seq[EntityId] = Seq.empty
)

object DeleteBouncesResult {
  implicit val formats: OFormat[DeleteBouncesResult] = Json.format[DeleteBouncesResult]
}
