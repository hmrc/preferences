/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package utils

import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.{ SimpleName, TaxIdentifier }
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.model.{ EmailAddress, EmailVerificationLink, EntityId, PendingEmailAddress, Preferences, TaxId, TermsAndConditions, UserType }
import uk.gov.hmrc.preferences.util.Dc

import java.time.{ Duration, Instant, LocalDate }

object TestData {
  val TEST_EMAIL = "test@test.com"
  val TEST_URI = "test_uri"
  val TEST_STATUS = "verified"
  val TEST_MSG = "test_message"
  val TEST_IDENTIFIER = "test_12345"
  val TEST_TITLE = "test_title"
  val TEST_TEMPLATE_ID = "test_template"
  val TEST_SUBJECT = "test_subject"
  val TEST_BODY = "test_body"

  val TEST_ID = "test_id"
  val TEST_SOURCE = "test_source"
  val TEST_REGIME = "test_regime"
  val TEST_FORM_TYPE = "test_form_type"
  val TEST_ENROLMENT = "test_enrolment"

  val TEST_FORENAME = "test_forename"
  val TEST_SUR_NAME = "test_last_name"
  val TEST_SECOND_NAME = "test_second_name"
  val TEST_HONOURS = "test_honours"
  val TEST_LINE_1 = "test_line1"
  val TEST_LINE_2 = "test_line2"
  val TEST_LINE_3 = "test_line3"
  val TEST_TOKEN = "test_token"
  val TEST_LINK = "test_link"

  val TEST_EMAIL_ADDRESS: EmailAddress = EmailAddress(TEST_EMAIL)
  val TEST_TO_ADRESS = "test_to_adress"
  val TEST_EMAIL_VERIFICATION_LINK: EmailVerificationLink =
    EmailVerificationLink(linkSentTime = Instant.now.minus(Duration.ofDays(1)))

  val TEST_TERMS_AND_CONDITIONS: TermsAndConditions = TermsAndConditions(Accepted(Dc.instantNow()))

  val TEST_YEAR = 2025
  val TEST_MONTH_12 = 12
  val TEST_DAY_10 = 10

  val TEST_LOCAL_DATE: LocalDate = LocalDate.of(TEST_YEAR, TEST_MONTH_12, TEST_DAY_10)

  val FIVE = 5
  val TWO = 2
  val FOURTEEN = 14
  val TEST_CODE = 156

  val THREE_HUNDRED_THOUSAND = 300000
  val ONE_HUNDRED_TWENTY_THOUSAND = 120000
  val HUNDRED = 100
  val FIVE_THOUSAND = 5000

  private val TEST_EPOCH_SECONDS = 67813456
  val TEST_TIME_INSTANT: Instant = Instant.ofEpochSecond(TEST_EPOCH_SECONDS)

  private val TEST_ENTITY_ID_VALUE = "test_id"
  val TEST_ENTITY_ID: EntityId = EntityId(TEST_ENTITY_ID_VALUE)

  val TEST_TAX_IDENTIFIER: TaxIdentifier & SimpleName = new TaxIdentifier with SimpleName {
    override val name: String = "test_name"
    override def value: String = "test_value"
  }

  val TEST_SAUTR = "2000029888"
  val TEST_NINO = "AB112233A"
  val TEST_ITSA_ID = "test-itsa-id"

  val TEST_PREFERENCES: Preferences = Preferences(
    entityId = TEST_ENTITY_ID,
    termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow())),
    pendingEmail =
      Some(PendingEmailAddress(email = "test@mail.com", verificationLink = Some(TEST_EMAIL_VERIFICATION_LINK))),
    userType = Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))
  )

  val TEST_TAX_ID: TaxId = TaxId(_id = TEST_ID, sautr = Some("2000029888"), nino = Some("YY000200A"))

  val TEST_ERROR_MESSAGE = "Error occurred"
  val TEST_HTTP_NOT_FOUND_EXCEPTION = new NotFoundException(TEST_ERROR_MESSAGE)
}
