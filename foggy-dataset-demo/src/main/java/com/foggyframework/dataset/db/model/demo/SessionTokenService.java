package com.foggyframework.dataset.db.model.demo;

import org.springframework.stereotype.Service;

@Service
public class SessionTokenService {

    public SessionToken getSessionToken() {
        SessionToken token = new SessionToken();
        token.setUserId("user_001");
        token.setUserName("张三");
        token.setRole("MANAGER");
        token.setStoreKey(1);
        token.setTeamId("TEAM_001");
        token.setRegionId("REGION_001");
        return token;
    }

    public static class SessionToken {
        private String userId;
        private String userName;
        private String role;
        private Integer storeKey;
        private String teamId;
        private String regionId;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public Integer getStoreKey() {
            return storeKey;
        }

        public void setStoreKey(Integer storeKey) {
            this.storeKey = storeKey;
        }

        public String getTeamId() {
            return teamId;
        }

        public void setTeamId(String teamId) {
            this.teamId = teamId;
        }

        public String getRegionId() {
            return regionId;
        }

        public void setRegionId(String regionId) {
            this.regionId = regionId;
        }
    }
}
