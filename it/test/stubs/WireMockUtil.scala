/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package stubs

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, Suite }

trait WireMockUtil extends BeforeAndAfterAll with BeforeAndAfterEach {
  this: Suite =>

  lazy val wireMockServer = new WireMockServer(
    wireMockConfig()
      .dynamicHttpsPort()
      .dynamicPort()
      .notifier(new Slf4jNotifier(true))
  )

  override def beforeAll(): Unit = {
    super.beforeAll()
    wireMockServer.start()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    wireMockServer.resetMappings()
    wireMockServer.resetRequests()
  }
  override def afterAll(): Unit = {
    super.afterAll()
    wireMockServer.stop()
  }
}
