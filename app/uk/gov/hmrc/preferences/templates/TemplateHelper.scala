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
