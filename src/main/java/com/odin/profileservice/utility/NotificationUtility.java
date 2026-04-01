package com.odin.profileservice.utility;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.odin.profileservice.dto.NotificationDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NotificationUtility {

    private static final String TOPIC = "otp.notification.message";

    @Autowired
    private KafkaTemplate<String, NotificationDTO> kafkaTemplate;

    public void sendOtpMessage(NotificationDTO message) {
        log.info("Producing message to Kafka: {}", message);
        kafkaTemplate.send(TOPIC, message);
    }
}
