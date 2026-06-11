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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.config.ScheduledJobConfig
import uk.gov.hmrc.preferences.service.CleanupUnverifiedMigrationService

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ Duration, FiniteDuration }

// $COVERAGE-OFF$Disabling
@Singleton
class CleanupUnverifiedMigrationJob @Inject() (
  configuration: Configuration,
  cleanupUnverifiedMigrationService: CleanupUnverifiedMigrationService,
  lifecycle: ApplicationLifecycle,
  sink: Sink[Unit, ?] = Sink.ignore
)(implicit actorSystem: ActorSystem) {
  private val logger: Logger = Logger(getClass)
  private implicit val ec: ExecutionContext = actorSystem.dispatcher
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val name: String = "cleanupUnverifiedJob"
  private val config = ScheduledJobConfig(configuration, name)

  ScheduledStream
    .builder(config, name, logger, lifecycle, sink)(actorSystem)
    .withWorkload {
      val stopWatch = StopWatch.createStarted()
      val result = cleanupUnverifiedMigrationService.execute()
      result.map { r =>
        stopWatch.stop()
        logger.warn(s"[stopwatch $stopWatch] ${r.message}")
      }
    }
    .withConditional {
      ActivePeriod(config, name).isActive
    }
    .build()
}
// $COVERAGE-ON$
