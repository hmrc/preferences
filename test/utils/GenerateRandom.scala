/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package utils

import java.util.UUID
import uk.gov.hmrc.domain.{ Nino, NinoGenerator, SaUtr, SaUtrGenerator }
import uk.gov.hmrc.preferences.model.EntityId

object GenerateRandom {
  private val saUtrGenerator: SaUtrGenerator = SaUtrGenerator()
  private val ninoGenerator: NinoGenerator = NinoGenerator()

  def email(): String = s"${UUID.randomUUID()}@TEST.com"

  def utr(): SaUtr = saUtrGenerator.nextSaUtr

  def gatewayId(): String = UUID.randomUUID.toString

  def idaPid(): String = UUID.randomUUID.toString

  def nino(): Nino = ninoGenerator.nextNino

  def entityId(): EntityId = EntityId(UUID.randomUUID().toString)
}
