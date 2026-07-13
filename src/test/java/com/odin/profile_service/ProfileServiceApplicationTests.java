package com.odin.profile_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.odin.profileservice.ProfileServiceApplication;

@SpringBootTest(classes = ProfileServiceApplication.class, properties = {
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.datasource.url=jdbc:h2:mem:profile-context;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.kafka.bootstrap-servers=localhost:9092",
		"core.update.url=http://localhost/",
		"encryption-key=test-encryption-key",
		"is.static.otp=true",
		"static.otp=123456",
		"jwt.expiration=900000",
		"jwt.refreshExpiration=2592000000",
		"jwt.issuer=test",
		"jwt.secret=test-secret-key-that-is-long-enough-for-hmac-signing-1234567890",
		"max.incorrect.password.count=3",
		"otp.expiry.duration.seconds=60",
		"update.sync.time=false",
		"eureka.client.enabled=false"
})
class ProfileServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
