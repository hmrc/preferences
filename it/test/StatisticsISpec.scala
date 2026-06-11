/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

import conf.PreferencesTestRoutes._
import conf._
import org.scalatest.concurrent.Eventually
import play.api.http.Status._
import uk.gov.hmrc.preferences.model.{ DatedCount, EntityId }
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import utils.GenerateRandom

import java.time.LocalDate
import scala.concurrent.duration._
import scala.language.postfixOps

class StatisticsISpec extends ISpec with Tardis with Eventually with EntityResolverSupport {

  override def beforeEach(): Unit = {
    super.beforeEach()
    val preferencesBuilder = app.injector.instanceOf[PreferencesBuilder]
    val authHelper: ItAuthHelper = app.injector.instanceOf[ItAuthHelper]

    val entityId1 = EntityId("1")
    withEntity(entityId1.toString, Option(nino.toString()), Option(utr.value))
    val entityId2 = EntityId("2")
    withEntity(entityId2.toString, Option(nino.toString()), Option(utr.value))
    val entityId3 = EntityId("3")
    withEntity(entityId3.toString, Option(nino.toString()), Option(utr.value))
    val entityId4 = EntityId("4")
    withEntity(entityId4.toString, Option(nino.toString()), Option(utr.value))

    atTime(daysAgo(30)) {
      preferencesBuilder
        .withEntityId(entityId1)
        .thenAcceptGenericTermsAndConditions(
          GenerateRandom.email(),
          CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )

      preferencesBuilder
        .withEntityId(entityId2)
        .thenAcceptGenericTermsAndConditions(
          GenerateRandom.email(),
          CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .thenVerifyEmail()
    }
    atTime(daysAgo(10)) {
      preferencesBuilder
        .withEntityId(entityId3)
        .thenAcceptGenericTermsAndConditions(
          GenerateRandom.email(),
          CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
    }
    val _ = atTime(daysAgo(3)) {
      preferencesBuilder
        .withEntityId(entityId4)
        .thenAcceptGenericTermsAndConditions(
          GenerateRandom.email(),
          CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
    }
  }

  "Requesting user's print suppression status" should {
    "return current suppression status counts" in new ISpecTestCase {

      private val entityId5 = EntityId("5")
      withEntity(entityId5.toString, Option(nino.toString()), Option(utr.value))
      private val entityId6 = EntityId("6")
      withEntity(entityId6.toString, Option(nino.toString()), Option(utr.value))
      private val entityId7 = EntityId("7")
      withEntity(entityId7.toString, Option(nino.toString()), Option(utr.value))
      private val entityId8 = EntityId("8")
      withEntity(entityId8.toString, Option(nino.toString()), Option(utr.value))
      private val entityId9 = EntityId("9")
      withEntity(entityId9.toString, Option(nino.toString()), Option(utr.value))
      private val entityId10 = EntityId("10")
      withEntity(entityId10.toString, Option(nino.toString()), Option(utr.value))
      private val entityId11 = EntityId("11")
      withEntity(entityId11.toString, Option(nino.toString()), Option(utr.value))
      private val entityId12 = EntityId("12")
      withEntity(entityId12.toString, Option(nino.toString()), Option(utr.value))
      private val entityId13 = EntityId("13")
      withEntity(entityId13.toString, Option(nino.toString()), Option(utr.value))

      preferencesBuilder
        .withEntityId(entityId5)
        .thenAcceptGenericTermsAndConditions(
          GenerateRandom.email(),
          CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .thenBounceEmail()

      preferencesBuilder
        .withEntityId(entityId6)
        .thenAcceptGenericTermsAndConditions(
          GenerateRandom.email(),
          CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )

      preferencesBuilder
        .withEntityId(entityId7)
        .thenAcceptGenericTermsAndConditions(
          GenerateRandom.email(),
          CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .thenVerifyEmail()

      preferencesBuilder
        .withEntityId(entityId8)
        .thenAcceptGenericTermsAndConditions(
          GenerateRandom.email(),
          CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .thenVerifyEmail()
        .thenChangeEmailAddress(GenerateRandom.email())

      preferencesBuilder
        .withEntityId(entityId9)
        .thenAcceptGenericTermsAndConditions(
          GenerateRandom.email(),
          CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .thenVerifyEmail()
        .thenBounceEmail()

      preferencesBuilder
        .withEntityId(entityId10)
        .thenAcceptGenericTermsAndConditions(
          GenerateRandom.email(),
          CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .thenVerifyEmail()
        .thenBounceEmail()
        .thenChangeEmailAddress(GenerateRandom.email())

      preferencesBuilder
        .withEntityId(entityId11)
        .thenAcceptGenericTermsAndConditions(
          GenerateRandom.email(),
          CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .thenVerifyEmail()
        .thenChangeEmailAddress(GenerateRandom.email())
        .thenBounceEmail()

      preferencesBuilder
        .withEntityId(entityId12)
        .thenAcceptGenericTermsAndConditions(
          GenerateRandom.email(),
          CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .thenVerifyEmail()
        .thenBounceEmail()
        .thenChangeEmailAddress(GenerateRandom.email())
        .thenBounceEmail()

      private val entityIdRandom = EntityId("1111")
      withEntity(entityIdRandom.toString, Option(nino.toString()), Option(utr.value))

      preferencesBuilder
        .withEntityId(entityIdRandom)
        .thenAcceptGenericTermsAndConditions(
          GenerateRandom.email(),
          CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .thenVerifyEmail()

      preferencesBuilder
        .withEntityId(entityId13)
        .thenDeclineGenericTermsAndConditions(CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))

      preferencesTestRoutes.post(`/preferences/stats`)

      eventually(timeout(2 seconds), interval(200 milliseconds)) {
        val computedCounts = preferencesTestRoutes.get(`/preferences/stats`).json.as[Map[String, DatedCount]]
        computedCounts.values.headOption.value.date.toString must be(LocalDate.now().toString)
        val computedStats = computedCounts.view.mapValues(_.count).toMap
        computedStats("generic.optedOut") mustBe 1
        computedStats("generic.optedInAndVerified") mustBe 8
        computedStats("generic.optedIn") mustBe 13
      }
    }
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]
}
