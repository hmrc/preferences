/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

import conf.PreferencesTestRoutes._
import conf._
import play.api.http.Status._
import uk.gov.hmrc.preferences.model.EntityId
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import utils.GenerateRandom

class ChangeEmailNoService extends ISpec with EntityResolverSupport {

  "changing email address when email service is unavailable" should {
    "save the pending email regardless" in new ISpecTestCase {
      val entityId: EntityId = GenerateRandom.entityId()
      val emailAddress: String = GenerateRandom.email()
      val newEmailAddress: String = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

      preferencesBuilder
        .withEntityId(entityId)
        .thenAcceptGenericTermsAndConditions(emailAddress, CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))
        .thenChangeEmailAddress(newEmailAddress)

      (preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json \ "email" \ "email")
        .as[String] mustBe newEmailAddress
    }
  }

  override val cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]

}
