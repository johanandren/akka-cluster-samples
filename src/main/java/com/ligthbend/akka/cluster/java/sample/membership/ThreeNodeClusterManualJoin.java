/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.ligthbend.akka.cluster.java.sample.membership;

import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class ThreeNodeClusterManualJoin {

  public static void main(String[] args) {
    final Config commonConfig = ConfigFactory.load("java-cluster-with-manual-join");

    final ActorSystem node1 = ActorSystem.create("cluster", commonConfig);
    final ActorSystem node2 = ActorSystem.create("cluster", commonConfig);
    final ActorSystem node3 = ActorSystem.create("cluster", commonConfig);

    // joins itself to form cluster
    final Cluster node1Cluster = Cluster.get(node1);
    node1Cluster.join(node1Cluster.selfAddress());

    // joins the cluster through the one node in the cluster
    final Cluster node2Cluster = Cluster.get(node2);
    node2Cluster.join(node1Cluster.selfAddress());

    // subsequent nodes can join through any node that is already in the cluster
    final Cluster node3Cluster = Cluster.get(node3);
    node3Cluster.join(node2Cluster.selfAddress());

  }
}
