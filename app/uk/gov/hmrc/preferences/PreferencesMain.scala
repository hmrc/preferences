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

package uk.gov.hmrc.preferences

import scala.concurrent.{ ExecutionContext, Future }
import org.apache.pekko.actor.ActorSystem
import play.api.{ Configuration, Logger }

import javax.inject.{ Inject, Named, Singleton }
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator

import scala.annotation.unused
import scala.concurrent.duration.{ DurationInt, DurationLong }
import scala.util.{ Failure, Success, Try }

// $COVERAGE-OFF$Nothing to see here
@Singleton
class PreferencesMain @Inject() (
  metricOrchestrator: MetricOrchestrator,
  actorSystem: ActorSystem,
  @unused configuration: Configuration,
  @Named("refreshInterval") refreshInterval: Long,
  lifecycle: ApplicationLifecycle
)(implicit val ec: ExecutionContext) {

  val logger: Logger = Logger(getClass)

  lifecycle.addStopHook(() =>
    Future {
      actorSystem.terminate()
      metricOrchestrator
    }
  )

  actorSystem.scheduler.scheduleWithFixedDelay(60 seconds, refreshInterval milliseconds) { () =>
    Try {
      metricOrchestrator
        .attemptMetricRefresh()
        .foreach(_.log())
    } match {
      case Failure(e) => logger.error(s"An error occurred processing metrics: ${e.getMessage}", e)
      case Success(_) => ()
    }
  }
}
// $COVERAGE-ON$
