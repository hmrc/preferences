/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package utils

import uk.gov.hmrc.crypto.{ Crypted, Decrypter, PlainBytes, PlainContent, PlainText }

object FakeApplicationCrypto extends Decrypter {
  override def decrypt(crypted: Crypted): PlainText = PlainText(crypted.value)
  override def decryptAsBytes(reversiblyEncrypted: Crypted): PlainBytes = PlainBytes(reversiblyEncrypted.value.getBytes)
}
