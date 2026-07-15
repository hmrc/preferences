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

package uk.gov.hmrc.preferences.controllers.stats

import org.mockito.Mockito.{ times, verify, when }
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.{ contentAsJson, defaultAwaitTimeout, status }
import play.api.test.{ FakeRequest, Helpers }
import uk.gov.hmrc.preferences.model.DatedCount
import uk.gov.hmrc.preferences.repository.{ StatsCounter, StatsRepository }

import java.time.LocalDate
import scala.concurrent.Future

class StatsControllerSpec extends PlaySpec {

  "stats controller" must {
    "get preferences stats" in new TestCase {
      val fakeRequest = FakeRequest("GET", routes.StatsController.preferencesStats.url)
      when(mockStatsRepository.findAllWithDefaults())
        .thenReturn(Future.successful(Map("item" -> DatedCount(1, testTime))))
      val result = controller.preferencesStats(fakeRequest)
      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.parse("""{"item":{"count":1,"date":"2022-12-01"}}""")
      verify(mockStatsRepository, times(1)).findAllWithDefaults()
    }

    "compute statistics" in new TestCase {
      val fakeRequest = FakeRequest("POST", routes.StatsController.computeStatistics.url)
      when(mockStatsCounter.timedStatsQuery()).thenReturn(Future.successful(1L))
      val result = controller.computeStatistics(fakeRequest)
      status(result) mustBe Status.ACCEPTED
      verify(mockStatsCounter, times(1)).timedStatsQuery()
    }

    class TestCase {

      import scala.concurrent.ExecutionContext.Implicits.global

      val mockStatsRepository: StatsRepository = mock[StatsRepository]
      val mockStatsCounter: StatsCounter = mock[StatsCounter]
      val testTime = LocalDate.parse("2022-12-01")

      val controller =
        new StatsController(
          mockStatsRepository,
          mockStatsCounter,
          Helpers.stubControllerComponents()
        )

    }
  }
}
