package com.foggyframework.dataviewer.repository;

import com.foggyframework.dataviewer.domain.ListPresetDef;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 自定义列表 Mongo 仓库。
 */
@Repository
public interface ListPresetRepository extends MongoRepository<ListPresetDef, String> {

    List<ListPresetDef> findByOwnerIdAndModelAndBusinessKeyOrderByUpdatedAtDesc(
            String ownerId,
            String model,
            String businessKey);

    Optional<ListPresetDef> findByIdAndOwnerId(String id, String ownerId);

    Optional<ListPresetDef> findFirstByOwnerIdAndModelAndBusinessKeyAndIsDefaultTrue(
            String ownerId,
            String model,
            String businessKey);

    List<ListPresetDef> findByOwnerIdAndModelAndBusinessKeyAndIsDefaultTrue(
            String ownerId,
            String model,
            String businessKey);
}
