package com.foggyframework.dataset.mcp.experience;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "foggy.mcp.experience-recipe.registry")
public class ExperienceRecipeRegistryProperties {
    private String store = "memory";
    private boolean autoInitSchema;

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public boolean isAutoInitSchema() {
        return autoInitSchema;
    }

    public void setAutoInitSchema(boolean autoInitSchema) {
        this.autoInitSchema = autoInitSchema;
    }
}
