/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.ligthbend.akka.cluster.java.sample.singleton;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.cluster.singleton.ClusterSingletonProxy;
import akka.cluster.singleton.ClusterSingletonProxySettings;
import com.ligthbend.akka.cluster.java.sample.LogAllTheThingsActor;
import com.ligthbend.akka.cluster.java.sample.SampleHelpers;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.time.Duration;

import static com.ligthbend.akka.cluster.java.sample.SampleHelpers.portConfig;

public class SingletonSample {

  private static final String SINGLETON_MANAGER_NAME = "logging-singleton-manager";

  private static void startSingletonManager(ActorSystem node) {
    Props singletonManagerProps = ClusterSingletonManager.props(
        LogAllTheThingsActor.props(),
        PoisonPill.getInstance(),
        ClusterSingletonManagerSettings.create(node)
    );
    node.actorOf(singletonManagerProps, SINGLETON_MANAGER_NAME);
  }

  private static ActorRef createProxy(ActorSystem node) {
    return node.actorOf(
        ClusterSingletonProxy.props(
            "/user/" + SINGLETON_MANAGER_NAME,
            ClusterSingletonProxySettings.create(node)
        ),
        "logging-singleton-proxy"
    );

  }

  public static void main(String[] args) throws Exception {
    final Config commonConfig = ConfigFactory.load("java-cluster-with-seeds");

    final ActorSystem node1 = ActorSystem.create("cluster", portConfig(25520).withFallback(commonConfig));
    final ActorSystem node2 = ActorSystem.create("cluster", portConfig(25521).withFallback(commonConfig));
    final ActorSystem node3 = ActorSystem.create("cluster", portConfig(25522).withFallback(commonConfig));

    // one manager per singleton and node needs to be running
    startSingletonManager(node1);
    startSingletonManager(node2);
    startSingletonManager(node3);

    // all communication to the singleton must be done through the singleton proxy
    final ActorRef node1Proxy = createProxy(node1);
    final ActorRef node2Proxy = createProxy(node2);
    final ActorRef node3Proxy = createProxy(node3);


    // sample depends on cluster being formed, so wait for that before moving on
    // this is only to keep the sample brief and easy to follow
    SampleHelpers.waitForAllNodesUp(node1, node2, node3);


    for (int n = 1; n < 6; n++) {
      node1Proxy.tell("Message " + n + " from node1", ActorRef.noSender());
      node2Proxy.tell("Message " + n + " from node2", ActorRef.noSender());
      node3Proxy.tell("Message " + n + " from node3", ActorRef.noSender());

      SampleHelpers.wait(Duration.ofSeconds(1));
    }

    // singleton runs on the oldest node, if that node dies it is started
    // on the new oldest node
    Cluster.get(node1).leave(Cluster.get(node1).selfAddress());
    // give it some time to leave before we try sending messages again
    SampleHelpers.wait(Duration.ofSeconds(1));


    for (int n = 1; n < 6; n++) {
      node2Proxy.tell("Message " + n + " from node2 after node1 stopped", ActorRef.noSender());
      node3Proxy.tell("Message " + n + " from node3 after node1 stopped", ActorRef.noSender());

      SampleHelpers.wait(Duration.ofSeconds(1));
    }

    // Some more things to try out:
    // actor state is lost on node crash, to have the new node reach the same state on startup
    // there basically are two options - use a persistent actor or use ddata to gossip state to all nodes
    // and query that on singleton startup

  }
}
