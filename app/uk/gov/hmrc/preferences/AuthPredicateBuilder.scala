/*
 * Copyright 2023 HM Revenue & Customs
 *
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
