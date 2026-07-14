package com.foggyframework.mcp.launcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * MCP 服务启动器
 *
 * <p>启动 MCP 数据模型服务，默认包含演示数据模型。
 *
 * <p>配置选项：
 * <ul>
 *   <li>{@code foggy.demo.enabled=false} - 禁用演示数据模型</li>
 * </ul>
 */
@SpringBootApplication
@EnableAsync
public class McpLauncherApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpLauncherApplication.class, args);
    }
}
