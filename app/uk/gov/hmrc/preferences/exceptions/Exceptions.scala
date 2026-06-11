/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.exceptions

sealed trait PreferenceException extends Exception
case class PreferenceNotFound(message: String = "") extends Exception(message) with PreferenceException
case class PreferenceInternalServerError(message: String) extends Exception(message) with PreferenceException

sealed trait EntityResolverResponse extends Exception
case object UnsetMarkDeEnrolment extends EntityResolverResponse
case object DeletePreferences extends EntityResolverResponse
case object DoNotProcess extends EntityResolverResponse
case object InvalidEntity extends EntityResolverResponse
case object EntityProcessError extends EntityResolverResponse
case object EntityNotFound extends EntityResolverResponse
case object EntityResponseAuthFailed extends EntityResolverResponse
case class EntityBadRequest(message: String) extends Exception(message) with EntityResolverResponse
case class EntityUnauthorised(message: String) extends EntityResolverResponse
case class EntityRequestServerError(message: String) extends Exception(message) with EntityResolverResponse
