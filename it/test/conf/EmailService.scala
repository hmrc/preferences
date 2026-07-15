/*
 * Copyright 2020 HM Revenue & Customs
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
