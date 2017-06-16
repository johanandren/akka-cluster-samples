/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.typesafe.com>
 */
package com.lightbend.akka.sample

import akka.actor.{Actor, ActorLogging, Props}


object LogAllTheThingsActor {
  def props(messageTransformer: Option[PartialFunction[Any, String]] = None) =
    Props(new LogAllTheThingsActor(messageTransformer))

}

class LogAllTheThingsActor(messageTransformer: Option[PartialFunction[Any, String]]) extends Actor with ActorLogging {
  override def receive: Receive = {
    case message =>
      val logEntry = messageTransformer.flatMap(transformer =>
        transformer.lift(message)
      ).getOrElse(s"Saw message: [$message]")

      log.info(logEntry)
  }
}

