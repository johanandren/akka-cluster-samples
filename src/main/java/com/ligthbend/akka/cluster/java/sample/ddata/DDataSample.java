/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.ligthbend.akka.cluster.java.sample.ddata;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.ddata.*;
import com.ligthbend.akka.cluster.java.sample.LogAllTheThingsActor;
import com.ligthbend.akka.cluster.java.sample.SampleHelpers;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static akka.pattern.PatternsCS.*;
import static com.ligthbend.akka.cluster.java.sample.SampleHelpers.portConfig;

public class DDataSample {

  public static void main(String[] args) throws Exception {
    final Config commonConfig = ConfigFactory.load("java-cluster-with-seeds");

    final ActorSystem node1 = ActorSystem.create("cluster", portConfig(25520).withFallback(commonConfig));
    final ActorSystem node2 = ActorSystem.create("cluster", portConfig(25521).withFallback(commonConfig));
    final ActorSystem node3 = ActorSystem.create("cluster", portConfig(25522).withFallback(commonConfig));

    // replicator needs to be running on all nodes (can also be done through config
    // but we need the actorrefs to them anyway in this sample
    final ActorRef node1Replicator = DistributedData.get(node1).replicator();
    final ActorRef node2Replicator = DistributedData.get(node2).replicator();
    final ActorRef node3Replicator = DistributedData.get(node3).replicator();

    // sample depends on cluster being formed, so wait for that before moving on
    // this is only to keep the sample brief and easy to follow
    SampleHelpers.waitForAllNodesUp(node1, node2, node3);


    final Key counterKey = GCounterKey.create("visit-counter-1");
    final GCounter initialCounterValue = GCounter.empty();

    // subscribe to a key to get notified of updates on node1
    {
      ActorRef loggingActorOnNode1 = node1.actorOf(LogAllTheThingsActor.props(), "logging-actor");
      node1Replicator.tell(new Replicator.Subscribe(counterKey, loggingActorOnNode1), ActorRef.noSender());
    }


    // in a separate thread (we are pretending it is on a separate JVM/node)
    // query a few times with a little while inbetween on node2
    new Thread(() -> {
      for (int n = 0; n < 10; n++) {
        CompletionStage<Object> responseCS =
            ask(node2Replicator,
              new Replicator.Get(
                  counterKey,
                  Replicator.readLocal(),
                  Optional.of(n)),
              Duration.ofSeconds(1)
            );

        responseCS.whenComplete((response, failure) -> {
          if (failure == null) {
            node2.log().info("Value from Get response: {}", response);
          } else {
            node2.log().error(failure, "Get query failed");
          }
        });

        SampleHelpers.wait(Duration.ofSeconds(1));
      }
    });



    // wait a bit, just to make the sample clear
    // then update on node3
    SampleHelpers.wait(Duration.ofSeconds(2));
    CompletionStage<Object> writeCompletion = ask(node3Replicator,
        new Replicator.Update<GCounter>(
            counterKey,
            initialCounterValue,
            Replicator.writeLocal(),
            // to use vector clocks the increment needs to know which node we did the update on
            (GCounter counter) -> counter.increment(Cluster.get(node3), 1L)
        ),
        Duration.ofSeconds(3)
    );

    writeCompletion.whenComplete((complete, failure) -> {
      if (failure != null) {

      } else {
        
      }
    })

    /*

  (node3Replicator ? Replicator.Update(
    key = CounterKey,
    initial = InitialCounterValue,
    writeConsistency = Replicator.WriteLocal
  ) { counter =>
    counter.increment(Cluster(node3))
  }).onComplete(result => node3.log.info("Local write result: [{}]", result))


  // Some more things to try out:
  // start a new node with a replicator later and see the data reach it
  // try out the different read and write consistencies
  // harder with multiple nodes in the same JVM: sever the connection, write on both sides and see that they
  // reach consistency when they can communicate again

     */
  }
}
