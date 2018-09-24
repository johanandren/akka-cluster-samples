/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.ligthbend.akka.cluster.java.sample.sharding;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;

/**
 * An actor that accepts any kind of message. When it gets a message it increases a counter
 * and logs the counter and the message.
 */
public final class CountingActor extends AbstractLoggingActor {

  public static Props props() {
    return Props.create(CountingActor.class, CountingActor::new);
  }

  private int counter = 0;

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(Object.class, this::onMessage)
        .build();
  }

  private void onMessage(Object message) {
    counter += 1;
    log().info("Got message nr {}: {}", counter, message);
  }

}
