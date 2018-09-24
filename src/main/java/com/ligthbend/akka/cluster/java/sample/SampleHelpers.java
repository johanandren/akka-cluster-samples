/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.ligthbend.akka.cluster.java.sample;

import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SampleHelpers {

  public static Config portConfig(int port) {
    return ConfigFactory.parseString("akka.remote.artery.canonical.port = " + port);
  }




  /**
   * Block the calling thread until all the given nodes have reached Up and that is seen from all nodes.
   * In a real system (tm) you should have no use for such a thing but instead use an actor listening for
   * cluster membership events. This is here to keep samples brief and easy to follow.
   */
  public static void waitForAllNodesUp(ActorSystem... nodes) throws InterruptedException {
    boolean allNodesUp = false;
    final List<ActorSystem> nodeList = Arrays.asList(nodes);
    do {
      allNodesUp = nodeList.stream().anyMatch(system -> {
        int upNodes = 0;
        Iterable<Member> members = Cluster.get(system).state().getMembers();
        for (Member member : members) {
          if (member.status().equals(MemberStatus.up()))
            upNodes++;
        }
        return upNodes != nodeList.size();
      });
      // don't ever block like this in actual code
      Thread.sleep(250);
    } while (!allNodesUp);
  }

  /**
   * Block the calling thread for the given time period. In real system (tm) built with
   * Akka you should NEVER have anything like this - it is just for making the samples
   * easier to follow and understand.
   */
  public static void wait(Duration d) {
    try {
      Thread.sleep(d.toMillis());
    } catch (InterruptedException ex) {
      throw new RuntimeException(ex);
    }
  }
}
