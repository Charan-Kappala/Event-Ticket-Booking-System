package com.ticketbooking;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.MySQLContainer;

import com.ticketbooking.support.MutableClock;

/**
 * Base class for tests that need a real MySQL database.
 *
 * One container is started for the whole test run (singleton container pattern)
 * and Spring Boot points its DataSource at it via @ServiceConnection.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Keep test output readable: SQL logging is very noisy under 100 parallel requests
                "spring.jpa.show-sql=false",
                "logging.level.org.hibernate.SQL=WARN",
                "logging.level.org.hibernate.type.descriptor.sql.BasicBinder=WARN",
                "logging.level.org.springframework.transaction=WARN",
                "logging.level.com.ticketbooking=INFO",
                // Tests run the hold-expiry job themselves, so keep the background schedule out of the way
                "booking.hold-sweep-interval=PT1H",
                // Test-only signing key; real deployments set JWT_SECRET in the environment
                "jwt.secret=integration-tests-only-signing-key-0123456789"
        })
@Import(AbstractIntegrationTest.TestClockConfig.class)
public abstract class AbstractIntegrationTest {

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    static {
        MYSQL.start();
    }
}
