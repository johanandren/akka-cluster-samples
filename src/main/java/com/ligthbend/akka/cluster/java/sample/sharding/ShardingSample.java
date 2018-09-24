/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.ligthbend.akka.cluster.java.sample.sharding;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.sharding.ShardRegion.MessageExtractor;
import com.ligthbend.akka.cluster.java.sample.SampleHelpers;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.Serializable;
import java.util.Arrays;

import static com.ligthbend.akka.cluster.java.sample.SampleHelpers.portConfig;

public class ShardingSample {

  /**
   * Envelope wrapping a message to the sharded actor, providing an identifier which entity
   * the message is intended for
   *
   * In this sample we use an envelope, but we could also use messages that already
   * contain an id field and extract it from there
   */
  static final class Envelope implements Serializable {
    public final String entityId;
    public final Object payload;
    public Envelope(String entityId, Object payload) {
      this.entityId = entityId;
      this.payload = payload;
    }
  }

  private final static MessageExtractor MESSAGE_EXTRACTOR = new MessageExtractor() {

    @Override
    public String entityId(Object message) {
      if (message instanceof Envelope) {
        return ((Envelope) message).entityId;
      } else {
        return null;
      }
    }

    @Override
    public Object entityMessage(Object message) {
      if (message instanceof Envelope) {
        return ((Envelope) message).payload;
      } else {
        return null;
      }
    }

    @Override
    public String shardId(Object message) {
      if (message instanceof Envelope) {
        // distribute the entities across the number of shards based on
        // the entity id hashcode
        int entityIdHash = ((Envelope) message).entityId.hashCode();
        return Integer.toString(entityIdHash % NUMBER_OF_SHARDS);
      } else {
        return null;
      }
    }
  };

  // we could have different types of sharded entities running in the same cluster so we need to identify each
  public static final String SHARD_TYPE_NAME = "Counter";
  // each entity will belong to a shard, we extract this many different shards
  // that the entities will be living in, set to a low number in the sample to
  // make sure the (low number of) entities end up in different nodes
  public static final int NUMBER_OF_SHARDS = 5;

  public static void main(String[] args) throws Exception {
    final Config commonConfig = ConfigFactory.load("java-cluster-with-seeds");

    final ActorSystem node1 = ActorSystem.create("cluster", portConfig(25520).withFallback(commonConfig));
    final ActorSystem node2 = ActorSystem.create("cluster", portConfig(25521).withFallback(commonConfig));
    final ActorSystem node3 = ActorSystem.create("cluster", portConfig(25522).withFallback(commonConfig));

    // we need to start sharding on each node
    final ActorRef node1Region = ClusterSharding.get(node1).start(
        SHARD_TYPE_NAME,
        CountingActor.props(),
        ClusterShardingSettings.create(node1),
        MESSAGE_EXTRACTOR
    );
    final ActorRef node2Region = ClusterSharding.get(node2).start(
        SHARD_TYPE_NAME,
        CountingActor.props(),
        ClusterShardingSettings.create(node2),
        MESSAGE_EXTRACTOR
    );
    final ActorRef node3Region = ClusterSharding.get(node3).start(
        SHARD_TYPE_NAME,
        CountingActor.props(),
        ClusterShardingSettings.create(node3),
        MESSAGE_EXTRACTOR
    );


    // all interaction with the entities is done through the region (or through a proxy started with
    // ClusterSharding(node).startProxy()
    node1Region.tell(new Envelope("1", "Early message from node1 to entity 1"), ActorRef.noSender());
    node2Region.tell(new Envelope("1", "Early message from node2 to entity 1"), ActorRef.noSender());
    node3Region.tell(new Envelope("1", "Early message from node3 to entity 1"), ActorRef.noSender());

    node1Region.tell(new Envelope("5", "Early message from node1 to entity 5"), ActorRef.noSender());
    node2Region.tell(new Envelope("5", "Early message from node2 to entity 5"), ActorRef.noSender());
    node2Region.tell(new Envelope("5", "Early message from node3 to entity 5"), ActorRef.noSender());
    SampleHelpers.waitForAllNodesUp(node1, node2, node3);


    node1Region.tell(new Envelope("1", "A bit later message from node1 to entity 1"), ActorRef.noSender());
    node2Region.tell(new Envelope("1", "A bit later message from node2 to entity 1"), ActorRef.noSender());
    node2Region.tell(new Envelope("1", "A bit later message from node3 to entity 1"), ActorRef.noSender());

    node1Region.tell(new Envelope("5", "A bit later message from node1 to entity 5"), ActorRef.noSender());
    node2Region.tell(new Envelope("5", "A bit later message from node2 to entity 5"), ActorRef.noSender());
    node2Region.tell(new Envelope("5", "A bit later message from node3 to entity 5"), ActorRef.noSender());

    // Some more things to try out:
    // When a node is removed (downed), the shards on it will be moved to another node in the cluster
    // their state will only be kept if they were persistent actors or used some other means of ensuring the state can
    // be recovered.
    // When new nodes are added, the existing shards will be spread out across them. Try adding more entities
    // and then adding a new node.
  }

}
