package com.rapidapp.postgres.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rapidapp.postgres")
public class RapidappPostgresConfigurationProperties {

    private String apiKey;
    private String databaseId;
    private String databaseName;
    private boolean enabled;
    private boolean dropBeforeApplicationExit;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(String databaseId) {
        this.databaseId = databaseId;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public boolean getDropBeforeApplicationExit() {
        return dropBeforeApplicationExit;
    }

    public void setDropBeforeApplicationExit(boolean dropBeforeApplicationExit) {
        this.dropBeforeApplicationExit = dropBeforeApplicationExit;
    }
}
