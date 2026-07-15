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

package uk.gov.hmrc.preferences.controllers

import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc.{ Action, ControllerComponents }
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.preferences.Auditable
import uk.gov.hmrc.preferences.connector.Bounce
import uk.gov.hmrc.preferences.model.ProcessEvent
import uk.gov.hmrc.preferences.service.EmailBounceQueueMonitorService

import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class EmailBounceController @Inject() (
  emailBounceQueueMonitorService: EmailBounceQueueMonitorService,
  val controllerComponents: ControllerComponents,
  auditable: Auditable
)(implicit ec: ExecutionContext)
    extends BackendBaseController {
  private val logger: Logger = Logger(getClass)

  def process: Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ProcessEvent] { event =>
      val eventId = event.eventId.toString
      logger.info(s"EmailBounceController: processing eventId $eventId")

      val bounceItem = bounceObject(event)
      val emailEventId = (event.event \ "id").asOpt[String]
      val emailEventMap =
        emailEventId.fold(Map.empty[String, String]) { mailgunEventId =>
          Map("emailEventId" -> mailgunEventId)
        }

      for {
        _ <- emailBounceQueueMonitorService.markAsBounced(bounceItem)
        _ <- Future.successful(
               auditable.sendDataEvent(
                 "preferences-bounced-eventhub",
                 detail = Map[String, String](
                   "eventId"      -> eventId,
                   "timestamp"    -> event.timestamp.toString,
                   "subject"      -> event.subject,
                   "event"        -> event.event.toString(),
                   "emailAddress" -> bounceItem.emailAddress,
                   "groupId"      -> event.groupId
                 ) ++ emailEventMap,
                 tags = Map(
                   "eventId"      -> UUID.nameUUIDFromBytes(bounceItem.emailAddress.getBytes).toString,
                   "emailAddress" -> bounceItem.emailAddress
                 )
               )
             )
      } yield Ok(s"Bounce processed successfully for ${event.eventId}")
    }
  }

  def bounceObject(event: ProcessEvent): Bounce = {
    val detected = event.timestamp.toInstant(ZoneOffset.UTC)
    val emailSource = Some("preferences")
    val code = (event.event \ "code").asOpt[Int]
    val emailAddress = (event.event \ "emailAddress").as[String]
    val formType = (event.event \ "tags" \ "form-type").asOpt[String]
    val nino = (event.event \ "tags" \ "nino").asOpt[String]
    Bounce(emailAddress, detected, code, emailSource, None, formType, nino)
  }
}
