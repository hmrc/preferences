/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers

import javax.inject.Inject
import play.api.libs.json._
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.preferences.model._
import uk.gov.hmrc.preferences.service._

import scala.concurrent.ExecutionContext

class EventsController @Inject() (eventService: EventService, override val controllerComponents: ControllerComponents)(
  implicit ec: ExecutionContext
) extends BackendBaseController {

  def getEvents(entityId: EntityId): Action[AnyContent] = Action.async { implicit request =>
    eventService.getEvents(entityId).map(event => Ok(Json.toJson(event))).recover { case e: NotFoundException =>
      NotFound(e.getMessage)
    }
  }
}
