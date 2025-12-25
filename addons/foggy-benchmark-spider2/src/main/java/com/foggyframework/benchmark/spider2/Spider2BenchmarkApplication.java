package com.foggyframework.benchmark.spider2;

import com.foggyframework.core.annotates.EnableFoggyFramework;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spider2 基准测试应用启动类
 */
@SpringBootApplication
@EnableFoggyFramework(bundleName = "foggy-benchmark-spider2")
public class Spider2BenchmarkApplication {

    public static void main(String[] args) {
        SpringApplication.run(Spider2BenchmarkApplication.class, args);
    }
}
