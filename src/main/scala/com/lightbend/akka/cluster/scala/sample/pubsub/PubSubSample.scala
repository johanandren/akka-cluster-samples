/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.typesafe.com>
 */
package com.lightbend.akka.cluster.scala.sample.pubsub

import akka.actor.ActorSystem
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import com.lightbend.akka.cluster.scala.sample.{LogAllTheThingsActor, SampleHelpers}
import com.lightbend.akka.cluster.scala.sample.LogAllTheThingsActor
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object PubSubSample extends App {
  val commonConfig = ConfigFactory.parseString(
    """
      akka {
        actor.provider = cluster
        remote.artery.enabled = true
        remote.artery.canonical.hostname = 127.0.0.1
        cluster.seed-nodes = [ "akka://cluster@127.0.0.1:25520", "akka://cluster@127.0.0.1:25521" ]
        cluster.jmx.multi-mbeans-in-same-jvm = on
      }
    """)

  def portConfig(port: Int) = ConfigFactory.parseString(s"akka.remote.artery.canonical.port = $port")

  val node1 = ActorSystem("cluster", portConfig(25520).withFallback(commonConfig))
  val node2 = ActorSystem("cluster", portConfig(25521).withFallback(commonConfig))
  val node3 = ActorSystem("cluster", portConfig(25522).withFallback(commonConfig))

  // interaction with distributed pub sub is done through a local mediator
  val node1Mediator = DistributedPubSub(node1).mediator
  val node2Mediator = DistributedPubSub(node2).mediator
  val node3Mediator = DistributedPubSub(node3).mediator

  // there is no delivery guarantees with distributed pub-sub, if we start publishing
  // before a cluster node has successfully joined the cluster, those message end up in
  // the dead letters queue
  SampleHelpers.waitForAllNodesUp(node1, node2, node3)

  // an actor either subscribes to a topic
  val node1subscriber = node1.actorOf(LogAllTheThingsActor.props(), "node1-subscriber")
  node1Mediator ! DistributedPubSubMediator.Subscribe("my-topic", node1subscriber)

  val node2subscriber = node2.actorOf(LogAllTheThingsActor.props(), "node2-subscriber")
  node2Mediator ! DistributedPubSubMediator.Subscribe("my-topic", node2subscriber)


  // or registers that it is a available at a specific path using "Put"
  val node1registered = node1.actorOf(LogAllTheThingsActor.props(), "registered-path")
  node1Mediator ! DistributedPubSubMediator.Put(node1registered)


  // that a node has subscriptions for a topic or registered actors for a path
  // is gossiped between nodes - this means it is eventually consistent - if the subscription/registration
  // has not reached a node when a message is published/sent it may be lost.

  for (n <- 1 to 5) {
    // other nodes can then publish to the topic
    node3Mediator ! DistributedPubSubMediator.Publish("my-topic", s"message $n to topic [my-topic] from node 3")

    // or send to one of the available actors registered at a path
    node3Mediator ! DistributedPubSubMediator.Send(
      path = "/user/registered-path",
      msg = s"message to path [registered-path] from node 3",
      localAffinity = false // should a local actor be preferred?
    )
    SampleHelpers.wait(1.second)
  }

  // Some more things to try out:
  // subscriptions are dynamic, so a subscribed actor can unsubscribe and a registered ("Put") actor can remove itself
  // with a registered actor the message ends up at one of the actors, but you can send ot all using SendToAll


}
