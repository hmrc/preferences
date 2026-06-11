/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.filters

import javax.inject.{ Inject, Singleton }
import play.api.http.DefaultHttpFilters

@Singleton
class PreferencesFilter @Inject() (authFilter: AuthFilter) extends DefaultHttpFilters(authFilter)
