package com.foggyframework.dataviewer.repository;

import com.foggyframework.dataviewer.domain.QueryVisibility;
import com.foggyframework.dataviewer.domain.SavedQueryDef;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 保存查询仓库
 */
@Repository
public interface SavedQueryRepository extends MongoRepository<SavedQueryDef, String> {

    /**
     * 查询用户可见的保存查询
     * <p>
     * 包括：个人查询 + 同部门共享 + 同租户共享
     */
    @Query("{ 'model': ?0, $or: [ " +
            "{ 'ownerId': ?1 }, " +
            "{ 'visibility': 'DEPARTMENT', 'ownerDeptId': ?2 }, " +
            "{ 'visibility': 'TENANT', 'ownerTenantId': ?3 } " +
            "] }")
    List<SavedQueryDef> findVisibleQueries(String model, String userId, String deptId, String tenantId);
}
