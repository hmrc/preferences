/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import cats.data.EitherT
import com.google.inject.Singleton
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.exceptions.{ PreferenceInternalServerError, PreferenceNotFound }
import uk.gov.hmrc.preferences.model.{ EntityId, Preferences }
import uk.gov.hmrc.preferences.repository.PreferencesRepository

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

@Singleton
class PreferenceService @Inject() (
  preferencesRepository: PreferencesRepository
)(implicit executionContext: ExecutionContext)
    extends Logging {

  def getPreferencesByEntityId(
    entityId: EntityId
  )(implicit hc: HeaderCarrier): EitherT[Future, Throwable, Preferences] =
    EitherT(
      preferencesRepository
        .findBy(entityId)
        .transform {
          case Success(Some(value)) => Success(Right(value))
          case Success(None)        => Success(Left(PreferenceNotFound()))
          case Failure(ex) =>
            logger.error(s"Error retrieving preferences for entityId: ${entityId.value}", ex)
            Success(Left(ex))
        }
        .recover { case ex =>
          logger.error(s"Unexpected error while finding preference with entityId: ${entityId.value}", ex)
          Left(PreferenceInternalServerError(ex.getMessage))
        }
    )

}
