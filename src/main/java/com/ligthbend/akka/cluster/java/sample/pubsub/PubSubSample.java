/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.ligthbend.akka.cluster.java.sample.pubsub;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import com.ligthbend.akka.cluster.java.sample.LogAllTheThingsActor;
import com.ligthbend.akka.cluster.java.sample.SampleHelpers;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.time.Duration;

import static com.ligthbend.akka.cluster.java.sample.SampleHelpers.portConfig;

public class PubSubSample {

  public static void main(String[] args) throws Exception {
    final Config commonConfig = ConfigFactory.load("java-cluster-with-seeds");

    final ActorSystem node1 = ActorSystem.create("cluster", portConfig(25520).withFallback(commonConfig));
    final ActorSystem node2 = ActorSystem.create("cluster", portConfig(25521).withFallback(commonConfig));
    final ActorSystem node3 = ActorSystem.create("cluster", portConfig(25522).withFallback(commonConfig));

    // interaction with distributed pub sub is done through a local mediator
    final ActorRef node1Mediator = DistributedPubSub.get(node1).mediator();
    final ActorRef node2Mediator = DistributedPubSub.get(node2).mediator();
    final ActorRef node3Mediator = DistributedPubSub.get(node3).mediator();

    // there is no delivery guarantees with distributed pub-sub, if we start publishing
    // before a cluster node has successfully joined the cluster, those message end up in
    // the dead letters queue
    SampleHelpers.waitForAllNodesUp(node1, node2, node3);


    // an actor either subscribes to a topic
    ActorRef node1subscriber = node1.actorOf(LogAllTheThingsActor.props(), "node1-subscriber");
    node1Mediator.tell(new DistributedPubSubMediator.Subscribe("my-topic", node1subscriber), ActorRef.noSender());

    ActorRef node2subscriber = node2.actorOf(LogAllTheThingsActor.props(), "node2-subscriber");
    node2Mediator.tell(new DistributedPubSubMediator.Subscribe("my-topic", node2subscriber), ActorRef.noSender());


    // or registers that it is a available at a specific path using "Put"
    ActorRef node1registered = node1.actorOf(LogAllTheThingsActor.props(), "registered-path");
    node1Mediator.tell(new DistributedPubSubMediator.Put(node1registered), ActorRef.noSender());

    // that a node has subscriptions for a topic or registered actors for a path
    // is gossiped between nodes - this means it is eventually consistent - if the subscription/registration
    // has not reached a node when a message is published/sent it may be lost.

    for (int n = 1; n < 11; n++) {
      // other nodes can then publish to the topic
      node3Mediator.tell(
          new DistributedPubSubMediator.Publish("my-topic", "message " + n + " to topic [my-topic] from node 3"),
          ActorRef.noSender());

      // or send to one of the available actors registered at a path
      node3Mediator.tell(
          new DistributedPubSubMediator.Send(
            "/user/registered-path",
            "message to path [registered-path] from node 3",
            false // should a local actor be preferred?
          ),
          ActorRef.noSender());


      SampleHelpers.wait(Duration.ofSeconds(1));
    }

    // Some more things to try out:
    // subscriptions are dynamic, so a subscribed actor can unsubscribe and a registered ("Put") actor can remove itself
    // with a registered actor the message ends up at one of the actors, but you can send ot all using SendToAll

 }
}
