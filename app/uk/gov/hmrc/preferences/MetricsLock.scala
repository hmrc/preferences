/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences

import uk.gov.hmrc.mongo.lock.MongoLockRepository

import java.time.Duration

final case class MetricsLock(lockId: String, holdLockFor: Duration, repo: MongoLockRepository)
