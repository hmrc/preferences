/*
 * Copyright 2020 HM Revenue & Customs
 *
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
