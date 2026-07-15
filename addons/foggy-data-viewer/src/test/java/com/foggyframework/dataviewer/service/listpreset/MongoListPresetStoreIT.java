package com.foggyframework.dataviewer.service.listpreset;

import com.foggyframework.dataviewer.domain.ListPresetDef;
import com.foggyframework.dataviewer.domain.QueryVisibility;
import com.foggyframework.dataviewer.repository.ListPresetRepository;
import de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mongo store 真实读写集成测试。
 *
 * 默认跳过，避免没有 Mongo 的本地/CI 环境被外部二进制下载或服务可用性阻塞。
 * 执行方式：
 * FOGGY_DATA_VIEWER_MONGO_IT=true
 * FOGGY_DATA_VIEWER_MONGO_URI=mongodb://localhost:27017/foggy_data_viewer_it
 */
@EnabledIfEnvironmentVariable(named = "FOGGY_DATA_VIEWER_MONGO_IT", matches = "true")
@DataMongoTest(
        excludeAutoConfiguration = EmbeddedMongoAutoConfiguration.class,
        properties = "spring.data.mongodb.uri=${FOGGY_DATA_VIEWER_MONGO_URI:mongodb://localhost:27017/foggy_data_viewer_it}"
)
@Import(MongoListPresetStoreIT.TestConfig.class)
class MongoListPresetStoreIT {

    @Autowired
    private ListPresetRepository repository;

    private MongoListPresetStore store;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        store = new MongoListPresetStore(repository);
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void shouldPersistAndListByUserModelAndBusinessKey() {
        ListPresetDef saved = store.save(preset("preset-1", "u1", "TicketModel", "ticket-list", true));
        store.save(preset("preset-2", "u2", "TicketModel", "ticket-list", false));
        store.save(preset("preset-3", "u1", "TicketModel", "other-list", false));

        List<ListPresetDef> result = store.list("u1", "TicketModel", "ticket-list");

        assertEquals(1, result.size());
        assertEquals(saved.getId(), result.get(0).getId());
        assertEquals("u1", result.get(0).getOwnerId());
    }

    @Test
    void shouldFindAndClearDefaultPreset() {
        store.save(preset("preset-1", "u1", "TicketModel", "ticket-list", true));
        store.save(preset("preset-2", "u1", "TicketModel", "ticket-list", true));

        assertTrue(store.findDefault("u1", "TicketModel", "ticket-list").isPresent());

        store.clearDefault("u1", "TicketModel", "ticket-list");

        assertTrue(store.findDefault("u1", "TicketModel", "ticket-list").isEmpty());
    }

    @Test
    void shouldDeletePreset() {
        ListPresetDef saved = store.save(preset("preset-1", "u1", "TicketModel", "ticket-list", false));

        store.delete(saved);

        assertTrue(store.findById("u1", saved.getId()).isEmpty());
    }

    private ListPresetDef preset(String id, String ownerId, String model, String businessKey, boolean isDefault) {
        Instant now = Instant.parse("2026-05-24T00:00:00Z");
        return ListPresetDef.builder()
                .id(id)
                .model(model)
                .businessKey(businessKey)
                .title("列表 " + id)
                .columns(List.of("ticketNo", "title"))
                .columnSettings(List.of())
                .query(new ListPresetDef.QueryConditionPreset(List.of(), List.of()))
                .pageSize(50)
                .visibility(QueryVisibility.PRIVATE)
                .ownerId(ownerId)
                .isDefault(isDefault)
                .version(1)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableMongoRepositories(
            basePackageClasses = ListPresetRepository.class,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = ListPresetRepository.class
            )
    )
    static class TestConfig {
    }
}
