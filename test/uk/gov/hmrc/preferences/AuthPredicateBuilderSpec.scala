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

package uk.gov.hmrc.preferences

import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.{ Application, Configuration, inject }
import uk.gov.hmrc.auth.core.{ ConfidenceLevel, Enrolment }
import uk.gov.hmrc.auth.core.authorise.*
import uk.gov.hmrc.preferences.AuthPredicateBuilder.{ AuthParamsNotDefined, NoControllersConfigDefined, NoEnrolmentDefined }
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.crypto.{ Decrypter, Encrypter }
import utils.FakeApplicationCrypto
import play.api.inject.bind

class AuthPredicateBuilderSpec extends PlaySpec with GuiceOneAppPerTest with MockitoSugar {

  "confidenceLevel" should {
    "successfully get the confidence level when a valid value is defined" in {
      val validConfidenceLevel = 50
      val configuration = Configuration.from(Map("confidenceLevel" -> validConfidenceLevel))
      val authPredicateBuilder = AuthPredicateBuilder("someName", None)
      authPredicateBuilder.confidenceLevel(configuration).map(_.level) mustBe Some(validConfidenceLevel)
    }

    "throw an error when the confidenceLevel is invalid" in {
      val invalidConfidenceLevel = 3
      val configuration = Configuration.from(Map("confidenceLevel" -> invalidConfidenceLevel))
      val authPredicateBuilder = AuthPredicateBuilder("someName", None)
      a[NoSuchElementException] must be thrownBy {
        authPredicateBuilder.confidenceLevel(configuration)
      }
    }

    "return None when no confidenceLevel is defined" in {
      val configuration = Configuration.from(Map())
      val authPredicateBuilder = AuthPredicateBuilder("someName", None)

      authPredicateBuilder.confidenceLevel(configuration) mustBe None

    }
  }

  "confidence" should {
    "successfully get the confidence level when a valid value is defined" in {
      val validConfidenceLevel = 50
      val configuration = Configuration.from(Map("confidenceLevel" -> validConfidenceLevel))
      val authPredicateBuilder = AuthPredicateBuilder("someName", None)
      authPredicateBuilder.confidence(configuration).map(_.level) mustBe Right(validConfidenceLevel)
    }

    "throw an error when the confidenceLevel is invalid" in {
      val invalidConfidenceLevel = 3
      val configuration = Configuration.from(Map("confidenceLevel" -> invalidConfidenceLevel))
      val authPredicateBuilder = AuthPredicateBuilder("someName", None)

      a[NoSuchElementException] must be thrownBy {
        authPredicateBuilder.confidenceLevel(configuration)
      }

    }

    "return None when no confidenceLevel is defined" in {
      val configuration = Configuration.from(Map())
      val authPredicateBuilder = AuthPredicateBuilder("someName", None)

      authPredicateBuilder.confidence(configuration) mustBe Left(NoControllersConfigDefined())

    }
  }

  "enrolment" should {
    "successfully get the enrolment when a valid value is defined" in {
      val enrolment = "enrolment"
      val configuration = Configuration.from(Map("enrolment" -> enrolment))
      val authPredicateBuilder = AuthPredicateBuilder("someName", None)
      authPredicateBuilder.enrolment(configuration) mustBe Right(Enrolment("enrolment"))
    }

    "return Left(NoEnrolmentDefined()) when no enrolment is defined" in {
      val configuration = Configuration.from(Map())
      val authPredicateBuilder = AuthPredicateBuilder("someName", None)

      authPredicateBuilder.enrolment(configuration) mustBe Left(NoEnrolmentDefined("someName"))
    }
  }

  "auth predicate" should {
    "authorise predicate" in {
      val configuration = Configuration.from(Map("enrolment" -> "enrolment", "confidenceLevel" -> 50))
      val authPredicateBuilder = AuthPredicateBuilder("someName", None)
      val answer = authPredicateBuilder.authPredicate(configuration)
      answer must be(Right(CompositePredicate(Enrolment("enrolment", List(), "Activated", None), ConfidenceLevel.L50)))
    }
  }

  "auth config" should {
    "cause error when authorise without config" in {
      val authPredicateBuilder = AuthPredicateBuilder("someName", None)
      val answer = authPredicateBuilder.authConfig
      answer must be(Left(NoControllersConfigDefined()))
    }

    "cause error when config not specified" in {
      val configuration = Configuration.from(
        Map(
          "someName.authParams" ->
            Map("enrolment" -> "EN", "confidenceLevel" -> 200)
        )
      )
      val authPredicateBuilder = AuthPredicateBuilder("someWrongName", Some(configuration))
      val answer = authPredicateBuilder.authConfig
      answer must be(Left(AuthParamsNotDefined("someWrongName")))
    }

    "create a predicated when authorised with config" in {
      val configuration = Configuration.from(
        Map(
          "someName.authParams" ->
            Map("enrolment" -> "EN", "confidenceLevel" -> 200)
        )
      )
      val authPredicateBuilder = AuthPredicateBuilder("someName", Some(configuration))
      val answer = authPredicateBuilder.authConfig
      answer must be(Right(CompositePredicate(Enrolment("EN", List(), "Activated", None), ConfidenceLevel.L200)))
    }
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure("metrics.jvm" -> false)
    .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
    .build()
}
