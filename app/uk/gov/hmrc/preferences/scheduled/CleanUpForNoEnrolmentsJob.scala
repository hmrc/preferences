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

package uk.gov.hmrc.preferences.scheduled

import org.apache.commons.lang3.time.StopWatch
import org.apache.pekko.actor.ActorSystem

import org.apache.pekko.stream.scaladsl.Sink
import play.api.Logger
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.preferences.config.ScheduledJobConfig
import uk.gov.hmrc.preferences.service.CleanUpForNoEnrolmentsService

import javax.inject.{ Inject, Singleton }
import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

// $COVERAGE-OFF$
@Singleton
class CleanUpForNoEnrolmentsJob @Inject() (
  configuration: Configuration,
  cleanUpService: CleanUpForNoEnrolmentsService,
  lifecycle: ApplicationLifecycle,
  sink: Sink[Unit, ?] = Sink.ignore
)(implicit actorSystem: ActorSystem) {

  private implicit val ec: ExecutionContext = actorSystem.dispatcher

  private val name: String = "cleanUpForNoEnrolments"
  private val config = ScheduledJobConfig(configuration, name)
  private val logger: Logger = Logger(getClass)

  ScheduledStream
    .builder(config, name, logger, lifecycle, sink)(actorSystem)
    .withWorkload {
      val stopWatch = StopWatch.createStarted()
      cleanUpService.execute.map { result =>
        stopWatch.stop()
        logger.warn(s"[stopwatch: $stopWatch] ${result.message}")
      }
    }
    .withConditional {
      ActivePeriod(config, name).isActive
    }
    .build()
}
// $COVERAGE-ON$
