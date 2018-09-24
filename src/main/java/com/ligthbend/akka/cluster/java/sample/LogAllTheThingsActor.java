/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.ligthbend.akka.cluster.java.sample;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;

public final class LogAllTheThingsActor extends AbstractLoggingActor {

  public static Props props() {
    return Props.create(LogAllTheThingsActor.class, LogAllTheThingsActor::new);
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(Object.class, message -> log().info("Saw message {}", message))
        .build();
  }
}
