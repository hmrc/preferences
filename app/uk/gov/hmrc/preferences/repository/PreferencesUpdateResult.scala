/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.repository

sealed trait PreferenceUpdateResult
object NewPreferenceCreated extends PreferenceUpdateResult
object NoEmailForPreference extends PreferenceUpdateResult
object PreferenceUpdated extends PreferenceUpdateResult
object PreferenceMatched extends PreferenceUpdateResult
object PreferenceNotMatched extends PreferenceUpdateResult
object LanguageNotUpdated extends PreferenceUpdateResult
object NoTermsAndConditions extends PreferenceUpdateResult
object InvalidTermsAncConditions extends PreferenceUpdateResult
object ErrorResult extends PreferenceUpdateResult
