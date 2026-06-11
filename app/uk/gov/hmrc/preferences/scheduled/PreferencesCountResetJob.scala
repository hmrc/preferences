/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.scheduled

import org.apache.commons.lang3.time.StopWatch
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import play.api.inject.ApplicationLifecycle
import play.api.{ Configuration, Logger }
import uk.gov.hmrc.preferences.config.ScheduledJobConfig
import uk.gov.hmrc.preferences.service.{ PreferencesCountResetService, VerificationChaser }

import java.time.{ Duration, Instant }
import javax.inject.{ Inject, Singleton }
import scala.annotation.unused
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.DurationConverters.JavaDurationOps

// $COVERAGE-OFF$
@Singleton
class PreferencesCountResetJob @Inject() (
  configuration: Configuration,
  preferencesCountResetService: PreferencesCountResetService,
  lifecycle: ApplicationLifecycle,
  sink: Sink[Unit, ?] = Sink.ignore
)(implicit actorSystem: ActorSystem) {
  private implicit val ec: ExecutionContext = actorSystem.dispatcher

  private val name = "preferencesCountReset"
  private val config = ScheduledJobConfig(configuration, name)
  private val logger = Logger(getClass)

  // Define the base workload
  private def counterWorkload: Future[Unit] =
    preferencesCountResetService.execute.map { result =>
      logger.warn(s"Counter result: ${result.message}")
    }

  // Build the stream
  ScheduledStream
    .builder(config, name, logger, lifecycle, sink)(actorSystem)
    .withWorkload(counterWorkload)
    .build()
}
// $COVERAGE-ON$
