/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import play.api.libs.json._

case class EntityId(value: String) {
  override def toString: String = value
}

object EntityId {

  implicit val read: Reads[EntityId] = new Reads[EntityId] {
    override def reads(json: JsValue): JsResult[EntityId] = json match {
      case JsString(s) => JsSuccess(EntityId(s))
      case _           => JsError("No entityId")
    }
  }

  implicit val write: Writes[EntityId] = new Writes[EntityId] {
    override def writes(e: EntityId): JsValue = JsString(e.value)
  }

  implicit val formats: Format[EntityId] = Format(read, write)
}
