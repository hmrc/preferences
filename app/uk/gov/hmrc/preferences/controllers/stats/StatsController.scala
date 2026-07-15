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

package uk.gov.hmrc.preferences.controllers.stats

import javax.inject.{ Inject, Singleton }
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.preferences.repository.{ StatsCounter, StatsRepository }

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class StatsController @Inject() (
  statsRepository: StatsRepository,
  statsCounter: StatsCounter,
  override val controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendBaseController {

  private val logger = Logger(getClass)

  def preferencesStats: Action[AnyContent] = Action.async { _ =>
    statsRepository.findAllWithDefaults().map(x => Ok(Json.toJson(x)))
  }

  def computeStatistics: Action[AnyContent] = Action.async { _ =>
    statsCounter.timedStatsQuery().map { millis =>
      logger.warn(s"statsCounter completed successfully in $millis millis")
    }
    Future.successful(Accepted)
  }
}
