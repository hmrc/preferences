/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package conf

import javax.inject.{ Inject, Singleton }
import org.scalatestplus.play.PlaySpec
import play.api.libs.ws.{ WSClient, WSRequest }

@Singleton
class EmailService @Inject() (ws: WSClient) extends PlaySpec {
  val name: String = ExternalServiceNames.Email

  def `test-only/hmrc/email-admin/process-email-queue`: WSRequest =
    ws.url("http://localhost:8300/test-only/hmrc/email-admin/process-email-queue")

  def `test-only/hmrc/email-admin/coordinate-bounce-queue`: WSRequest =
    ws.url("http://localhost:8300/test-only/hmrc/email-admin/coordinate-bounce-queue")

}
