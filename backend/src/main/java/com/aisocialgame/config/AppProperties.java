package com.aisocialgame.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String projectKey = "aisocialgame";
    private Ai ai = new Ai();
    private Admin admin = new Admin();

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public Ai getAi() {
        return ai;
    }

    public void setAi(Ai ai) {
        this.ai = ai;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public static class Ai {
        private String defaultModel = "";
        private long systemUserId = 0;

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public long getSystemUserId() {
            return systemUserId;
        }

        public void setSystemUserId(long systemUserId) {
            this.systemUserId = systemUserId;
        }
    }

    public static class Admin {
        private String username = "admin";
        private String password = "admin123";
        private String displayName = "系统管理员";
        private long tokenTtlHours = 8;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public long getTokenTtlHours() {
            return tokenTtlHours;
        }

        public void setTokenTtlHours(long tokenTtlHours) {
            this.tokenTtlHours = tokenTtlHours;
        }
    }
}
