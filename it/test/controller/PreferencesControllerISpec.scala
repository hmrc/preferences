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

package controller

import conf.PreferencesTestRoutes.*
import conf.{ CleanMongoCollection, ISpec }
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.libs.json.Json.*
import play.api.libs.json.{ Format, JsValue, Json }
import play.api.mvc.Result
import play.api.test.Helpers.*
import play.api.test.{ FakeRequest, Helpers }
import uk.gov.hmrc.crypto.{ Encrypter, PlainText }
import uk.gov.hmrc.mongo.test.{ CleanMongoCollectionSupport, MongoSupport }
import uk.gov.hmrc.paperless.controllers.model.PreferenceResponse
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.model.SurveyType.StandardInterruptOptOut
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import uk.gov.hmrc.preferences.util.Dc
import utils.GenerateRandom

import java.io.File
import java.time.Instant
import scala.concurrent.Future
import scala.io.Source

class PreferencesControllerISpec
    extends ISpec with MongoSupport with CleanMongoCollectionSupport with EntityResolverSupport {

  private val email = GenerateRandom.email()
  private val resourcePath = sys.props.getOrElse("RESOURCE_PATH", "./test/resources")

  override def beforeEach(): Unit = {
    prepareDatabase()
    super.beforeEach()
  }

  def readFromResource(file: String): JsValue = {
    val resource = Source.fromFile(new File(s"$resourcePath/$file")).getLines().mkString("\n")
    Json.parse(resource.replace("test@test.com", email))
  }

  "get enrolment status" should {

    "return ok and preference for enrolled and pending verification" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      private val email = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .acceptGenericTermsPendingVerification(entityId, email, Some(authHelper.authHeader(nino, ggAuthPort)))

      private val response = preferencesTestRoutes.get(`/preferences/:entityId`(entityId))
      response.status mustBe OK

      private val preference = response.json.as[PreferenceResponse]
      preference.digital mustBe true
      preference.termsAndConditions("generic").accepted mustBe true
      private val emailPreference = preference.email.get
      emailPreference.email mustBe email
      emailPreference.isVerified mustBe false
    }

    "return not found when the entityId has no preferences" in new ISpecTestCase {
      preferencesTestRoutes.get(`/preferences/:entityId`(GenerateRandom.entityId())).status mustBe 404
    }
  }

  "update preference" should {
    "overwrite the existing verified email with the new one which is pending verification" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      private val (oldEmail, newEmail) = (GenerateRandom.email(), GenerateRandom.email())
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      private val prefBuilder = preferencesBuilder
        .acceptGenericTermsAndVerifyEmail(entityId, oldEmail, Some(authHelper.authHeader(nino, ggAuthPort)))

      private val preference = preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json.as[PreferenceResponse]
      private val emailPreference = preference.email.get
      emailPreference.email mustBe oldEmail
      emailPreference.isVerified mustBe true

      prefBuilder.thenChangeEmailAddress(newEmail)
      private val newEmailPreference =
        preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json.as[PreferenceResponse].email.get
      newEmailPreference.email mustBe newEmail
      newEmailPreference.isVerified mustBe false
    }

    "return 400 with invalid payload" in new ISpecTestCase {
      private val invalidPreferences = toJson(Map[String, String]())
      preferencesTestRoutes
        .put(`/preferences/:entityId/pending-email`(GenerateRandom.entityId()), invalidPreferences)
        .status must be(BAD_REQUEST)
    }

    "return ok when opting out for a new user" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .withEntityId(entityId)
        .thenDeclineGenericTermsAndConditions(
          shouldReturnStatus = CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )

      private val response = preferencesTestRoutes.get(`/preferences/:entityId`(entityId))
      response.status mustBe 200
      private val preference = response.json.as[PreferenceResponse]
      preference.digital mustBe false
      preference.termsAndConditions("generic").accepted mustBe false
    }

    "return ok when opting out for a new user with survey" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .withEntityId(entityId)
        .thenDeclineGenericTermsAndConditionsWithSurvey(
          shouldReturnStatus = CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )

      private val response = preferencesTestRoutes.get(`/preferences/:entityId`(entityId))
      response.status mustBe 200
      private val preference = response.json.as[PreferenceResponse]
      preference.digital mustBe false
      preference.termsAndConditions("generic").accepted mustBe false
      preference.surveys.get.size mustBe 1
      preference.surveys.get(0).surveyType mustBe StandardInterruptOptOut
    }
  }

  "Getting a preference" should {

    "return 200 if the user is found" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      private val emailAddress = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .acceptGenericTermsAndVerifyEmail(entityId, emailAddress, Some(authHelper.authHeader(nino, ggAuthPort)))

      preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).status must be(OK)
    }

    "return 200 if the user is found in auth" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      private val emailAddress = GenerateRandom.email()

      // insert entity for testing
      withEntity(entityId.value, Option(nino.toString()), Option(utr.value))
      val authHeader = authHelper.authHeader(nino, ggAuthPort)
      preferencesBuilder
        .acceptGenericTermsAndVerifyEmail(entityId, emailAddress, Some(authHelper.authHeader(nino, ggAuthPort)))

      private val response = preferencesTestRoutes.get("/preferences", headers = Some(authHeader))
      response.status must be(OK)
      val preferenceResponse: PreferenceResponse = response.json.as[PreferenceResponse]
      preferenceResponse.entityId.isDefined mustBe true
    }

    "return status section in response if the user is found in auth" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      private val emailAddress = GenerateRandom.email()

      // insert entity for testing
      withEntity(entityId.value, Option(nino.toString()), Option(utr.value))
      val authHeader = authHelper.authHeader(nino, ggAuthPort)
      preferencesBuilder
        .acceptGenericTermsAndVerifyEmail(entityId, emailAddress, Some(authHelper.authHeader(nino, ggAuthPort)))

      private val result = preferencesTestRoutes.get("/preferences", headers = Some(authHeader))
      result.status must be(OK)
      private val preferenceResponse = result.json.as[PreferenceResponse]
      preferenceResponse.status.isDefined mustBe true
      preferenceResponse.entityId.isDefined mustBe true
    }

    "return 401 if no bearer token supplied" in new ISpecTestCase {
      preferencesTestRoutes.get("/preferences", headers = None).status must be(UNAUTHORIZED)
    }

    "return 404 if the user is not found via auth" in new ISpecTestCase {
      // NOTE, no inserted entity
      val authHeader = authHelper.authHeader(nino, ggAuthPort)
      val response = preferencesTestRoutes.get("/preferences", headers = Some(authHeader))
      response.status must be(NOT_FOUND)
      response.responseString must be("Entity not found")
    }

    "return 404 if entity is found, but no entityId matches in preferences" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.value, Option(nino.toString()), Option(utr.value))
      val authHeader = authHelper.authHeader(nino, ggAuthPort)
      // NOTE: no inserted preference
      val response = preferencesTestRoutes.get("/preferences", headers = Some(authHeader))
      response.status must be(NOT_FOUND)
      response.responseString must be("Preference not found")
    }

    "return 400 if taxId is invalid" in new ISpecTestCase {
      val response = preferencesTestRoutes.get("/preferences?taxRegime=nino&taxId=SA112233")
      response.status must be(BAD_REQUEST)
    }

    "return 400 if query string contains an invalid key" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.value, Option(nino.toString()), Option(utr.value))
      val authHeader = authHelper.authHeader(nino, ggAuthPort)
      val response = preferencesTestRoutes.get("/preferences?whatever=false", headers = Some(authHeader))
      response.status must be(BAD_REQUEST)
    }

    "return 200 if the user is found in auth, bypassing resolution" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      private val emailAddress = GenerateRandom.email()

      // insert entity for testing
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      val authHeader = authHelper.authHeader(nino, ggAuthPort)
      preferencesBuilder
        .acceptGenericTermsAndVerifyEmail(entityId, emailAddress, Some(authHelper.authHeader(nino, ggAuthPort)))

      private val result = preferencesTestRoutes.get("/preferences?resolve=false", headers = Some(authHeader))
      result.status must be(OK)
      private val preferenceResponse = result.json.as[PreferenceResponse]
      preferenceResponse.entityId.isDefined mustBe true
    }

    "return 404 if the user is not found" in new ISpecTestCase {
      preferencesTestRoutes.get(`/preferences/:entityId`(GenerateRandom.entityId())).status must be(NOT_FOUND)
    }
  }

  "Getting an email's language" should {
    "return 200 and language" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      private val emailId = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      val encrypter = app.injector.instanceOf[Encrypter]
      val encryptedEmail: String = new String(encrypter.encrypt(PlainText(emailId)).toBase64)
      preferencesBuilder
        .acceptGenericTermsAndVerifyEmail(entityId, emailId, Some(authHelper.authHeader(nino, ggAuthPort)))
      private val result = preferencesTestRoutes.get(`/preferences/language/:emailId`(encryptedEmail))
      result.status must be(OK)
      result.json.as[Language] must be(Language.Welsh)
    }
  }

  "Preference" should {

    "setPendingEmail for unverified user and preserve Language" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      private val emailId = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .acceptGenericTermsPendingVerification(
          entityId,
          emailId,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )

      preferencesTestRoutes.put(
        `/preferences/:entityId/pending-email`(entityId: EntityId),
        Json.parse("""{"email": "testtest@test.com"}""")
      )
      private val result = preferencesTestRoutes.get(`/preferences/:entityId`(entityId))

      result.json.as[PreferenceResponse].email.get.language.get must be(Language.Welsh)
    }
  }

  "updated preference" should {

    "return 404 if the entity id does not exist" in new ISpecTestCase {
      preferencesTestRoutes.put(`/preferences/:entityId/updated`(GenerateRandom.entityId())).status mustBe NOT_FOUND
    }
  }

  "mark preference for de-enrolment" should {

    "succeed with ok if not already marked" in new ISpecTestCase {
      createPreferencesUnmarked(entityId, Option(nino.value), Option(utr.value))
      val request = FakeRequest(PUT, s"/preferences/mark-for-de-enrolment?taxRegime=paye&taxId=${nino.value}")
      val result: Future[Result] = route(app, request).get
      Helpers.status(result) mustBe OK

      val p = preferencesRepository.findBy(entityId).futureValue
      p.get.markForDeEnrolment.isDefined mustBe true
    }

    "succeed with no_content if already marked" in new ISpecTestCase {
      createPreferencesMarked(entityId, Option(nino.value), Option(utr.value))
      val request = FakeRequest(PUT, s"/preferences/mark-for-de-enrolment?taxRegime=paye&taxId=${nino.value}")
      val result: Future[Result] = route(app, request).get

      Helpers.status(result) mustBe NO_CONTENT
      val p = preferencesRepository.findBy(entityId).futureValue
      p.get.markForDeEnrolment.isDefined mustBe true
    }

    "fail if entity not found" in new ISpecTestCase {
      createPreferencesUnmarked(entityId, Option(nino.value), Option(utr.value), false)
      val request = FakeRequest(PUT, s"/preferences/mark-for-de-enrolment?taxRegime=paye&taxId=${nino.value}")
      val result: Future[Result] = route(app, request).get
      Helpers.status(result) mustBe NOT_FOUND
      contentAsString(result) mustBe "Entity not found"

      val p = preferencesRepository.findBy(entityId).futureValue
      p.get.markForDeEnrolment.isDefined mustBe false
    }
  }

  "unset de-enrolment" should {

    "succeed with ok if not already unmarked" in new ISpecTestCase {
      createPreferencesMarked(entityId, Option(nino.value), Option(utr.value))
      val request = FakeRequest(PUT, s"/preferences/unset-de-enrolment?taxRegime=paye&taxId=${nino.value}")
      val result: Future[Result] = route(app, request).get
      Helpers.status(result) mustBe OK

      val p = preferencesRepository.findBy(entityId).futureValue
      p.get.markForDeEnrolment.isDefined mustBe false
    }

    "succeed with no_content if already unmarked" in new ISpecTestCase {
      createPreferencesUnmarked(entityId, Option(nino.value), Option(utr.value))
      val request = FakeRequest(PUT, s"/preferences/unset-de-enrolment?taxRegime=paye&taxId=${nino.value}")
      val result: Future[Result] = route(app, request).get
      Helpers.status(result) mustBe NO_CONTENT

      val p = preferencesRepository.findBy(entityId).futureValue
      p.get.markForDeEnrolment.isDefined mustBe false
    }

    "fail if entity not found" in new ISpecTestCase {
      createPreferencesMarked(entityId, Option(nino.value), Option(utr.value), false)
      val request = FakeRequest(PUT, s"/preferences/unset-de-enrolment?taxRegime=paye&taxId=${nino.value}")
      val result: Future[Result] = route(app, request).get
      Helpers.status(result) mustBe NOT_FOUND
      contentAsString(result) mustBe "Entity not found"

      val p = preferencesRepository.findBy(entityId).futureValue
      p.get.markForDeEnrolment.isDefined mustBe true
    }
  }

  def createPreferencesMarked(
    entityId: EntityId,
    nino: Option[String],
    sautr: Option[String],
    addEntity: Boolean = true
  ) = {
    val p = Preferences(
      entityId = entityId,
      termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow())),
      markForDeEnrolment = Some(MarkForDeEnrolment(time = Instant.now, identifier = "whatever"))
    )
    create(p, entityId, nino, sautr, addEntity)
  }

  def createPreferencesUnmarked(
    entityId: EntityId,
    nino: Option[String],
    sautr: Option[String],
    addEntity: Boolean = true
  ) = {
    val p = Preferences(
      entityId = entityId,
      termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow()))
    )
    create(p, entityId, nino, sautr, addEntity)
  }

  def create(
    p: Preferences,
    entityId: EntityId,
    nino: Option[String],
    sautr: Option[String],
    addEntity: Boolean = true
  ): Preferences = {
    implicit val pformat: Format[Preferences] = Preferences.formats

    val result = mongoDatabase
      .getCollection("saIndividualPreferences")
      .insertOne(Document(pformat.writes(p).toString()))
      .toFuture()
      .futureValue

    result.wasAcknowledged() mustBe true
    result.getInsertedId.isNull mustBe false

    if (addEntity) {
      val sb: StringBuilder = new StringBuilder
      sb ++= s"""{"_id" : "$entityId""""
      if (nino.isDefined) sb ++= s""", "nino" : "${nino.get}" """
      if (sautr.isDefined) sb ++= s""", "sautr": "${sautr.get}" """
      sb ++= s"}"

      // add data to a different db, since its running in another service
      val item = mongoClient
        .getDatabase("entity-resolver")
        .getCollection("entity")
        .insertOne(Document(sb.mkString))
        .toFuture()
        .futureValue
      item.wasAcknowledged() must be(true)
    }
    p
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]
}
