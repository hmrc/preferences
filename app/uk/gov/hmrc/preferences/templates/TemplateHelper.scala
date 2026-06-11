/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.templates

import javax.inject.{ Inject, Singleton }
import play.api.Configuration
import uk.gov.hmrc.preferences.model.TaxpayerName

import java.util.Base64

@Singleton
class TemplateHelper @Inject() (configuration: Configuration) {

  lazy val btaHost = s"${configuration.get[String]("business-account.host")}"
  lazy val btaMessagesUrl = s"$btaHost/business-account/messages"
  lazy val ptaHost = s"${configuration.get[String]("personal-account.host")}"
  lazy val ptaMessagesUrl = s"$ptaHost/personal-account/messages"
  lazy val preferencesFrontendUrl = s"${configuration.get[String]("taxPlatformSaPrefsRootUri")}"

  def getMessageContent(): String =
    encodeToBase64String(
      html.digitalOptinConfirmation().body
    )

  def getWelshMessageContent(): String =
    encodeToBase64String(
      html.digitalOptinConfirmation_cy().body
    )

  def getSalutation(taxpayerName: Option[TaxpayerName]): String = {

    val defaultName = DefaultName.DefaultEnglishName
    val salutation = Salutation.EnglishSalutation

    taxpayerName match {
      case Some(name) =>
        (name.title, name.surname) match {
          case (Some(title), Some(surname)) =>
            s"$salutation ${title.toLowerCase.capitalize} ${surname.toLowerCase.capitalize}"
          case _ => s"$salutation $defaultName"
        }
      case _ => s"$salutation $defaultName"
    }
  }

  private def encodeToBase64String(text: String): String =
    Base64.getEncoder.encodeToString(text.getBytes("UTF-8"))
}

object Salutation {
  val EnglishSalutation = "Dear"
}

object DefaultName {
  val DefaultEnglishName = "Customer"
}
