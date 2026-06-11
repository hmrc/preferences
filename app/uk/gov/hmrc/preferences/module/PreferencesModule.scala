/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.module

import com.google.inject.{ AbstractModule, Provides }
import net.codingwell.scalaguice.ScalaModule
import org.apache.pekko.stream.scaladsl.Sink
import play.api.{ Configuration, Logger }
import play.api.libs.concurrent.PekkoGuiceSupport
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.mongo.metrix.{ MetricOrchestrator, MetricSource, MongoMetricRepository }
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import uk.gov.hmrc.mongo.lock.{ LockService, MongoLockRepository }
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.preferences.config.CryptoProvider
import uk.gov.hmrc.preferences.jobs.*
import uk.gov.hmrc.preferences.model.EmailVerificationLink
import uk.gov.hmrc.preferences.repository.*
import uk.gov.hmrc.preferences.service.{ CleanUpForNoEnrolmentsService, EmailBounceLock }
import uk.gov.hmrc.preferences.{ Auditable, PreferencesMain }
import uk.gov.hmrc.preferences.connector.EntityResolverConnector
import uk.gov.hmrc.preferences.scheduled.{ CleanUpForNoEnrolmentsJob, CleanupUnverifiedMigrationJob, PreferencesCountResetJob, VerificationEmailReminders }
import uk.gov.hmrc.preferences.util.Dc

import java.time.Instant
import javax.inject.{ Named, Singleton }
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ Duration as SCDuration, MILLISECONDS }

// $COVERAGE-OFF$
class PreferencesModule extends AbstractModule with ScalaModule with PekkoGuiceSupport {

  private val logger: Logger = Logger(getClass)

  override def configure(): Unit = {
    bind[PreferencesMain].asEagerSingleton()
    bind[CleanUpForNoEnrolmentsJob].asEagerSingleton()
    bind[CleanupUnverifiedMigrationJob].asEagerSingleton()
    bind[PreferencesCountResetJob].asEagerSingleton()
    bind[VerificationEmailReminders].asEagerSingleton()
    bind[Decrypter].toProvider[CryptoProvider]
  }

  @Provides
  def sink(): Sink[Unit, ?] = Sink.ignore

  @Provides
  @Named("verificationTimeout")
  @Singleton
  def configVerificationTimeout(configuration: Configuration): Int = {
    val verificationTimeout = configuration.get[Int](s"verification.timeoutInDays")
    logger.warn(s"verification timeout period is set to $verificationTimeout")
    verificationTimeout
  }

  @Provides
  @Singleton
  def preferencesMongoRepositoryProvider(repo: PreferencesMongoRepository): PreferencesRepository = repo

  @Provides
  @Singleton
  def metricsProvider(statsRepository: StatsRepository): List[MetricSource] =
    List(statsRepository)

  @Provides
  @Named("refreshInterval")
  @Singleton
  def refreshIntervalProvider(runModeBridge: RunModeBridge): Long =
    runModeBridge.getLongMillis("microservice.metrics.gauges.interval")

  @Provides
  @Named("etmpUpdate")
  @Singleton
  def configETMPService(configuration: Configuration): Boolean = {
    val featureFlag = configuration.get[Boolean]("etmpUpdate.switchOn")
    logger.warn(s"Feature flag for etmpUpdate is set to $featureFlag")
    featureFlag
  }

  @Provides
  @Named("reoptinMajor")
  @Singleton
  def configReoptinMajor(configuration: Configuration): Int = {
    val reoptinMajor = configuration.get[Int]("reoptin.major")
    logger.warn(s"Major version for re-optin is $reoptinMajor")
    reoptinMajor
  }

  @Provides
  @Named("gracePeriod")
  @Singleton
  def confiGracePeriod(configuration: Configuration): Int = {
    val gracePeriod = configuration.get[Int](s"activation.gracePeriodInMin")
    logger.warn(s"activation grace period is set to $gracePeriod")
    gracePeriod
  }

  @Provides
  @Singleton
  def lockService(runModeBridge: RunModeBridge, repository: MongoLockRepository): LockService =
    LockService(
      repository,
      "preferences-metrics",
      SCDuration(runModeBridge.getLongMillis("microservice.metrics.gauges.interval"), MILLISECONDS)
    )

  @Provides
  @Singleton
  def metricsOrchestratorProvider(
    sources: List[MetricSource],
    metricsLock: LockService,
    mongoMetricRepository: MongoMetricRepository,
    metrics: Metrics
  ): MetricOrchestrator =
    new MetricOrchestrator(
      sources,
      metricsLock,
      mongoMetricRepository,
      metrics.defaultRegistry
    )

  @Provides
  @Named("taxPlatformSaPrefsRootUri")
  @Singleton
  def taxPlatformSaPrefsRootUriProvider(runModeBridge: RunModeBridge): String =
    runModeBridge.getStringForMode("taxPlatformSaPrefsRootUri")

  @Provides
  @Singleton
  def externalVerificationLinkProvider(
    @Named("taxPlatformSaPrefsRootUri") taxPlatformSaPrefsRootUri: String
  ): EmailVerificationLink => String =
    link => s"$taxPlatformSaPrefsRootUri/sa/print-preferences/verification/${link._id}"

  @Provides
  def auditProvider(auditConnector: AuditConnector, @Named("appName") appName: String): Audit =
    Audit(appName, auditConnector)

  @Provides
  def auditableProvider(audit: Audit, @Named("appName") appName: String): Auditable =
    Auditable(appName, audit)

  @Provides
  @Singleton
  def timeSourceProvider: () => Instant = () => Dc.instantNow()

  @Provides
  @Singleton
  def emailBounceProvider(runModeBridge: RunModeBridge, lockRepository: MongoLockRepository): EmailBounceLock =
    EmailBounceLock(
      LockService(
        lockRepository,
        "bounceQueue",
        SCDuration(runModeBridge.getLongMillis("bounceQueue.forceLockReleaseAfter"), MILLISECONDS)
      )
    )
}
// $COVERAGE-ON$
