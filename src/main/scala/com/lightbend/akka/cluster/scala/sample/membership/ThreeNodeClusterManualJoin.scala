/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.typesafe.com>
 */
package com.lightbend.akka.cluster.scala.sample.membership

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory

object ThreeNodeClusterManualJoin extends App {
  val commonConfig = ConfigFactory.parseString(
    """
      akka {
        actor.provider = cluster
        remote.artery.enabled = true
        remote.artery.canonical.hostname = 127.0.0.1
        cluster.jmx.multi-mbeans-in-same-jvm = on
      }
    """)

  def portConfig(port: Int) = ConfigFactory.parseString(s"akka.remote.artery.canonical.port = $port")

  val node1 = ActorSystem("cluster", portConfig(25520).withFallback(commonConfig))
  val node2 = ActorSystem("cluster", portConfig(25521).withFallback(commonConfig))
  val node3 = ActorSystem("cluster", portConfig(25522).withFallback(commonConfig))

  // joins itself to form cluster
  val node1Cluster = Cluster(node1)
  node1Cluster.join(node1Cluster.selfAddress)

  // joins the cluster through the one node in the cluster
  val node2Cluster = Cluster(node2)
  node2Cluster.join(node1Cluster.selfAddress)

  // subsequent nodes can join through any node that is already in the cluster
  val node3Cluster = Cluster(node3)
  node3Cluster.join(node2Cluster.selfAddress)
}
