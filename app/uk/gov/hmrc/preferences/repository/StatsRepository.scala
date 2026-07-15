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

package uk.gov.hmrc.preferences.repository

import org.apache.commons.lang3.time.StopWatch
import play.api.Logger

import javax.inject.{ Inject, Singleton }
import uk.gov.hmrc.mongo.play.json.{ Codecs, PlayMongoRepository }
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.metrix.MetricSource
import uk.gov.hmrc.preferences.CurrentTime
import uk.gov.hmrc.preferences.model.{ DatedCount, Entry }
import org.mongodb.scala.ObservableFuture
import org.mongodb.scala.model.Filters

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class StatsRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository(
      mongo,
      "saPreferencesStats",
      Entry.formats,
      Seq(),
      extraCodecs = Seq(Codecs.playFormatCodec(Entry.formats))
    ) with MetricSource with CurrentTime {
  val logger: Logger = Logger(this.getClass)

  val defaults: Map[String, DatedCount] = List(
    "generic.optedIn",
    "generic.optedOut",
    "generic.optedInAndVerified",
    "generic.optedInAndVerifiedAndWelsh",
    "generic.reOptedIn",
    "generic.customerReOptedOut",
    "verifiedButBounced"
  ).map(k => k -> DatedCount(0)).toMap

  def findAllWithDefaults(): Future[Map[String, DatedCount]] =
    collection.find().toFuture().map { (r: Seq[Entry]) =>
      defaults ++ r.map(e => e._id -> e.value)
    }

  override def metrics(implicit ec: ExecutionContext): Future[Map[String, Int]] = {
    val stopWatch = StopWatch.createStarted()
    findAllWithDefaults().map(_.map { metric =>
      if (stopWatch.isStarted) stopWatch.stop()
      logger.debug(s"[stopwatch $stopWatch] metrics findAllWithDefaults")
      metric match {
        case (name, DatedCount(value, _)) => s"statistics.$name" -> value
      }
    })
  }
}
