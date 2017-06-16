/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.typesafe.com>
 */
package com.lightbend.akka.sample.cluster

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{ClusterDomainEvent, CurrentClusterState, MemberEvent}
import com.lightbend.akka.sample.LogAllTheThingsActor
import com.typesafe.config.ConfigFactory

object ClusterEvents extends App {
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
  val loggingActor = node1.actorOf(LogAllTheThingsActor.props(), "logging-actor")
  Cluster(node1).subscribe(loggingActor, classOf[MemberEvent])

  val node2 = ActorSystem("cluster", portConfig(25521).withFallback(commonConfig))
  val node3 = ActorSystem("cluster", portConfig(25522).withFallback(commonConfig))

}
