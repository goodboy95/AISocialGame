package com.aisocialgame.backend.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private List<AiStyleProperty> aiStyles = new ArrayList<>();

    public List<AiStyleProperty> getAiStyles() {
        return aiStyles;
    }

    public void setAiStyles(List<AiStyleProperty> aiStyles) {
        this.aiStyles = aiStyles;
    }

    public static class AiStyleProperty {
        private String key;
        private String label;
        private String description;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
