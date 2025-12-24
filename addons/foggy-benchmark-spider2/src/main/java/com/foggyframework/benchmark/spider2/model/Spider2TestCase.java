package com.foggyframework.benchmark.spider2.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Spider2 测试用例数据模型
 *
 * 对应 Spider2-Lite 的 spider2-lite.jsonl 数据格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Spider2TestCase {

    /**
     * 测试用例唯一 ID
     * 例如：local002, bq010, sf_bq033
     */
    @JsonProperty("instance_id")
    private String instanceId;

    /**
     * 数据库名称
     * 例如：E_commerce, ga360, PATENTS
     */
    @JsonProperty("db")
    private String database;

    /**
     * 自然语言问题
     */
    @JsonProperty("question")
    private String question;

    /**
     * 外部知识文档路径（可选）
     * 例如：haversine_formula.md
     */
    @JsonProperty("external_knowledge")
    private String externalKnowledge;

    /**
     * 数据库类型
     */
    public enum DatabaseType {
        SQLITE,      // local* 前缀
        BIGQUERY,    // bq* 前缀
        SNOWFLAKE    // sf_* 前缀
    }

    /**
     * 获取数据库类型
     */
    public DatabaseType getDatabaseType() {
        if (instanceId == null) {
            return null;
        }
        if (instanceId.startsWith("local")) {
            return DatabaseType.SQLITE;
        } else if (instanceId.startsWith("sf_")) {
            return DatabaseType.SNOWFLAKE;
        } else if (instanceId.startsWith("bq")) {
            return DatabaseType.BIGQUERY;
        }
        return null;
    }

    /**
     * 检查是否为本地 SQLite 测试用例
     */
    public boolean isLocalSqlite() {
        return getDatabaseType() == DatabaseType.SQLITE;
    }

    /**
     * 获取 SQLite 数据库文件名
     */
    public String getSqliteFileName() {
        if (!isLocalSqlite() || database == null) {
            return null;
        }
        return database + ".sqlite";
    }
}
