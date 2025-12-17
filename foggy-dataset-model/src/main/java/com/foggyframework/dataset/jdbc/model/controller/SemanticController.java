package com.foggyframework.dataset.jdbc.model.controller;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.jdbc.model.config.SemanticProperties;
import com.foggyframework.dataset.jdbc.model.semantic.domain.*;
import com.foggyframework.dataset.jdbc.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.jdbc.model.semantic.service.SemanticServiceV3;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 语义元数据接口控制器
 * 为上游MCP服务提供字段语义信息和值映射能力
 */
@RestController
@RequestMapping("semantic/v1")
//@ConfigurationProperties(prefix = "semantic")
public class SemanticController implements ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(SemanticController.class);

    @Resource
    SemanticServiceV3 semanticService;

    @Resource
    SemanticQueryServiceV3 semanticQueryService;

    @Resource
    SemanticProperties semanticProperties;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        System.currentTimeMillis();
    }

    @RequestMapping("metadata")
    @ApiOperation("获取字段语义元数据")
    public RX<SemanticMetadataResponse> getMetadata(
            @ApiParam(value = "输出格式: json(为上游MCP服务)|markdown(为大语言模型)", defaultValue = "markdown")
            @RequestParam(value = "format", defaultValue = "markdown") String format,
            @RequestBody(required = false) SemanticMetadataRequest request) {
        if (request == null) {
            request = new SemanticMetadataRequest();

        }

        semanticProperties.applyGetMetadata(request);

        if (request.getLevels() == null || request.getLevels().isEmpty()) {
            request.setLevels(Arrays.asList(1));
        }


        SemanticMetadataResponse response = semanticService.getMetadata(request, format);
        return RX.success(response);
    }



    @RequestMapping(value = "description-model-internal/{model}", method = {RequestMethod.GET, RequestMethod.POST})
    @ApiOperation("获取指定模型的全量字段与字典映射（内部MCP接口）")
    public RX<SemanticMetadataResponse> descriptionModelInternal(
            @ApiParam(value = "模型名称", required = true) @PathVariable String model,
            @ApiParam(value = "输出格式: json(为上游MCP服务)|markdown(为大语言模型)", defaultValue = "markdown")
            @RequestParam(value = "format", defaultValue = "markdown") String format) {

        // 记录接口调用的详细参数信息
        logger.info("=== 模型内部描述接口调用开始 ===");
        logger.info("模型名称: {}", model);
        logger.info("输出格式: {}", format);

        // 构建请求，设置levels为[1,2,3]获取全量字段
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Arrays.asList(model));

        semanticProperties.applyDescriptionModelInternal(request);
        request.setIncludeExamples(true); // 包含示例数据，便于MCP理解字段

        logger.info("请求参数详情: 模型列表: {}, 层级: {}, 包含示例: {}",
                request.getQmModels(),
                request.getLevels(),
                request.isIncludeExamples());

        SemanticMetadataResponse response = semanticService.getMetadata(request, format);

        // 记录响应信息
        logger.info("模型描述完成 - 模型: {}, 数据内容: {}, 处理时间: {}ms",
                model,
                response.getData() != null ? "有数据" : "无数据",
                System.currentTimeMillis()); // 简单的时间记录
        logger.info("=== 模型内部描述接口调用结束 ===");

        return RX.success(response);
    }

    @PostMapping("query-model/v2/{model}")
    @ApiOperation("语义查询接口 - 支持$caption条件/分组/排序")
    public RX<SemanticQueryResponse> queryModel(
            @ApiParam(value = "模型名称", required = true) @PathVariable String model,
            @ApiParam(value = "查询模式: execute(执行) | validate(验证)", defaultValue = "execute")
            @RequestParam(value = "mode", defaultValue = "execute") String mode,
            @RequestBody SemanticQueryRequest request) throws InterruptedException {

        // 记录接口调用的详细参数信息
        logger.info("=== 语义查询接口调用开始 ===");
        logger.info("模型名称: {}", model);
        logger.info("查询模式: {}", mode);
        logger.info("字段列表: {}", request.getColumns());
        logger.info("查询条件: {}", request.getSlice());
        logger.info("分组字段: {}", request.getGroupBy());
        logger.info("排序规则: {}", request.getOrderBy());
        logger.info("分页参数: start={}, limit={}, cursor={}", request.getStart(), request.getLimit(), request.getCursor());

        // 如果请求体内容较多，记录完整JSON（调试时启用）
        if (logger.isDebugEnabled()) {
            logger.debug("语义查询完整请求体: {}", request);
        }
//        Thread.sleep(2000L);
        SemanticQueryResponse response = semanticQueryService.queryModel(model, request, mode);

        // 记录响应基本信息
        logger.info("语义查询完成 - 模型: {}, 返回记录数: {}, 执行时间: {}ms",
                model,
                response.getItems() != null ? response.getItems().size() : 0,
                response.getDebug() != null && response.getDebug().getDurationMs() != null ? response.getDebug().getDurationMs() : 0);
        logger.info("=== 语义查询接口调用结束 ===");

        return RX.success(response);
    }


}