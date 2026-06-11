/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.repository

import com.codahale.metrics.SharedMetricRegistries
import org.mongodb.scala.ObservableFuture
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.{ BeforeAndAfterEach, LoneElement }
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext

class PreferencesMetricsRepositorySpec
    extends PlaySpec with DefaultPlayMongoRepositorySupport[PreferenceCount] with ScalaFutures with LoneElement
    with BeforeAndAfterEach with IntegrationPatience {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  override val repository: PreferencesMetricsRepository = new PreferencesMetricsRepository(mongoComponent)

  override protected def checkTtlIndex: Boolean = false

  override def beforeEach(): Unit = {
    super.beforeEach()
    SharedMetricRegistries.clear()
  }

  "increment" should {
    "insert the key and value if not already present" in {

      repository.increment("test-key1", 1).futureValue
      repository.increment("test-key2", 1).futureValue

      val result = repository.collection.find().toFuture().futureValue

      result must contain.allOf(
        PreferenceCount("test-key1", 1, 1),
        PreferenceCount("test-key2", 1, 1)
      )
    }

    "insert the key and value multiple times" in {
      repository.increment("test-key", 1).futureValue
      repository.increment("test-key", 3).futureValue
      repository.increment("test-key", 5).futureValue

      repository.collection.find().toFuture().futureValue.loneElement mustBe PreferenceCount("test-key", 9, 9)
    }

    "reset the counters but not the total" in {
      repository.increment("test-key", 3).futureValue
      repository.reset().futureValue

      repository.collection.find().toFuture().futureValue.loneElement mustBe PreferenceCount("test-key", 3, 0)
    }

  }
}
