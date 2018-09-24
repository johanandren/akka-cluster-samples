/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.ligthbend.akka.cluster.java.sample.membership;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import com.ligthbend.akka.cluster.java.sample.LogAllTheThingsActor;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import static com.ligthbend.akka.cluster.java.sample.SampleHelpers.portConfig;

public class ClusterEvents {

  public static void main(String[] args) {
    final Config commonConfig = ConfigFactory.load("java-cluster-with-seeds");

    final ActorSystem node1 = ActorSystem.create("cluster", portConfig(25520).withFallback(commonConfig));
    final ActorRef loggingActor = node1.actorOf(LogAllTheThingsActor.props(), "logging-actor");
    Cluster.get(node1).subscribe(loggingActor, ClusterEvent.MemberEvent.class);

    final ActorSystem node2 = ActorSystem.create("cluster", portConfig(25521).withFallback(commonConfig));
    final ActorSystem node3 = ActorSystem.create("cluster", portConfig(25522).withFallback(commonConfig));

  }
}
