package com.foggyframework.analytics.console.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.console.model.AnalyticsConsoleCatalogState;
import com.foggyframework.analytics.console.model.AnalyticsConsoleFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class FileAnalyticsConsoleCatalogRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void atomicallyPersistsConsoleOwnedMetadataAcrossRepositoryRestart() {
        Path catalog = tempDir.resolve("product/catalog.json");
        FileAnalyticsConsoleCatalogRepository repository =
                new FileAnalyticsConsoleCatalogRepository(catalog, new ObjectMapper());
        AnalyticsConsoleFolder folder = new AnalyticsConsoleFolder(
                "folder-sales", "销售驾驶舱", null, "designer-a", Instant.EPOCH);

        AnalyticsConsoleCatalogState updated = repository.update(state -> {
            var folders = new ArrayList<>(state.folders());
            folders.add(folder);
            return new AnalyticsConsoleCatalogState(
                    state.revision(), folders, state.assets(), state.conversations());
        });

        assertThat(updated.revision()).isEqualTo(1);
        assertThat(new FileAnalyticsConsoleCatalogRepository(catalog, new ObjectMapper())
                .read().folders()).containsExactly(folder);
    }
}
