package com.rapidapp.postgres.autoconfigure;


import com.zaxxer.hikari.HikariDataSource;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import info.schnatterer.mobynamesgenerator.MobyNamesGenerator;
import io.rapidapp.RapidappClient;
import io.rapidapp.postgres.PostgresOuterClass;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextClosedEvent;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(RapidappPostgresConfigurationProperties.class)
@ConditionalOnProperty(prefix = "rapidapp.postgres", name = "enabled", havingValue = "true")
public class RapidappPostgresAutoConfiguration implements ApplicationListener<ContextClosedEvent> {

    private final RapidappPostgresConfigurationProperties properties;

    public static Logger logger = LogManager.getLogger(RapidappPostgresAutoConfiguration.class.getName());

    private String databaseId;

    public RapidappPostgresAutoConfiguration(RapidappPostgresConfigurationProperties properties) {
        logger.info("RapidappPostgresAutoConfiguration loaded");
        this.properties = properties;
    }

    @Bean
    public DataSource dataSource(@Autowired RapidappClient rapidappClient) {
        String id;
        String dbName = properties.getDatabaseName();
        if (dbName == null) {
            dbName = MobyNamesGenerator.getRandomName();
        }
        if (properties.getDatabaseId() == null) {
            logger.info("Creating database with name: {}", dbName);
            PostgresOuterClass.PostgresId postgresId = rapidappClient.createPostgresDatabase(dbName);
            id = postgresId.getId();
        } else {
            id = properties.getDatabaseId();
        }
        this.setDatabaseId(id);
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
        logger.info("Creating RapidappClient...");
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

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        if (properties.getDropBeforeApplicationExit()) {
            RapidappClient rapidappClient = rapidappClient();
            logger.info("Dropping database with id: {}", this.getDatabaseId());
            rapidappClient.deletePostgresDatabase(this.getDatabaseId());
        }
    }


    public String getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(String databaseId) {
        this.databaseId = databaseId;
    }
}
