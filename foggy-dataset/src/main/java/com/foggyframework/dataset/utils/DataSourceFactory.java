package com.foggyframework.dataset.utils;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 数据源工厂接口
 *
 * <p>用于在 fsscript 中动态创建数据源，支持外部模型配置自定义数据库连接。
 *
 * <h3>fsscript 中使用示例：</h3>
 * <pre>
 * // datasource.fsscript
 * import bean dataSourceFactory;
 *
 * // 方式1: 使用 Map 配置
 * export const myDataSource = dataSourceFactory.create({
 *     url: 'jdbc:mysql://localhost:3306/mydb',
 *     username: 'root',
 *     password: 'password'
 * });
 *
 * // 方式2: 使用环境变量
 * export const myDataSource = dataSourceFactory.create({
 *     url: env('MY_DB_URL', 'jdbc:mysql://localhost:3306/mydb'),
 *     username: env('MY_DB_USER', 'root'),
 *     password: env('MY_DB_PASS', 'password')
 * });
 * </pre>
 *
 * <h3>TM 模型中引用：</h3>
 * <pre>
 * // MyModel.tm
 * import '../datasource.fsscript' as ds;
 *
 * export const model = {
 *     name: 'MyModel',
 *     dataSource: ds.myDataSource,
 *     tableName: 'my_table',
 *     ...
 * };
 * </pre>
 *
 * @author foggy-framework
 * @since 8.0.0
 */
public interface DataSourceFactory {

    /**
     * 创建数据源
     *
     * @param config 数据源配置，支持以下参数：
     *               <ul>
     *               <li><b>url</b> (必填) - JDBC 连接 URL</li>
     *               <li><b>username</b> (必填) - 数据库用户名</li>
     *               <li><b>password</b> (必填) - 数据库密码</li>
     *               <li><b>driverClassName</b> (可选) - JDBC 驱动类名，自动检测</li>
     *               <li><b>poolName</b> (可选) - 连接池名称</li>
     *               <li><b>maximumPoolSize</b> (可选) - 最大连接数，默认 10</li>
     *               <li><b>minimumIdle</b> (可选) - 最小空闲连接，默认 2</li>
     *               <li><b>connectionTimeout</b> (可选) - 连接超时(ms)，默认 30000</li>
     *               <li><b>idleTimeout</b> (可选) - 空闲超时(ms)，默认 600000</li>
     *               <li><b>maxLifetime</b> (可选) - 连接最大生命周期(ms)，默认 1800000</li>
     *               </ul>
     * @return 创建的数据源
     * @throws IllegalArgumentException 如果必填参数缺失
     */
    DataSource create(Map<String, Object> config);

    /**
     * 使用简化参数创建数据源
     *
     * @param url      JDBC 连接 URL
     * @param username 数据库用户名
     * @param password 数据库密码
     * @return 创建的数据源
     */
    DataSource create(String url, String username, String password);

    /**
     * 获取环境变量值，支持默认值
     *
     * <p>用于在 fsscript 中获取环境变量配置：
     * <pre>
     * dataSourceFactory.env('MY_DB_URL', 'jdbc:mysql://localhost:3306/mydb')
     * </pre>
     *
     * @param name         环境变量名
     * @param defaultValue 默认值（环境变量不存在时使用）
     * @return 环境变量值或默认值
     */
    String env(String name, String defaultValue);

    /**
     * 获取环境变量值
     *
     * @param name 环境变量名
     * @return 环境变量值，不存在时返回 null
     */
    String env(String name);
}
