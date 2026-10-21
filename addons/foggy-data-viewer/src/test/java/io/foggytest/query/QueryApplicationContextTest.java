package io.foggytest.query;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.DbModelAutoConfiguration;
import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.semantic.member.MemberBindingService;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataviewer.controller.ViewerApiController;
import com.foggyframework.dataviewer.domain.MemberQueryRequest;
import com.foggyframework.dataviewer.service.MemberQueryService;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Query consumer outside the Foggy package: all production beans must come from Boot imports. */
class QueryApplicationContextTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"sqlite", "memory"})
    void queryApplicationStartsAndExecutesMemberQueryThroughAutoConfiguredBinding(String store) throws Exception {
        Path models = Files.createDirectory(directory.resolve("models"));
        Files.writeString(models.resolve("StartupOrder.tm"), """
                export const model={name:'StartupOrder',tableName:'startup_order',idColumn:'id',
                  properties:[{name:'orderId',column:'id',type:'INTEGER'}],
                  dimensions:[{name:'customer',tableName:'startup_customer',foreignKey:'customer_id',
                    primaryKey:'id',captionColumn:'name'}],measures:[]};
                """);
        Files.writeString(models.resolve("StartupQuery.qm"), """
                const m=loadTableModel('StartupOrder');
                export const queryModel={name:'StartupQuery',loader:'v2',model:m,
                  columnGroups:[{caption:'Fields',items:[
                    {ref:m.customer$id,name:'customerKey',alias:'keyOut'},
                    {ref:m.customer$caption,name:'customerName',alias:'nameOut'}]}],accesses:[]};
                """);

        assertThat(QueryApplication.class.getPackageName()).doesNotStartWith("com.foggyframework");
        new WebApplicationContextRunner()
                .withUserConfiguration(QueryApplication.class)
                .withPropertyValues(
                        "spring.main.lazy-initialization=false",
                        "spring.datasource.url=jdbc:sqlite:" + directory.resolve("query.sqlite"),
                        "spring.datasource.driver-class-name=org.sqlite.JDBC",
                        "foggy.data-viewer.enabled=true",
                        "foggy.data-viewer.cache.store=" + (store.equals("memory") ? "memory" : "mongo"),
                        "foggy.data-viewer.cache.storage=sqlite",
                        "foggy.data-viewer.cache.sqlite-path=" + directory.resolve("viewer.sqlite"),
                        "foggy.data-viewer.list-preset.file-base-dir=" + directory.resolve("presets"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DbModelAutoConfiguration.class);
                    assertThat(context).hasSingleBean(QueryModelLoader.class);
                    assertThat(context).hasSingleBean(MemberBindingService.class);
                    assertThat(context).hasSingleBean(QueryFacade.class);
                    assertThat(context).hasSingleBean(MemberQueryService.class);
                    assertThat(context).hasSingleBean(ViewerApiController.class);

                    JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
                    jdbc.execute("CREATE TABLE startup_customer(id INTEGER PRIMARY KEY, name VARCHAR(50))");
                    jdbc.execute("CREATE TABLE startup_order(id INTEGER PRIMARY KEY, customer_id INTEGER)");
                    jdbc.update("INSERT INTO startup_customer VALUES(1,'Customer A')");
                    jdbc.update("INSERT INTO startup_order VALUES(10,1)");
                    SystemBundlesContext bundles = context.getBean(SystemBundlesContext.class);
                    assertThat(bundles.addExternalBundle("query-startup", "", models.toString(), false)).isTrue();
                    try {
                        MemberQueryRequest request = new MemberQueryRequest();
                        request.setQmModel("StartupQuery");
                        request.setFieldName("customerKey");
                        var result = context.getBean(MemberQueryService.class).query(request, "", null);
                        assertThat(result.getSelectionFieldName()).isEqualTo("customerKey");
                        assertThat(result.getDisplayFieldName()).isEqualTo("customerName");
                        assertThat(result.getItems()).hasSize(1);
                        assertThat(result.getItems().get(0).getLabel()).isEqualTo("Customer A");
                    } finally {
                        bundles.removeBundle("query-startup");
                    }
                });
    }

    // No @ComponentScan or explicit @Import of engine/viewer beans: missing registration must fail this test.
    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
            "de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration"
    })
    static class QueryApplication {
    }
}
