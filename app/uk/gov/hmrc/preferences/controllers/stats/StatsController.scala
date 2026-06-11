/*
 * Copyright 2023 HM Revenue & Customs
 *
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
