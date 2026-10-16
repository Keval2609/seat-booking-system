package com.dev.seatbooking;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.TimeZone;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17");
    }
}
