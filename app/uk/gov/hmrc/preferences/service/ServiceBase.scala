/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import uk.gov.hmrc.preferences.model.{ Event, Preferences }

trait ServiceBase {
  def getAllEvents(prefs: Preferences, event: Option[Event]): Option[List[Event]] = {
    val existingEvents = prefs.events.getOrElse(List.empty[Event])
    event match {
      case Some(e) => Some(e :: existingEvents)
      case _       => Some(existingEvents)
    }
  }

}
