package com.aisocialgame.backend.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private List<AiStyleProperty> aiStyles = new ArrayList<>();
    private AiModelProperties aiModel = new AiModelProperties();
    private CorsProperties cors = new CorsProperties();

    public List<AiStyleProperty> getAiStyles() {
        return aiStyles;
    }

    public void setAiStyles(List<AiStyleProperty> aiStyles) {
        this.aiStyles = aiStyles;
    }

    public AiModelProperties getAiModel() {
        return aiModel;
    }

    public void setAiModel(AiModelProperties aiModel) {
        this.aiModel = aiModel != null ? aiModel : new AiModelProperties();
    }

    public CorsProperties getCors() {
        return cors;
    }

    public void setCors(CorsProperties cors) {
        this.cors = cors != null ? cors : new CorsProperties();
    }

    public AiStyleProperty findAiStyle(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return aiStyles.stream()
                .filter(style -> key.equalsIgnoreCase(style.getKey()))
                .findFirst()
                .orElse(null);
    }

    public String describeAiStyle(String key) {
        AiStyleProperty style = findAiStyle(key);
        return style != null ? style.getDescription() : null;
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

    public static class AiModelProperties {
        private String baseUrl = "";
        private String token = "";
        private String modelName = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public boolean isConfigured() {
            return baseUrl != null && !baseUrl.isBlank()
                    && modelName != null && !modelName.isBlank();
        }
    }

    public static class CorsProperties {
        private List<String> allowedOrigins = new ArrayList<>(List.of("*"));
        private boolean allowCredentials = false;

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins != null ? allowedOrigins : new ArrayList<>();
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }
    }
}
