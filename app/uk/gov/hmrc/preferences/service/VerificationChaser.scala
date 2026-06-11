/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{ Sink, Source }

import javax.inject.{ Inject, Singleton }
import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.connector.EmailConnector
import uk.gov.hmrc.preferences.jobs.RunModeBridge
import uk.gov.hmrc.preferences.model.Reminders.*
import uk.gov.hmrc.preferences.repository.{ PreferencesRepository, ReminderWorkItem }
import uk.gov.hmrc.preferences.util.Dc

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class VerificationChaser @Inject() (
  runModeBridge: RunModeBridge,
  preferencesRepository: PreferencesRepository,
  emailConnector: EmailConnector
)(implicit ec: ExecutionContext, materializer: Materializer) {

  private val logger: Logger = Logger(getClass)

  private lazy val failureProcessingIntervalMillis =
    runModeBridge.getLongMillis("verificationReminders.retryFailedAfter")

  private lazy val reminderDispatchIntervalMillis =
    runModeBridge.getLongMillis("verificationReminders.sendRemindersAfter")

  private lazy val anotherReminderDispatchIntervalMillis =
    runModeBridge.getLongMillis("verificationReminders.anotherReminderAfter")

  def chaseVerifications(implicit hc: HeaderCarrier): Future[ProcessingResult] = {

    def unverifiedEmailsBefore: Instant = Dc.instantNow().minusMillis(reminderDispatchIntervalMillis.toInt)

    def anotherUnverifiedEmailsBefore = Dc.instantNow().minusMillis(anotherReminderDispatchIntervalMillis.toInt)

    def incompleteBefore = Dc.instantNow().minusMillis(failureProcessingIntervalMillis.toInt)

    def sendEmail(emailAddress: String, verificationLink: String, daysAgo: String) =
      emailConnector.sendVerificationReminder(emailAddress, verificationLink, daysAgo)

    def processItems(): Future[Option[Boolean]] =
      for {
        maybeFirstReminderItems <- preferencesRepository
                                     .pullReminder(unverifiedEmailsBefore, incompleteBefore, firstReminder)
        maybeItemsToProcess <- maybeFirstReminderItems match {
                                 case None =>
                                   preferencesRepository
                                     .pullReminder(anotherUnverifiedEmailsBefore, incompleteBefore, secondReminder)
                                 case a =>
                                   Future.successful(a)
                               }
        maybeOk <- maybeItemsToProcess match {
                     case Some(workItem) =>
                       processItem(
                         sendEmail,
                         preferencesRepository.setReminderSucceeded,
                         preferencesRepository.setReminderFailed
                       )(workItem).map(Some.apply)
                     case None =>
                       Future.successful(None)
                   }
      } yield maybeOk

    def generateM[A](itemPublisherFn: () => Future[Option[A]]): Source[A, NotUsed] =
      Source.unfoldAsync(())(_ => itemPublisherFn().map(_.map(((), _))))

    val counter = Sink.fold[ProcessingResult, Boolean](ProcessingResult.Empty) { case (pr, isSuccessful) =>
      ProcessingResult(
        processedCount = pr.processedCount + 1,
        successfulCount = pr.successfulCount + (if (isSuccessful) 1 else 0)
      )
    }
    generateM[Boolean](() => processItems()).runWith(counter)
  }

  def processItem(
    dispatch: (String, String, String) => Future[Unit],
    successful: ReminderWorkItem => Future[Boolean],
    failed: ReminderWorkItem => Future[Boolean]
  )(workItem: ReminderWorkItem): Future[Boolean] = {
    for {
      _ <-
        dispatch(workItem.email, runModeBridge.externalVerificationLink(workItem.verificationLink), daysAgo(workItem))
      _ <- successful(workItem)
    } yield true
  }.recoverWith { case e =>
    logger.error(s"Could not send email reminder: ${e.getMessage}")
    failed(workItem).map(_ => false)
  }
}
