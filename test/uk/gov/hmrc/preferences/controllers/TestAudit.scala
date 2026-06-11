/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers

import java.util.concurrent.ConcurrentLinkedQueue
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit.Body
import uk.gov.hmrc.play.audit.model.{ Audit, AuditAsMagnet, DataEvent }

import scala.concurrent.ExecutionContext

class TestAudit(auditConnector: AuditConnector) extends Audit("preferences", auditConnector) {
  var capturedTxName: String = ""
  var capturedInputs: Map[String, String] = Map.empty
  private val dataEvents = new ConcurrentLinkedQueue[DataEvent]

  override def as[A](
    auditMagnet: AuditAsMagnet[A]
  )(body: Body[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): A = {
    this.capturedTxName = auditMagnet.txName
    this.capturedInputs = auditMagnet.inputs
    super.as(auditMagnet)(body)
  }

  def capturedDataEvents: Seq[DataEvent] = dataEvents.toArray(new Array[DataEvent](0)).toSeq

  def captureDataEvent(event: DataEvent) = {
    this.dataEvents.add(event)
    ()
  }

  override def sendDataEvent(de: DataEvent)(implicit ec: ExecutionContext): Unit =
    captureDataEvent(de)
}
