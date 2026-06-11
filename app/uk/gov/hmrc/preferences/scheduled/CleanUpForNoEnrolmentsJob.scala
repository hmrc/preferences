/*
 * Copyright 2023 HM Revenue & Customs
 *
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
