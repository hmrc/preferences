/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package controller

import conf.{ CleanMongoCollection, ISpec }
import org.apache.pekko.stream.Materializer
import play.api.libs.json.{ Format, JsResult, JsValue, Json }
import uk.gov.hmrc.preferences.controllers.EmailVerificationController
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import uk.gov.hmrc.preferences.util.{ DateFormats, Dc }
import utils.GenerateRandom

import java.io.File
import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.language.postfixOps
import scala.util.Using

trait TermsAndConditionsControllerISpecBase extends ISpec with EntityResolverSupport {

  implicit val datetimeFormatDefault: Format[Instant] = new Format[Instant] {
    override def reads(json: JsValue): JsResult[Instant] = DateFormats.instantFormats.reads(json)

    override def writes(o: Instant): JsValue = DateFormats.instantFormats.writes(o)
  }

  val shouldBeUpdatedAfterThisTime: Instant = Dc.instantNow()
  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  implicit val materializer: Materializer = app.injector.instanceOf[Materializer]
  val repo = app.injector.instanceOf[PreferencesRepository]

  val emailVerificationController = app.injector.instanceOf[EmailVerificationController]
  val resourcePath: String = sys.props.getOrElse("RESOURCE_PATH", "./test/resources")

  def readFromResource(file: String, replaceEmail: String): JsValue = {
    val resource = Using(Source.fromFile(new File(s"$resourcePath/$file"))) { source =>
      source.getLines().mkString("\n")
    }
    Json.parse(resource.get.replace("test@test.com", replaceEmail))
  }

  trait TestCase extends ISpecTestCase {
    val email: String = GenerateRandom.email()
    val testTime: Instant = Instant.now
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]

}
