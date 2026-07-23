package com.odin.profileservice.config;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.web.client.RestTemplate;

import com.odin.profileservice.dto.AccountDeletionEvent;
import com.odin.profileservice.dto.GroupCreatedEvent;
import com.odin.profileservice.dto.NotificationDTO;
import com.odin.profileservice.dto.PrivacyVisibilityChangeEvent;
import com.odin.profileservice.dto.PublicKeyRefreshEvent;
import com.odin.profileservice.service.ContactTokenService;
import com.odin.profileservice.utility.PhoneNumberHasher;

@Configuration
public class AppConfig {

	@Value("${spring.kafka.bootstrap-servers}")
	private String kafkaUrl; 
	
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ContactTokenService contactTokenService(
            PhoneNumberHasher phoneNumberHasher,
            ContactTokenProperties contactTokenProperties) {
        return new ContactTokenService(phoneNumberHasher, contactTokenProperties);
    }
    
    @Bean
    public ProducerFactory<String, NotificationDTO> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class); 
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, NotificationDTO> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ProducerFactory<String, PublicKeyRefreshEvent> publicKeyRefreshProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, PublicKeyRefreshEvent> publicKeyRefreshKafkaTemplate() {
        return new KafkaTemplate<>(publicKeyRefreshProducerFactory());
    }

    @Bean
    public ProducerFactory<String, GroupCreatedEvent> groupEventProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, GroupCreatedEvent> groupEventKafkaTemplate() {
        return new KafkaTemplate<>(groupEventProducerFactory());
    }

    @Bean
    public ProducerFactory<String, PrivacyVisibilityChangeEvent> privacyVisibilityChangeProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, PrivacyVisibilityChangeEvent> privacyVisibilityChangeKafkaTemplate() {
        return new KafkaTemplate<>(privacyVisibilityChangeProducerFactory());
    }

    @Bean
    public ProducerFactory<String, AccountDeletionEvent> accountDeletionProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, AccountDeletionEvent> accountDeletionKafkaTemplate() {
        return new KafkaTemplate<>(accountDeletionProducerFactory());
    }
}
