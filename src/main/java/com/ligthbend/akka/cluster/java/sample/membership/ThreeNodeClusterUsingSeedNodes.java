/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.ligthbend.akka.cluster.java.sample.membership;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import static com.ligthbend.akka.cluster.java.sample.SampleHelpers.portConfig;

public class ThreeNodeClusterUsingSeedNodes {

  public static void main(String[] args) {
    final Config commonConfig = ConfigFactory.load("java-cluster-with-seeds");

    final ActorSystem node1 = ActorSystem.create("cluster", portConfig(25520).withFallback(commonConfig));
    final ActorSystem node2 = ActorSystem.create("cluster", portConfig(25521).withFallback(commonConfig));
    final ActorSystem node3 = ActorSystem.create("cluster", portConfig(25522).withFallback(commonConfig));
  }
}
