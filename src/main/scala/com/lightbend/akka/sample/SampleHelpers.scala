/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.typesafe.com>
 */
package com.lightbend.akka.sample

import akka.actor.ActorSystem
import akka.cluster.{Cluster, MemberStatus}

import scala.concurrent.duration.FiniteDuration

object SampleHelpers {

  /**
   * Block the calling thread until all the given nodes have reached Up and that is seen from all nodes.
   * In a real system (tm) you should have no use for such a thing but instead use an actor listening for
   * cluster membership events. This is here to keep samples brief and easy to follow.
   */
  def waitForAllNodesUp(nodes: ActorSystem*): Unit = {
    if (nodes.exists(node => Cluster(node).state.members.count(_.status == MemberStatus.Up) != nodes.size)) {
      Thread.sleep(250)
      waitForAllNodesUp(nodes:_*)
    } else ()
  }

  /**
   * Block the calling thread for the given time period. In real system (tm) built with
   * Akka you should never have anything like this - it is just for making the samples
   * easier to follow and understand.
   */
  def wait(d: FiniteDuration): Unit = {
    Thread.sleep(d.toMillis)
  }

}
