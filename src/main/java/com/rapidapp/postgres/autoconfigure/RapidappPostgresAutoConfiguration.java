package com.rapidapp.postgres.autoconfigure;


import com.zaxxer.hikari.HikariDataSource;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.rapidapp.RapidappClient;
import io.rapidapp.postgres.PostgresOuterClass;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(RapidappPostgresConfigurationProperties.class)
@ConditionalOnProperty(prefix = "rapidapp.postgres", name = "enabled", havingValue = "true")
public class RapidappPostgresAutoConfiguration {

    private final RapidappPostgresConfigurationProperties properties;

    public static Logger logger = LogManager.getLogger(RapidappPostgresAutoConfiguration.class.getName());

    public RapidappPostgresAutoConfiguration(RapidappPostgresConfigurationProperties properties) {
        logger.info("RapidappPostgresAutoConfiguration loaded");
        this.properties = properties;
    }

    @Bean
    public DataSource dataSource(@Autowired RapidappClient rapidappClient) {
        String id;
        if (properties.getDatabaseId() == null) {
            logger.info("Creating database with name: " + properties.getDatabaseName());
            PostgresOuterClass.PostgresId postgresId = rapidappClient.createPostgresDatabase(properties.getDatabaseName());
            id = postgresId.getId();
        } else {
            id = properties.getDatabaseId();
        }
        PostgresOuterClass.Postgres postgresDatabase = getPostgresDatabaseWithRetry(rapidappClient, id, 10, Duration.ofSeconds(2));
        if (postgresDatabase != null) {
            String url = "jdbc:postgresql://" + postgresDatabase.getHost() + ":" + postgresDatabase.getPort() + "/" + postgresDatabase.getDatabase();
            return DataSourceBuilder.create()
                    .driverClassName("org.postgresql.Driver")
                    .url(url)
                    .username(postgresDatabase.getUsername())
                    .password(postgresDatabase.getPassword())
                    .type(HikariDataSource.class)
                    .build();
        }
        logger.error("Failed to create database");
        return null;
    }

    @Bean
    public RapidappClient rapidappClient() {
        logger.info("Creating RapidappClient with apiKey: " + properties.getApiKey());
        return new RapidappClient(properties.getApiKey());
    }

    private PostgresOuterClass.Postgres getPostgresDatabaseWithRetry(RapidappClient client, String id, int maxRetries, Duration retryInterval) {
        RetryPolicy<PostgresOuterClass.Postgres> retryPolicy = RetryPolicy.<PostgresOuterClass.Postgres>builder()
                .handleIf((result, failure) -> result == null || !List.of("running", "readonly").contains(result.getStatus())) // Condition to retry
                .withDelay(retryInterval)
                .withMaxRetries(maxRetries)
                .build();

        return Failsafe.with(retryPolicy).get(() -> client.getPostgresDatabase(id));
    }

}
