package com.foggyframework.dataset.db.model.demo;

import org.springframework.stereotype.Service;

@Service
public class DemoAuthorizationService {

    public UserContext getCurrentUserContext(Object context) {
        UserContext userContext = new UserContext();
        userContext.setUserId("user_001");
        userContext.setUserName("张三");
        userContext.setRole("MANAGER");
        userContext.setStoreKey(1);
        userContext.setTeamId("TEAM_001");
        userContext.setRegionId("REGION_001");
        userContext.setPermissions(new String[]{"VIEW_SALES", "VIEW_CUSTOMER", "VIEW_STORE"});
        return userContext;
    }

    public static class UserContext {
        private String userId;
        private String userName;
        private String role;
        private Integer storeKey;
        private String teamId;
        private String regionId;
        private String[] permissions;

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

        public String[] getPermissions() {
            return permissions;
        }

        public void setPermissions(String[] permissions) {
            this.permissions = permissions;
        }
    }
}
