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
