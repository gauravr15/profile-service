package com.odin.profileservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseStartupLogger {

    private final DataSource dataSource;
    private final Environment environment;

    @PostConstruct
    public void logDatabaseInfo() {
        String ddlMode = environment.getProperty("spring.jpa.hibernate.ddl-auto", "not-set");
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            log.info("[DB] Hibernate DDL mode: {}", ddlMode);
            log.info("[DB] Connected to: {}", url);
        } catch (Exception ex) {
            log.warn("[DB] Unable to determine datasource info: {}", ex.getMessage());
        }
    }
}
