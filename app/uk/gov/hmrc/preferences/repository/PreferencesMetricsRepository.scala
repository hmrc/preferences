/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.repository

import com.mongodb.client.result.UpdateResult
import org.mongodb.scala.result
import org.mongodb.scala.model.{ Filters, IndexModel, IndexOptions, UpdateOptions, Updates }
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.inc
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ToSingleObservablePublisher
import play.api.Logger

import javax.inject.{ Inject, Singleton }
import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.metrix.MetricSource
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import scala.concurrent.{ ExecutionContext, Future }

case class PreferenceCount(name: String, total: Int, count: Int)

object PreferenceCount {
  implicit val formats: OFormat[PreferenceCount] = Json.format[PreferenceCount]
}

@Singleton
class PreferencesMetricsRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[PreferenceCount](
      mongoComponent = mongo,
      collectionName = "preferencesMetrics",
      domainFormat = PreferenceCount.formats,
      indexes = Seq(
        IndexModel(
          ascending("name"),
          IndexOptions()
            .name("preference_metric_key_idx")
            .unique(true)
            .background(true)
        )
      )
    ) with MetricSource {

  val logger: Logger = Logger(this.getClass)

  override def metrics(implicit ec: ExecutionContext): Future[Map[String, Int]] =
    collection
      .find[PreferenceCount](filter = Filters.empty())
      .map(pc => Map(s"${pc.name}.total" -> pc.total, s"${pc.name}.count" -> pc.count))
      .toSingle()
      .toFuture()

  def increment(key: String, by: Int): Future[Unit] =
    collection
      .updateOne(
        filter = Filters.equal("name", key),
        update = Updates.combine(inc("count", by), inc("total", by)),
        options = UpdateOptions().upsert(true)
      )
      .toFuture()
      .recover[result.UpdateResult] { case error =>
        logger.error(error.getMessage)
        UpdateResult.unacknowledged()
      }
      .map(_ => ())

  def reset(): Future[result.UpdateResult] =
    collection
      .updateOne( // TODO: Should this be update many?
        filter = Filters.empty(),
        update = Updates.set("count", 0)
      )
      .toFuture()
}

object PreferencesMetricsRepository {
  val userOptOut = "userOptOut"
  val manualOptOut = "manualOptOut"
}
