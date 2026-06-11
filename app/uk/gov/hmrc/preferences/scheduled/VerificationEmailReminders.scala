/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.scheduled

import org.apache.commons.lang3.time.StopWatch
import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier

import scala.util.{ Failure, Success, Try }
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import uk.gov.hmrc.preferences.service.*
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.preferences.config.ScheduledJobConfig

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

// $COVERAGE-OFF$
@Singleton
class VerificationEmailReminders @Inject() (
  configuration: Configuration,
  verificationChaser: VerificationChaser,
  lifecycle: ApplicationLifecycle,
  sink: Sink[Unit, ?] = Sink.ignore
)(implicit actorSystem: ActorSystem) {
  private implicit val ec: ExecutionContext = actorSystem.dispatcher
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val name: String = "sendVerificationReminders"
  private val config = ScheduledJobConfig(configuration, name)
  private val logger: Logger = Logger(getClass)

  ScheduledStream
    .builder(config, name, logger, lifecycle, sink)(actorSystem)
    .withWorkload {
      verificationChaser.chaseVerifications.map { result =>
        val stopWatch = StopWatch.createStarted()
        result match {
          case ProcessingResult.Empty =>
            logger.warn(s"No work items found")
          case ProcessingResult(processed, successful) =>
            stopWatch.stop()
            logger.warn(
              s"[stopwatch: $stopWatch] Processed $processed email verification reminders, $successful successful"
            )
        }
      }
    }
    .withConditional {
      ActivePeriod(config, name).isActive
    }
    .build()
}
// $COVERAGE-ON$
