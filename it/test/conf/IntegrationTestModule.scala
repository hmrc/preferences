/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package conf

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import org.mockito.Mockito
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

class IntegrationTestModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[CleanMongoCollection]
    bind[EmailService]
    bind[TestEmailService]
    bind[PreferencesBuilder]
    bind[Metrics].toInstance(Mockito.mock(classOf[Metrics]))
  }
}
