/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package utils

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{ Level, Logger => LogbackLogger }
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

trait LogCapturing {

  import scala.jdk.CollectionConverters._
  import scala.reflect._

  def withCaptureOfLoggingFrom[T: ClassTag](body: (=> List[ILoggingEvent]) => Any): Any = {
    val logger = LoggerFactory.getLogger(classTag[T].runtimeClass).asInstanceOf[LogbackLogger]
    withCaptureOfLoggingFrom(logger)(body)
  }

  def withCaptureOfLoggingFrom(logger: LogbackLogger)(body: (=> List[ILoggingEvent]) => Any): Any = {
    val appender = new ListAppender[ILoggingEvent]()
    appender.setContext(logger.getLoggerContext)
    appender.start()
    logger.addAppender(appender)
    logger.setLevel(Level.TRACE)
    logger.setAdditive(true)
    body(appender.list.asScala.toList)
  }

}
