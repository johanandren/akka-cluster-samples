/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.typesafe.com>
 */
package com.lightbend.akka.sample.sharding

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.{DistributedData, GCounter, GCounterKey, Replicator}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.pattern.ask
import akka.util.Timeout
import com.lightbend.akka.sample.{LogAllTheThingsActor, SampleHelpers}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Success, Try}

object ShardingSample extends App {
  class CountingActor extends Actor with ActorLogging {
    var counter = 0
    def receive = {
      case msg =>
        counter += 1
        log.info(s"Got message nr $counter: $msg")
    }
  }
  object CountingActor {
    val props = Props[CountingActor]
  }

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

  case class Envelope(entityId: String, payload: Any)

  // we could have different types of sharded entities running in the same cluster so we need to identify each
  val ShardTypeName = "Counter"
  // each entity will belong to a shard, we extract this many different shard id's
  // that the entities will be living in
  val NumberOfShards = 5

  // we need a way to extract both a unique id and the shard id from a message
  // in this sample we use an envelope, but we could also use messages that already
  // contain an id field and extract from there
  val extractEntityIdFunction: ShardRegion.ExtractEntityId = {
    case Envelope(entityId, payload) => (entityId, payload)
  }
  val extractShardIdFunction: ShardRegion.ExtractShardId = {
    case Envelope(entityId, _) => (entityId.hashCode % NumberOfShards).toString
  }


  // we need to start sharding on each node
  val List(node1Region, node2Region, node3Region) =
    for {
      node <- List(node1, node2, node3)
    } yield {
      ClusterSharding(node).start(
        typeName = ShardTypeName,
        entityProps = CountingActor.props,
        settings = ClusterShardingSettings(node),
        extractEntityId = extractEntityIdFunction,
        extractShardId = extractShardIdFunction
      )
    }

  // all interaction with the entities is done through the region (or through a proxy started with
  // ClusterSharding(node).startProxy()
  node1Region ! Envelope(entityId = "1", payload = "Early message from node1 to entity 1")
  node2Region ! Envelope(entityId = "1", payload = "Early message from node2 to entity 1")
  node2Region ! Envelope(entityId = "1", payload = "Early message from node3 to entity 1")

  SampleHelpers.waitForAllNodesUp(node1, node2, node3)

  node1Region ! Envelope(entityId = "5", payload = "Early message from node1 to entity 5")
  node2Region ! Envelope(entityId = "5", payload = "Early message from node2 to entity 5")
  node2Region ! Envelope(entityId = "5", payload = "Early message from node3 to entity 5")



  node1Region ! Envelope(entityId = "1", payload = "A bit later message from node1 to entity 1")
  node2Region ! Envelope(entityId = "1", payload = "A bit later message from node2 to entity 1")
  node2Region ! Envelope(entityId = "1", payload = "A bit later message from node3 to entity 1")

  node1Region ! Envelope(entityId = "5", payload = "A bit later message from node1 to entity 5")
  node2Region ! Envelope(entityId = "5", payload = "A bit later message from node2 to entity 5")
  node2Region ! Envelope(entityId = "5", payload = "A bit later message from node3 to entity 5")


  // Some more things to try out:
  // When a node is removed (downed), the shards on it will be moved to another node in the cluster
  // their state will only be kept if they were persistent actors or used some other means of ensuring the state can
  // be recovered.
  // When new nodes are added, the existing shards will be spread out across them. Try adding more entities
  // and then adding a new node.

}
