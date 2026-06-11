/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.repository

import play.api.libs.json.Format
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

trait QueryBase {
  implicit val dateFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
}
