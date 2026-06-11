/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import com.codahale.metrics.SharedMetricRegistries
import com.mongodb.client.result.UpdateResult
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.mongodb.scala.bson.BsonNull
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers.mustBe
import uk.gov.hmrc.preferences.repository.PreferencesMetricsRepository

import scala.concurrent.{ ExecutionContext, Future }

class PreferencesCountResetServiceSpec
    extends AnyWordSpecLike with MockitoSugar with ScalaFutures with IntegrationPatience with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
    SharedMetricRegistries.clear()
  }

  "PreferencesCountResetServiceSpec - " should {

    "remove all metric counts" in new TestCase {
      when(repository.reset()).thenReturn(Future.successful(UpdateResult.acknowledged(0L, 0L, BsonNull())))
      service.execute.futureValue.message mustBe "Successfully reset metric counts"
    }

    "notify when failed to remove the metric counts" in new TestCase {
      when(repository.reset()).thenReturn(Future.successful(UpdateResult.unacknowledged()))
      service.execute.futureValue.message mustBe "Could not reset metric counts, update was not acknowledged"
    }

    trait TestCase {
      implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

      val repository = mock[PreferencesMetricsRepository]
      val service = new PreferencesCountResetService(repository)
    }
  }
}
