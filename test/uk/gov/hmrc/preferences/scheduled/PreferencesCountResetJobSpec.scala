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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.scaladsl.TestSink
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ times, verify, verifyNoInteractions, verifyNoMoreInteractions, when }
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.preferences.scheduling.Result
import uk.gov.hmrc.preferences.service.PreferencesCountResetService

import java.time.{ Instant, LocalTime }
import java.time.format.DateTimeFormatter
import scala.concurrent.{ ExecutionContext, Future }

class PreferencesCountResetJobSpec extends PlaySpec {
  val testKit = ActorTestKit()
  implicit val system: ActorSystem = testKit.system.classicSystem
  implicit val ec: ExecutionContext = system.dispatcher
  implicit lazy val materializer: Materializer = Materializer(system)

  "PreferencesCountResetJob" should {

    "emits elements correctly" in new Setup {
      when(mockService.execute).thenReturn(Future.successful(Result("any")))

      probeSubscriber
        .request(2)
        .expectNext(())
        .expectNext(())
    }

    "respect configured delays and intervals" in new Setup {
      when(mockService.execute).thenReturn(Future.successful(Result("any")))

      val startTime = System.currentTimeMillis()

      probeSubscriber
        .request(2)
        .expectNext(()) // Should arrive after ~100ms

      val firstElementTime = System.currentTimeMillis()
      (firstElementTime - startTime) must be >= 100L

      probeSubscriber
        .expectNext(()) // Should arrive after another ~200ms

      val secondElementTime = System.currentTimeMillis()
      (secondElementTime - firstElementTime) must be >= 180L // Give it some leeway
    }

    "successfully call service during workload processing" in new Setup {
      when(mockService.execute).thenReturn(Future.successful(Result("any")))

      probeSubscriber
        .request(2)
        .expectNext(())
        .expectNext(())

      // Verify external service was called
      verify(mockService, times(2)).execute
      verifyNoMoreInteractions(mockService)
    }

    "not do anything if job disabled" in new Setup {
      val newConfig =
        Configuration(s"scheduling.$jobName.taskEnabled" -> false).withFallback(configuration)

      val scheduledUpdatedJob =
        new PreferencesCountResetJob(
          newConfig,
          mockService,
          lifecycle
        )
      verifyNoInteractions(mockService)
    }
  }

  trait Setup {
    val jobName = "preferencesCountReset"

    val configuration = Configuration(
      s"scheduling.$jobName.initialDelay" -> "100 milliseconds",
      s"scheduling.$jobName.interval"     -> "200 milliseconds",
      s"scheduling.$jobName.taskEnabled"  -> true
    )
    val mockService = mock[PreferencesCountResetService]
    val lifecycle = mock[ApplicationLifecycle]
    val failedAfter: Long = 5L

    val (probeSubscriber, probeSink) = TestSink.probe[Unit].preMaterialize()

    val scheduledJob =
      PreferencesCountResetJob(
        configuration,
        mockService,
        lifecycle,
        sink = probeSink
      )
  }
}
