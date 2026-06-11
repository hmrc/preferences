/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package controller

import conf.PreferencesTestRoutes.*
import org.scalatest.concurrent.IntegrationPatience
import play.api.http.Status.*
import play.api.libs.json.{ JsValue, Json }
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.paperless.controllers.model.PreferenceResponse
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.model.PageType.IPage
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import utils.GenerateRandom

import java.io.File
import scala.io.Source

class NewTermsAndConditionsControllerISpec
    extends TermsAndConditionsControllerISpecBase with MongoSupport with IntegrationPatience
    with EntityResolverSupport {

  "record Event" should {
    val resourcePath = sys.props.getOrElse("RESOURCE_PATH", "./test/resources")
    val email = GenerateRandom.email()

    def readFromResource(file: String): JsValue = {
      val source = Source.fromFile(new File(s"$resourcePath/$file"))
      try {
        val resource = source.getLines().mkString("\n")
        Json.parse(resource.replace("test@test.com", email))
      } finally source.close()
    }

    "be updated with optInPage and returns majorVersion" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      private val emailId = GenerateRandom.email()

      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

      preferencesBuilder
        .acceptGenericTermsAndVerifyEmail(
          entityId,
          emailId,
          Some(authHelper.authHeader(nino, ggAuthPort)),
          OptInPage(Version(222, 111), 1, IPage)
        )

      private val result = preferencesTestRoutes.get(`/preferences/:entityId`(entityId))
      result.status must be(OK)
      private val genericTandCResponse = result.json.as[PreferenceResponse].termsAndConditions.get("generic")
      private val version = for {
        genericTandC <- genericTandCResponse
        version      <- genericTandC.majorVersion
      } yield version
      version.get must be(222)
    }

    "be updated with optIn event information" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      private val emailId = GenerateRandom.email()

      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

      preferencesBuilder
        .acceptGenericTermsPendingVerification(
          entityId,
          emailId,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
      eventually {
        val result = preferencesTestRoutes.get(`/preferences-admin/events/:entityId`(entityId: EntityId))
        val events = result.json.toString
        events must include("opt-in")
      }
    }

    "be updated with OptOut event information" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()

      withEntity(entityId.toString, Option(nino.toString()))

      preferencesTestRoutes
        .post(
          `/preferences/:entityId/optout`(entityId),
          readFromResource("optOutGenericPayload.json"),
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .status must be(CREATED)

      eventually {
        val result = preferencesTestRoutes.get(`/preferences-admin/events/:entityId`(entityId: EntityId))
        val events = result.json.toString
        events must include("opt-out")
      }
    }
  }
}
