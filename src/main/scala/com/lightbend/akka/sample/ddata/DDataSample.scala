/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.typesafe.com>
 */
package com.lightbend.akka.sample.ddata

import akka.actor.ActorSystem
import akka.cluster.{Cluster, MemberStatus}
import akka.pattern.ask
import akka.cluster.ddata.{DistributedData, GCounter, GCounterKey, Replicator}
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.lightbend.akka.sample.{LogAllTheThingsActor, SampleHelpers}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Success, Try}

object DDataSample extends App {
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

  // replicator needs to be running on all nodes (can also be done through config
  // but we need the actorrefs to them anyway in this sample
  val node1Replicator = DistributedData(node1).replicator
  val node2Replicator = DistributedData(node2).replicator
  val node3Replicator = DistributedData(node3).replicator

  // sample depends on cluster being formed, so wait for that before moving on
  // this is only to keep the sample brief and easy to follow
  SampleHelpers.waitForAllNodesUp(node1, node2, node3)

  val CounterKey = GCounterKey("visit-counter-1")
  val InitialCounterValue = GCounter.empty

  // timeout and ec for the asks below
  implicit val timeout = Timeout(10.seconds)
  import node1.dispatcher

  // subscribe to a key to get notified of updates on node1
  {
    val loggingActorOnNode1 = node1.actorOf(
      LogAllTheThingsActor.props(Some({
        case changed @ Replicator.Changed(CounterKey) => "New counter value: " + changed.get(CounterKey).value
      })),
      "logging-actor")
    node1Replicator ! Replicator.Subscribe(CounterKey, loggingActorOnNode1)
  }

  // in a separate thread (we are pretending it is on a separate JVM/node)
  // query a few times with a little while inbetween on node2
  import scala.concurrent.ExecutionContext.Implicits.global
  Future {
    val logGetResponse: Try[Replicator.GetResponse[GCounter]] => Unit = {
      case Success(success@Replicator.GetSuccess(CounterKey, Some(n))) =>
        node2.log.info(s"Get query [$n]: counter value: [${success.get(CounterKey).value}]")
      case Success(Replicator.NotFound(CounterKey, Some(n))) =>
        node2.log.info(s"Get query [$n]: no write to counter on any node yet")
      case whatever =>
        // in this sample:
        // Success(GetFailure) -> can't really happen with local read consistency
        // Falure(timeout exception) -> could possibly happen on a very slow machine
        node2.log.warning(s"Unexpected response to query: [$whatever]")
    }
    for (n <- 0 to 10) {
      (node2Replicator ? Replicator.Get(
        key = CounterKey,
        consistency = Replicator.ReadLocal,
        request = Some(n)))
        .mapTo[Replicator.GetResponse[GCounter]]
        .onComplete(logGetResponse)
      SampleHelpers.wait(1.second)
    }
  }


  // wait a bit, just to make the sample clear
  // then update on node3
  SampleHelpers.wait(2.seconds)
  (node3Replicator ? Replicator.Update(
    key = CounterKey,
    initial = InitialCounterValue,
    writeConsistency = Replicator.WriteLocal
  ) { counter =>
    // to use vector clocks the increment needs to know which node we did the update on
    counter.increment(Cluster(node3))
  }).onComplete(result => node3.log.info("Local write result: [{}]", result))


  // Some more things to try out:
  // start a new node with a replicator later and see the data reach it
  // try out the different read and write consistencies
  // harder with multiple nodes in the same JVM: sever the connection, write on both sides and see that they
  // reach consistency when they can communicate again

}
