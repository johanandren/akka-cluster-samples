/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.typesafe.com>
 */
package com.lightbend.akka.cluster.scala.sample.singleton

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.Cluster
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.lightbend.akka.cluster.scala.sample.{LogAllTheThingsActor, SampleHelpers}
import com.lightbend.akka.cluster.scala.sample.LogAllTheThingsActor
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._

object ClusterSingletonSample extends App {
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

  val SingletonManagerName = "logging-singleton-manager"

  // one manager per singleton and node needs to be running
  def createSingletonManager(node: ActorSystem): Unit = {
    val singletonMgrProps = ClusterSingletonManager.props(
      singletonProps = LogAllTheThingsActor.props(),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(node1)
    )

    node.actorOf(singletonMgrProps, SingletonManagerName)
  }

  createSingletonManager(node1)
  createSingletonManager(node2)
  createSingletonManager(node3)

  // sample depends on cluster being formed, so wait for that before moving on
  // this is only to keep the sample brief and easy to follow
  SampleHelpers.waitForAllNodesUp(node1, node2, node3)


  // all communication to the singleton must be done through a singleton proxy
  def createProxy(node: ActorSystem): ActorRef =
    node.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/$SingletonManagerName",
        settings = ClusterSingletonProxySettings(node)
      ),
      "logging-singleton-proxy"
    )

  val node1Proxy = createProxy(node1)
  val node2Proxy = createProxy(node2)
  val node3Proxy = createProxy(node3)

  for(n <- 0 to 5) {
    node1Proxy ! s"Message $n from node1"
    node2Proxy ! s"Message $n from node2"
    node3Proxy ! s"Message $n from node3"

    SampleHelpers.wait(1.second)
  }

  // singleton runs on the oldest node, if that node dies it is started
  // on the new oldest node
  Cluster(node1).leave(Cluster(node1).selfAddress)
  // give it some time to leave before we try sending messages again
  SampleHelpers.wait(1.second)

  for(n <- 0 to 5) {
    node2Proxy ! s"Message $n from node2 after node1 stopped"
    node3Proxy ! s"Message $n from node3 after node1 stopped"

    SampleHelpers.wait(1.second)
  }

  // Some more things to try out:
  // actor state is lost on node crash, to have the new node reach the same state on startup
  // there basically are two options - use a persistent actor or use ddata to gossip state to all nodes
  // and query that on singleton startup


}
