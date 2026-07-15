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

package uk.gov.hmrc.preferences.filters

import org.apache.pekko.stream.Materializer

import javax.inject.Inject
import play.api.Configuration
import play.api.mvc.{ Filter, RequestHeader, Result, Results }
import play.api.routing.Router
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.{ AuthConnector, AuthorisationException, AuthorisedFunctions }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.preferences.AuthPredicateBuilder
import uk.gov.hmrc.preferences.AuthPredicateBuilder.ThrowsAuthError

import scala.concurrent.{ ExecutionContext, Future }

// $COVERAGE-OFF$
class AuthFilter @Inject() (configuration: Configuration, val authConnector: AuthConnector)(implicit
  override val mat: Materializer,
  ec: ExecutionContext
) extends Filter with AuthorisedFunctions {

  lazy val controllerConfigs: Option[Configuration] = configuration.getOptional[Configuration]("controllers")

  def controllerNeedsAuth(controllerName: String): Option[Boolean] =
    controllerConfigs
      .flatMap(_.getOptional[Configuration](controllerName))
      .flatMap(c => c.getOptional[Boolean]("needsAuth"))

  def authConfigFromHeader(rh: RequestHeader): Option[ThrowsAuthError[Predicate]] =
    rh.attrs
      .get(Router.Attrs.HandlerDef)
      .map(_.controller)
      .flatMap(name =>
        controllerNeedsAuth(name).filter(_ == true).map(_ => AuthPredicateBuilder(name, controllerConfigs).authConfig)
      )

  override def apply(next: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(rh)
    authConfigFromHeader(rh) match {
      case Some(Right(predicate)) =>
        authorised(predicate) {
          next(rh)
        } recover { case a: AuthorisationException => Results.Unauthorized(a.getMessage) }
      case Some(Left(err)) => Future.successful(Results.Unauthorized(err.msg))
      case None            => next(rh)
    }
  }

}
// $COVERAGE-ON$
