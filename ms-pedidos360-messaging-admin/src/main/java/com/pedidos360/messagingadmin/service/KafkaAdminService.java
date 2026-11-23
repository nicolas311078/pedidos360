package com.pedidos360.messagingadmin.service;

import com.pedidos360.messagingadmin.dto.MessagingDtos.KafkaGroupInfo;
import com.pedidos360.messagingadmin.dto.MessagingDtos.KafkaTopicInfo;
import com.pedidos360.messagingadmin.dto.MessagingDtos.NewKafkaTopic;
import com.pedidos360.messagingadmin.dto.MessagingDtos.NodeInfo;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Estado y operaciones administrativas de Kafka (EP5) usando AdminClient. */
@Service
@RequiredArgsConstructor
public class KafkaAdminService {

    private final AdminClient adminClient;

    public List<KafkaTopicInfo> topics() throws Exception {
        Set<String> names = adminClient.listTopics().names().get();
        if (names.isEmpty()) {
            return List.of();
        }
        Map<String, KafkaFuture<TopicDescription>> futures = adminClient.describeTopics(names).topicNameValues();
        Map<String, TopicDescription> descriptions = new HashMap<>();
        for (Map.Entry<String, KafkaFuture<TopicDescription>> entry : futures.entrySet()) {
            descriptions.put(entry.getKey(), entry.getValue().get());
        }

        List<KafkaTopicInfo> result = new ArrayList<>();
        for (String name : names) {
            TopicDescription description = descriptions.get(name);
            int partitions = description == null ? 0 : description.partitions().size();
            int replicas = 0;
            if (description != null && !description.partitions().isEmpty()) {
                replicas = description.partitions().get(0).replicas().size();
            }
            result.add(new KafkaTopicInfo(name, partitions, replicas));
        }
        result.sort(Comparator.comparing(KafkaTopicInfo::name));
        return result;
    }

    public List<KafkaGroupInfo> consumerGroups() throws Exception {
        Collection<ConsumerGroupListing> listing = adminClient.listConsumerGroups().all().get();
        List<String> groupIds = listing.stream().map(ConsumerGroupListing::groupId).toList();
        if (groupIds.isEmpty()) {
            return List.of();
        }

        Map<String, ConsumerGroupDescription> descriptions =
                adminClient.describeConsumerGroups(groupIds).all().get();
        List<KafkaGroupInfo> result = new ArrayList<>();

        for (String groupId : groupIds) {
            ConsumerGroupDescription group = descriptions.get(groupId);
            if (group == null) {
                continue;
            }

            Map<TopicPartition, OffsetAndMetadata> committed = adminClient
                    .listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata()
                    .get();

            List<TopicPartition> partitions = new ArrayList<>(committed.keySet());
            Map<TopicPartition, ListOffsetsResultInfo> ends = partitions.isEmpty()
                    ? Map.of()
                    : adminClient.listOffsets(
                            partitions.stream().collect(java.util.stream.Collectors.toMap(
                                    tp -> tp, tp -> OffsetSpec.latest())))
                            .all()
                            .get();

            long lag = 0;
            for (TopicPartition partition : partitions) {
                OffsetAndMetadata offset = committed.get(partition);
                ListOffsetsResultInfo end = ends.get(partition);
                if (offset != null && end != null) {
                    lag += Math.max(0L, end.offset() - offset.offset());
                }
            }

            List<String> topics = partitions.stream()
                    .map(TopicPartition::topic)
                    .distinct()
                    .sorted()
                    .toList();

            result.add(new KafkaGroupInfo(
                    groupId,
                    group.state() == null ? "UNKNOWN" : group.state().toString(),
                    group.members().size(),
                    lag,
                    topics));
        }

        result.sort(Comparator.comparing(KafkaGroupInfo::groupId));
        return result;
    }

    public List<NodeInfo> brokers() throws Exception {
        Collection<Node> nodes = adminClient.describeCluster().nodes().get();
        return nodes.stream()
                .sorted(Comparator.comparingInt(Node::id))
                .map(node -> new NodeInfo(
                        node.id() + " · " + node.host() + ":" + node.port(), "broker", true))
                .toList();
    }

    public void createTopic(NewKafkaTopic request) throws Exception {
        adminClient.createTopics(List.of(new NewTopic(
                        request.name(),
                        request.partitions(),
                        (short) request.replicationFactor())))
                .all()
                .get();
    }

    public void deleteTopic(String name) throws Exception {
        adminClient.deleteTopics(List.of(name)).all().get();
    }
}
