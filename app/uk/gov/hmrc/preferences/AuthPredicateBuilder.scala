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

import play.api.Configuration
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.{ ConfidenceLevel, Enrolment }

import scala.util.Try

case class AuthPredicateBuilder(controllerName: String, controllerConfigs: Option[Configuration]) {

  import AuthPredicateBuilder._

  def confidenceLevel(config: Configuration): Option[ConfidenceLevel] =
    Try(config.get[Int]("confidenceLevel")).toOption.flatMap(ConfidenceLevel.fromInt(_).toOption)

  def resolveControllerConfigs: ThrowsAuthError[Configuration] =
    controllerConfigs.toRight(NoControllersConfigDefined())

  private lazy val globalConfidenceLevel: ThrowsAuthError[ConfidenceLevel] =
    resolveControllerConfigs.flatMap(confidence)

  def confidence(config: Configuration): ThrowsAuthError[ConfidenceLevel] =
    confidenceLevel(config) match {
      case Some(c) => Right(c)
      case None    => globalConfidenceLevel
    }

  def enrolment(config: Configuration): ThrowsAuthError[Enrolment] =
    Try(config.get[String]("enrolment")).toOption
      .map(t => Enrolment(t))
      .toRight(NoEnrolmentDefined(controllerName))

  def authPredicate(config: Configuration): ThrowsAuthError[Predicate] =
    for {
      enrolment  <- enrolment(config)
      confidence <- confidence(config)
    } yield enrolment and confidence

  def authConfig: ThrowsAuthError[Predicate] =
    for {
      config <- resolveControllerConfigs
      authParams <- config
                      .getOptional[Configuration](s"$controllerName.authParams")
                      .toRight(AuthParamsNotDefined(controllerName))
      predicates <- authPredicate(authParams)
    } yield predicates
}

object AuthPredicateBuilder {

  type ThrowsAuthError[A] = Either[AuthError, A]

  sealed trait AuthError {
    val msg: String
  }

  final case class NoEnrolmentDefined(controllerName: String) extends AuthError {
    val msg: String = s"No enrolment defined for controller: $controllerName, which needs authentication"
  }

  final case class AuthParamsNotDefined(controllerName: String) extends AuthError {
    val msg: String = s"No authParams defined for controller: $controllerName, which needs authentication"
  }

  final case class NoControllersConfigDefined(msg: String = "") extends AuthError

}
