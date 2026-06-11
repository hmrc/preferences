/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

import conf.PreferencesTestRoutes.`/preferences-admin/events/:entityId`
import conf.{ CleanMongoCollection, ISpec }
import play.api.http.Status.CREATED
import uk.gov.hmrc.preferences.model.PageType.IPage
import uk.gov.hmrc.preferences.model.{ EmailEventType, EntityId, OptInPage, Version }
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import utils.GenerateRandom

class EmailEventsISpec extends ISpec with EntityResolverSupport {
  "be updated with email verification event information and receive email verification event" in new ISpecTestCase {
    private val entityId = GenerateRandom.entityId()
    private val emailId = GenerateRandom.email()
    withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
    preferencesBuilder
      .acceptGenericTermsAndVerifyEmail(
        entityId,
        emailId,
        Some(authHelper.authHeader(nino, ggAuthPort))
      )
    eventually {
      val result = preferencesTestRoutes.get(`/preferences-admin/events/:entityId`(entityId: EntityId))
      val events = result.json.toString
      events must include(EmailEventType.EmailVerified.entryName)
      events must include("opt-in")
    }
  }

  "be updated with opt-in event information and receive email opt-in event" in new ISpecTestCase {
    private val entityId = GenerateRandom.entityId()
    private val emailId = GenerateRandom.email()
    withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

    preferencesBuilder
      .Builder(entityId, Some(emailId), None)
      .thenAcceptGenericTermsAndConditions(
        emailId,
        CREATED,
        Some(authHelper.authHeader(nino, ggAuthPort)),
        OptInPage(Version(2, 1), 1, IPage)
      )

    eventually {
      val result = preferencesTestRoutes.get(`/preferences-admin/events/:entityId`(entityId: EntityId))
      val events = result.json.toString
      events must include("opt-in")

    }
  }

  "be updated with opt-out event information and receive email opt-out event" in new ISpecTestCase {
    private val entityId = GenerateRandom.entityId()
    private val emailId = GenerateRandom.email()
    withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

    preferencesBuilder
      .Builder(entityId, Some(emailId), None)
      .thenDeclineGenericTermsAndConditions(CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))

    eventually {
      val result = preferencesTestRoutes.get(`/preferences-admin/events/:entityId`(entityId: EntityId))
      val events = result.json.toString
      events must include("opt-out")
    }
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]
}
