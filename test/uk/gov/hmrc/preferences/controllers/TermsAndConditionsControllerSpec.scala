/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers

import cats.data.EitherT
import cats.instances.future.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.{ any, eq as eqTo }
import org.mockito.Mockito.{ times, verify, when }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsDefined, JsString, Json }
import play.api.mvc.Result
import play.api.test.Helpers.*
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{ Name, Retrieval, ~ }
import uk.gov.hmrc.auth.core.{ AffinityGroup, AuthConnector, ConfidenceLevel, MissingBearerToken }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.TaxIdParams
import uk.gov.hmrc.preferences.connector.EntityResolverConnector
import uk.gov.hmrc.preferences.controllers.model.{ Credentials, TermsAndConditionsRequest }
import uk.gov.hmrc.preferences.exceptions.{ DoNotProcess, EntityBadRequest, EntityNotFound, EntityRequestServerError, EntityResolverResponse, EntityUnauthorised, PreferenceNotFound }
import uk.gov.hmrc.preferences.model.EntityId
import uk.gov.hmrc.preferences.model.Language.English
import uk.gov.hmrc.preferences.repository.{ InvalidTermsAncConditions, LanguageNotUpdated, NewPreferenceCreated, NoEmailForPreference, PreferenceUpdated }
import uk.gov.hmrc.preferences.service.{ PreferenceService, TermsAndConditionsService }
import uk.gov.hmrc.preferences.util.Dc

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import utils.TestData.TEST_ERROR_MESSAGE

class TermsAndConditionsControllerSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  val retrievalResult: Future[Option[AffinityGroup.Individual.type] ~ ConfidenceLevel] =
    Future.successful(
      new ~(Some(AffinityGroup.Individual), ConfidenceLevel.L200)
    )

  "Store a generic termsAndConditions" should {

    "return a OK when a customer has manually opted-out" in new TestCase {
      when(
        mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](
          any[Predicate],
          any[Retrieval[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel]]
        )(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(retrievalResult)
      when(
        mocktermsAndConditionsService.handleTermsAndConditionsRequest(
          eqTo(entityId),
          any[TermsAndConditionsRequest],
          any[Option[Credentials]]
        )(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      private val manualOptOutRequest = Json.parse(
        s"""
           |{
           |   "generic" :
           |    {
           |      "accepted": false ,
           |      "manualOptOut": true
           |    }
           |}
         """.stripMargin
      )

      private val fakeRequest = FakeRequest(
        "POST",
        routes.TermsAndConditionsController.store(entityId).url,
        FakeHeaders(),
        Json.toJson(manualOptOutRequest)
      )
      private val response = testController.store(entityId)(fakeRequest)
      status(response) mustBe OK
    }

    "create a preference and return CREATED when user opts-in with an email address" in new TestCase {
      when(
        mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](
          any[Predicate],
          any[Retrieval[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel]]
        )(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(retrievalResult)
      when(
        mocktermsAndConditionsService.handleTermsAndConditionsRequest(
          eqTo(entityId),
          any[TermsAndConditionsRequest],
          any[Option[Credentials]]
        )(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(NewPreferenceCreated))
      private val optInRequest = Json.parse(
        s"""
           |{
           |   "generic" :
           |    {
           |      "accepted": true
           |    },
           |    "email":"john.smith@hmrc.gov.uk"
           |}
         """.stripMargin
      )

      private val fakeRequest = FakeRequest(
        Helpers.POST,
        routes.TermsAndConditionsController.store(entityId).url,
        FakeHeaders(),
        Json.toJson(optInRequest)
      )
      private val response = testController.store(entityId)(fakeRequest)

      // verify(mocktermsAndConditionsService).handleTermsAndConditionsRequest(eqTo(entityId), any, any)(any)
      status(response) mustBe CREATED
    }

    "create a preference and return CREATED when user opts-in, email, return link text and return url are provided" in new TestCase {
      when(
        mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](
          any[Predicate],
          any[Retrieval[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel]]
        )(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(retrievalResult)
      when(
        mocktermsAndConditionsService.handleTermsAndConditionsRequest(
          eqTo(entityId),
          any[TermsAndConditionsRequest],
          any[Option[Credentials]]
        )(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(NewPreferenceCreated))
      private val optInRequest = Json.parse(
        s"""
           |{
           |   "generic" :
           |    {
           |      "accepted": true
           |    },
           |    "returnUrl":"Return Url",
           |    "returnText":"Return Text",
           |    "email":"john.smith@hmrc.gov.uk"
           |}
         """.stripMargin
      )

      private val fakeRequest = FakeRequest(
        Helpers.POST,
        routes.TermsAndConditionsController.store(entityId).url,
        FakeHeaders(),
        Json.toJson(optInRequest)
      )
      private val response = testController.store(entityId)(fakeRequest)

      status(response) mustBe CREATED
    }

    "update a preference (email) or opt-in an opted-out user and return a OK" in new TestCase {
      when(
        mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](
          any[Predicate],
          any[Retrieval[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel]]
        )(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(retrievalResult)
      when(
        mocktermsAndConditionsService.handleTermsAndConditionsRequest(
          eqTo(entityId),
          any[TermsAndConditionsRequest],
          any[Option[Credentials]]
        )(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      private val optInRequest = Json.parse(
        s"""
           |{
           |   "generic" :
           |    {
           |      "accepted": true
           |    },
           |    "email":"john.smith@hmrc.gov.uk"
           |}
         """.stripMargin
      )

      private val fakeRequest = FakeRequest(
        Helpers.POST,
        routes.TermsAndConditionsController.store(entityId).url,
        FakeHeaders(),
        Json.toJson(optInRequest)
      )
      private val response = testController.store(entityId)(fakeRequest)

      status(response) mustBe OK
    }

    "update just the user Language when no generic terms and conditions are not provided" in new TestCase {
      when(
        mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](
          any[Predicate],
          any[Retrieval[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel]]
        )(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(retrievalResult)
      when(
        mocktermsAndConditionsService.handleTermsAndConditionsRequest(
          eqTo(entityId),
          any[TermsAndConditionsRequest],
          any[Option[Credentials]]
        )(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      private val updateLanguagePayload = Json.parse(
        s"""
           |{
           |   "language" : "cy"
           |}
         """.stripMargin
      )

      private val fakeRequest = FakeRequest(
        "POST",
        routes.TermsAndConditionsController.store(entityId).url,
        FakeHeaders(),
        Json.toJson(updateLanguagePayload)
      )
      private val response = testController.store(entityId)(fakeRequest)
      status(response) mustBe OK

    }
  }

  "admin user" should {
    val manualOptOutRequest = Json.parse(
      s"""
         |{
         |   "generic" :
         |    {
         |      "accepted": false ,
         |      "manualOptOut": true
         |    }
         |}
       """.stripMargin
    )

    "be able to optout user from digital for generic" in new TestCase {
      when(
        mocktermsAndConditionsService.handleTermsAndConditionsRequest(
          any[EntityId],
          any[TermsAndConditionsRequest],
          any[Option[Credentials]]
        )(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(PreferenceUpdated))

      private val fakeRequest = FakeRequest(
        Helpers.POST,
        routes.TermsAndConditionsController.optOut(entityId).url,
        FakeHeaders(),
        Json.toJson(manualOptOutRequest)
      )
      private val response = testController.optOut(entityId)(fakeRequest)

      verify(mocktermsAndConditionsService, times(1))
        .handleTermsAndConditionsRequest(eqTo(entityId), any[TermsAndConditionsRequest], any[Option[Credentials]])(
          any[HeaderCarrier]
        )
      status(response) mustBe OK
    }
  }

  "Update email address" should {
    "successfully update email address if changed whilst opting-in" in new TestCase {
      when(
        mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](
          any[Predicate],
          any[Retrieval[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel]]
        )(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(retrievalResult)
      when(
        mocktermsAndConditionsService.handleTermsAndConditionsRequest(
          eqTo(entityId),
          any[TermsAndConditionsRequest],
          any[Option[Credentials]]
        )(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      private val optInRequest = Json.parse(
        s"""
           |{
           |   "generic" :
           |    {
           |      "accepted": true
           |    },
           |    "email":"john.smith+123456@hmrc.gov.uk"
           |}
         """.stripMargin
      )

      private val fakeRequest = FakeRequest(
        Helpers.POST,
        routes.TermsAndConditionsController.store(entityId).url,
        FakeHeaders(),
        Json.toJson(optInRequest)
      )
      private val response = testController.store(entityId)(fakeRequest)
      status(response) mustBe OK
    }

    "fail with no email provided" in new TestCase {
      when(
        mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](
          any[Predicate],
          any[Retrieval[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel]]
        )(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(retrievalResult)
      when(
        mocktermsAndConditionsService.handleTermsAndConditionsRequest(
          eqTo(entityId),
          any[TermsAndConditionsRequest],
          any[Option[Credentials]]
        )(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(NoEmailForPreference))
      private val optInRequest = Json.parse(
        s"""
           |{
           |   "generic" :
           |    {
           |      "accepted": true
           |    },
           |    "email":"john.smith+123456@hmrc.gov.uk"
           |}
         """.stripMargin
      )

      private val fakeRequest = FakeRequest(
        Helpers.POST,
        routes.TermsAndConditionsController.store(entityId).url,
        FakeHeaders(),
        Json.toJson(optInRequest)
      )
      private val response = testController.store(entityId)(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsJson(response) \ "reason" must be(
        JsDefined(JsString("No email provided for user opting in for paperless"))
      )
    }

    "fail with language not updated" in new TestCase {
      when(
        mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](
          any[Predicate],
          any[Retrieval[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel]]
        )(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(retrievalResult)
      when(
        mocktermsAndConditionsService.handleTermsAndConditionsRequest(
          eqTo(entityId),
          any[TermsAndConditionsRequest],
          any[Option[Credentials]]
        )(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(LanguageNotUpdated))
      private val optInRequest = Json.parse(
        s"""
           |{
           |   "generic" :
           |    {
           |      "accepted": true
           |    },
           |    "email":"john.smith+123456@hmrc.gov.uk"
           |}
         """.stripMargin
      )

      private val fakeRequest = FakeRequest(
        Helpers.POST,
        routes.TermsAndConditionsController.store(entityId).url,
        FakeHeaders(),
        Json.toJson(optInRequest)
      )
      private val response = testController.store(entityId)(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsJson(response) \ "reason" must be(JsDefined(JsString("Unable to update language")))
    }

    "fail with invalid terms and conditions" in new TestCase {
      when(
        mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](
          any[Predicate],
          any[Retrieval[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel]]
        )(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(retrievalResult)
      when(
        mocktermsAndConditionsService.handleTermsAndConditionsRequest(
          eqTo(entityId),
          any[TermsAndConditionsRequest],
          any[Option[Credentials]]
        )(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(InvalidTermsAncConditions))
      private val optInRequest = Json.parse(
        s"""
           |{
           |   "generic" :
           |    {
           |      "accepted": true
           |    },
           |    "email":"john.smith+123456@hmrc.gov.uk"
           |}
         """.stripMargin
      )

      private val fakeRequest = FakeRequest(
        Helpers.POST,
        routes.TermsAndConditionsController.store(entityId).url,
        FakeHeaders(),
        Json.toJson(optInRequest)
      )
      private val response = testController.store(entityId)(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsJson(response) \ "reason" must be(JsDefined(JsString("Invalid terms and conditions type")))
    }
  }

  "new direct routes" should {

    "change email language" in new TestCase {
      when(mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](any, any)(any, any))
        .thenReturn(retrievalResult)

      when(mockEntityResolverConnector.getEntityIdByAuth(any, any)(any))
        .thenReturn(EitherT.rightT(entityId))

      when(mocktermsAndConditionsService.handleTermsAndConditionsRequest(eqTo(entityId), any, any)(any))
        .thenReturn(Future.successful(PreferenceUpdated))

      private val changeEmailLanguageRequest = Json.parse(s"""{ "language":"en" }""")

      private val fakeRequest = FakeRequest(Helpers.POST, "", FakeHeaders(), Json.toJson(changeEmailLanguageRequest))
      private val response = testController.updatePreferences()(fakeRequest).futureValue

      response.header.status mustBe OK

      verify(mockEntityResolverConnector).getEntityIdByAuth(eqTo(Some(true)), eqTo(Some(true)))(any)
      verify(mocktermsAndConditionsService).handleTermsAndConditionsRequest(
        eqTo(entityId),
        eqTo(TermsAndConditionsRequest(None, None, None, None, language = Some(English))),
        any
      )(any)
    }

    "change regime email language for itsa specific journeys" in new TestCase {
      when(mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](any, any)(any, any))
        .thenReturn(retrievalResult)

      when(mockEntityResolverConnector.getEntityIdByAuthWithRegime(any, any)(any))
        .thenReturn(EitherT.rightT(entityId))

      when(mocktermsAndConditionsService.handleTermsAndConditionsRequest(eqTo(entityId), any, any)(any))
        .thenReturn(Future.successful(PreferenceUpdated))

      private val changeEmailLanguageRequest = Json.parse(s"""{ "language":"en" }""")

      private val fakeRequest = FakeRequest(Helpers.POST, "", FakeHeaders(), Json.toJson(changeEmailLanguageRequest))
      private val response = testController.updatePreferencesWithRegime()(fakeRequest).futureValue

      response.header.status mustBe OK

      verify(mockEntityResolverConnector).getEntityIdByAuthWithRegime(eqTo(Some(true)), eqTo(Some(true)))(any)
      verify(mocktermsAndConditionsService).handleTermsAndConditionsRequest(
        eqTo(entityId),
        eqTo(TermsAndConditionsRequest(None, None, None, None, language = Some(English))),
        any
      )(any)
    }
  }

  "updatePreferencesWithRegime" should {
    "return BadRequest if IllegalArgumentException error occurs while authorization" in new TestCase {
      when(mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](any, any)(any, any))
        .thenReturn(Future.failed(new IllegalArgumentException()))

      private val changeEmailLanguageRequest = Json.parse(s"""{ "language":"en" }""")

      private val fakeRequest = FakeRequest(Helpers.POST, "", FakeHeaders(), Json.toJson(changeEmailLanguageRequest))
      private val response = testController.updatePreferencesWithRegime()(fakeRequest).futureValue

      response.header.status mustBe BAD_REQUEST
    }

    "return Unauthorized if MissingBearerToken exception occurs while authorization" in new TestCase {
      when(mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](any, any)(any, any))
        .thenReturn(Future.failed(MissingBearerToken()))

      private val changeEmailLanguageRequest = Json.parse(s"""{ "language":"en" }""")

      private val fakeRequest = FakeRequest(Helpers.POST, "", FakeHeaders(), Json.toJson(changeEmailLanguageRequest))
      private val response = testController.updatePreferencesWithRegime()(fakeRequest).futureValue

      response.header.status mustBe UNAUTHORIZED
    }

    "return NotFound if NotFoundException error occurs while updating the preferences" in new TestCase {
      when(mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](any, any)(any, any))
        .thenReturn(retrievalResult)

      when(mockEntityResolverConnector.getEntityIdByAuthWithRegime(any, any)(any))
        .thenReturn(EitherT.leftT[Future, EntityResolverResponse](EntityNotFound))

      private val changeEmailLanguageRequest = Json.parse(s"""{ "language":"en" }""")

      private val fakeRequest = FakeRequest(Helpers.POST, "", FakeHeaders(), Json.toJson(changeEmailLanguageRequest))
      private val response = testController.updatePreferencesWithRegime()(fakeRequest).futureValue

      response.header.status mustBe NOT_FOUND
    }

    "return InternalServerError if any runtime exception occurs while authorization" in new TestCase {
      when(mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](any, any)(any, any))
        .thenReturn(Future.failed(InternalError(TEST_ERROR_MESSAGE)))

      private val changeEmailLanguageRequest = Json.parse(s"""{ "language":"en" }""")

      private val fakeRequest = FakeRequest(Helpers.POST, "", FakeHeaders(), Json.toJson(changeEmailLanguageRequest))
      private val response = testController.updatePreferencesWithRegime()(fakeRequest).futureValue

      response.header.status mustBe INTERNAL_SERVER_ERROR
    }

    "return BadRequest when TermsAndConditions update returns NoEmailForPreference" in new TestCase {
      when(mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](any, any)(any, any))
        .thenReturn(retrievalResult)

      when(mockEntityResolverConnector.getEntityIdByAuthWithRegime(any, any)(any))
        .thenReturn(EitherT.rightT(entityId))

      when(mocktermsAndConditionsService.handleTermsAndConditionsRequest(eqTo(entityId), any, any)(any))
        .thenReturn(Future.successful(NoEmailForPreference))

      private val changeEmailLanguageRequest = Json.parse(s"""{ "language":"en" }""")

      private val fakeRequest = FakeRequest(Helpers.POST, "", FakeHeaders(), Json.toJson(changeEmailLanguageRequest))
      private val response = testController.updatePreferencesWithRegime()(fakeRequest).futureValue

      response.header.status mustBe BAD_REQUEST
    }

    "return BadRequest when TermsAndConditions update returns LanguageNotUpdated" in new TestCase {
      when(mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](any, any)(any, any))
        .thenReturn(retrievalResult)

      when(mockEntityResolverConnector.getEntityIdByAuthWithRegime(any, any)(any))
        .thenReturn(EitherT.rightT(entityId))

      when(mocktermsAndConditionsService.handleTermsAndConditionsRequest(eqTo(entityId), any, any)(any))
        .thenReturn(Future.successful(LanguageNotUpdated))

      private val changeEmailLanguageRequest = Json.parse(s"""{ "language":"en" }""")

      private val fakeRequest = FakeRequest(Helpers.POST, "", FakeHeaders(), Json.toJson(changeEmailLanguageRequest))
      private val response = testController.updatePreferencesWithRegime()(fakeRequest).futureValue

      response.header.status mustBe BAD_REQUEST
    }

    "return BadRequest when TermsAndConditions update returns InvalidTermsAncConditions" in new TestCase {
      when(mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](any, any)(any, any))
        .thenReturn(retrievalResult)

      when(mockEntityResolverConnector.getEntityIdByAuthWithRegime(any, any)(any))
        .thenReturn(EitherT.rightT(entityId))

      when(mocktermsAndConditionsService.handleTermsAndConditionsRequest(eqTo(entityId), any, any)(any))
        .thenReturn(Future.successful(InvalidTermsAncConditions))

      private val changeEmailLanguageRequest = Json.parse(s"""{ "language":"en" }""")

      private val fakeRequest = FakeRequest(Helpers.POST, "", FakeHeaders(), Json.toJson(changeEmailLanguageRequest))
      private val response = testController.updatePreferencesWithRegime()(fakeRequest).futureValue

      response.header.status mustBe BAD_REQUEST
    }

    "return BadRequest when preferences update returns EntityBadRequest" in new TestCase {
      when(mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](any, any)(any, any))
        .thenReturn(retrievalResult)

      when(mockEntityResolverConnector.getEntityIdByAuthWithRegime(any, any)(any))
        .thenReturn(EitherT.leftT(EntityBadRequest(TEST_ERROR_MESSAGE)))

      when(mocktermsAndConditionsService.handleTermsAndConditionsRequest(eqTo(entityId), any, any)(any))
        .thenReturn(Future.successful(PreferenceUpdated))

      private val changeEmailLanguageRequest = Json.parse(s"""{ "language":"en" }""")

      private val fakeRequest = FakeRequest(Helpers.POST, "", FakeHeaders(), Json.toJson(changeEmailLanguageRequest))
      private val response = testController.updatePreferencesWithRegime()(fakeRequest).futureValue

      response.header.status mustBe BAD_REQUEST
    }

    "return NotFound when preferences update returns PreferenceNotFound" in new TestCase {
      when(mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](any, any)(any, any))
        .thenReturn(retrievalResult)

      when(mockEntityResolverConnector.getEntityIdByAuthWithRegime(any, any)(any))
        .thenReturn(EitherT.leftT(PreferenceNotFound(TEST_ERROR_MESSAGE)))

      when(mocktermsAndConditionsService.handleTermsAndConditionsRequest(eqTo(entityId), any, any)(any))
        .thenReturn(Future.successful(PreferenceUpdated))

      private val changeEmailLanguageRequest = Json.parse(s"""{ "language":"en" }""")

      private val fakeRequest = FakeRequest(Helpers.POST, "", FakeHeaders(), Json.toJson(changeEmailLanguageRequest))
      private val response = testController.updatePreferencesWithRegime()(fakeRequest).futureValue

      response.header.status mustBe NOT_FOUND
    }

    "return NotFound when preferences update returns EntityNotFound" in new TestCase {
      when(mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](any, any)(any, any))
        .thenReturn(retrievalResult)

      when(mockEntityResolverConnector.getEntityIdByAuthWithRegime(any, any)(any))
        .thenReturn(EitherT.leftT(EntityNotFound))

      when(mocktermsAndConditionsService.handleTermsAndConditionsRequest(eqTo(entityId), any, any)(any))
        .thenReturn(Future.successful(PreferenceUpdated))

      private val changeEmailLanguageRequest = Json.parse(s"""{ "language":"en" }""")

      private val fakeRequest = FakeRequest(Helpers.POST, "", FakeHeaders(), Json.toJson(changeEmailLanguageRequest))
      private val response = testController.updatePreferencesWithRegime()(fakeRequest).futureValue

      response.header.status mustBe NOT_FOUND
    }

    "return Unauthorized when preferences update returns EntityUnauthorised" in new TestCase {
      when(mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](any, any)(any, any))
        .thenReturn(retrievalResult)

      when(mockEntityResolverConnector.getEntityIdByAuthWithRegime(any, any)(any))
        .thenReturn(EitherT.leftT(EntityUnauthorised(TEST_ERROR_MESSAGE)))

      when(mocktermsAndConditionsService.handleTermsAndConditionsRequest(eqTo(entityId), any, any)(any))
        .thenReturn(Future.successful(PreferenceUpdated))

      private val changeEmailLanguageRequest = Json.parse(s"""{ "language":"en" }""")

      private val fakeRequest = FakeRequest(Helpers.POST, "", FakeHeaders(), Json.toJson(changeEmailLanguageRequest))
      private val response: Result = await(testController.updatePreferencesWithRegime()(fakeRequest))

      response.header.status mustBe UNAUTHORIZED
    }

    "return InternalServerError when preferences update returns EntityRequestServerError" in new TestCase {
      when(mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](any, any)(any, any))
        .thenReturn(retrievalResult)

      when(mockEntityResolverConnector.getEntityIdByAuthWithRegime(any, any)(any))
        .thenReturn(EitherT.leftT(EntityRequestServerError(TEST_ERROR_MESSAGE)))

      when(mocktermsAndConditionsService.handleTermsAndConditionsRequest(eqTo(entityId), any, any)(any))
        .thenReturn(Future.successful(PreferenceUpdated))

      private val changeEmailLanguageRequest = Json.parse(s"""{ "language":"en" }""")

      private val fakeRequest = FakeRequest(Helpers.POST, "", FakeHeaders(), Json.toJson(changeEmailLanguageRequest))
      private val response = testController.updatePreferencesWithRegime()(fakeRequest).futureValue

      response.header.status mustBe INTERNAL_SERVER_ERROR
    }

    "return InternalServerError when preferences update returns DoNotProcess" in new TestCase {
      when(mockAuthConnector.authorise[Option[AffinityGroup] ~ Option[Name] ~ ConfidenceLevel](any, any)(any, any))
        .thenReturn(retrievalResult)

      when(mockEntityResolverConnector.getEntityIdByAuthWithRegime(any, any)(any))
        .thenReturn(EitherT.leftT(DoNotProcess))

      when(mocktermsAndConditionsService.handleTermsAndConditionsRequest(eqTo(entityId), any, any)(any))
        .thenReturn(Future.successful(PreferenceUpdated))

      private val changeEmailLanguageRequest = Json.parse(s"""{ "language":"en" }""")

      private val fakeRequest = FakeRequest(Helpers.POST, "", FakeHeaders(), Json.toJson(changeEmailLanguageRequest))
      private val response = testController.updatePreferencesWithRegime()(fakeRequest).futureValue

      response.header.status mustBe INTERNAL_SERVER_ERROR
    }
  }

  trait TestCase {

    /** * DO NOT DELETE THIS LINE **
      */
    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val entityId: EntityId = EntityId("123123123123123")

    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mocktermsAndConditionsService: TermsAndConditionsService = mock[TermsAndConditionsService]
    val mockPreferenceService: PreferenceService = mock[PreferenceService]
    val mockEntityResolverConnector: EntityResolverConnector = mock[EntityResolverConnector]

    val testController: TermsAndConditionsController = new TermsAndConditionsController(
      mocktermsAndConditionsService,
      mockEntityResolverConnector,
      Helpers.stubControllerComponents(),
      mockAuthConnector,
      () => Dc.instantNow()
    )

    val reason: Option[String] = Some(TermsAndConditionsRequest.ManualOptOut.reason)
    val dontSendEmail = false
    val sendEmail = true
  }

}
