package com.pedidos360.messagingadmin.controller;

import com.pedidos360.messagingadmin.dto.MessagingDtos.KafkaGroupInfo;
import com.pedidos360.messagingadmin.dto.MessagingDtos.KafkaTopicInfo;
import com.pedidos360.messagingadmin.dto.MessagingDtos.NewKafkaTopic;
import com.pedidos360.messagingadmin.dto.MessagingDtos.NodeInfo;
import com.pedidos360.messagingadmin.service.KafkaAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Expone el estado de Kafka al BFF (ruta /api/kafka/**). */
@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
public class KafkaController {

    private final KafkaAdminService service;

    @GetMapping("/topics")
    public List<KafkaTopicInfo> topics() throws Exception {
        return service.topics();
    }

    @GetMapping("/consumer-groups")
    public List<KafkaGroupInfo> consumerGroups() throws Exception {
        return service.consumerGroups();
    }

    @GetMapping("/brokers")
    public List<NodeInfo> brokers() throws Exception {
        return service.brokers();
    }

    @PostMapping("/topics")
    public void createTopic(@RequestBody NewKafkaTopic request) throws Exception {
        service.createTopic(request);
    }

    @DeleteMapping("/topics/{name}")
    public void deleteTopic(@PathVariable String name) throws Exception {
        service.deleteTopic(name);
    }
}
