/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.config

import play.api.Configuration
import uk.gov.hmrc.crypto.{ Decrypter, SymmetricCryptoFactory }

import javax.inject.{ Inject, Provider, Singleton }

@Singleton
class CryptoProvider @Inject() (configuration: Configuration) extends Provider[Decrypter] {
  override def get(): Decrypter =
    SymmetricCryptoFactory.aesCryptoFromConfig(baseConfigKey = "queryParameter.encryption", configuration.underlying)
}
