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

package conf

import com.google.inject.Inject
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.libs.ws.{ WSClient, WSRequest }

class EntityResolverService @Inject() (wsClient: WSClient) extends PlaySpec with ScalaFutures {

  val name: String = ExternalServiceNames.EntityResolver

  def `/test-only/entity-resolver-admin/sa/:utr`(utr: String, port: Int): WSRequest =
    wsClient.url(s"http://localhost:$port/test-only/entity-resolver-admin/sa/$utr")

  def `/test-only/entity-resolver-admin/paye/:nino`(nino: String): WSRequest =
    wsClient.url(s"http://localhost:8015/test-only/entity-resolver-admin/paye/$nino")

}
