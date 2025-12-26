package com.foggyframework.dataset.db.model.semantic.controller;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Collections;

/**
 * V3版本语义服务测试Controller
 *
 * <p>用于测试 V3 版本的语义服务，核心特性：</p>
 * <ul>
 *   <li>维度字段展开为独立的 $id 和 $caption 字段</li>
 *   <li>每个展开字段有独立的描述（格式说明等）</li>
 *   <li>查询时直接使用字段名，无需拼接后缀</li>
 * </ul>
 */
@Api(tags = "V3语义服务测试")
@RestController
@RequestMapping("/semantic/v3/test")
public class SemanticServiceV3TestController {

    @Resource
    private SemanticServiceV3 semanticServiceV3;

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    /**
     * 获取模型元数据（V3版本：维度展开）
     *
     * @param model  模型名称
     * @param format 输出格式：json 或 markdown
     * @return 元数据响应
     */
    @ApiOperation("获取模型元数据（V3版本）")
    @GetMapping("/metadata/{model}")
    public SemanticMetadataResponse getMetadata(
            @ApiParam("模型名称") @PathVariable String model,
            @ApiParam("输出格式：json/markdown") @RequestParam(defaultValue = "markdown") String format) {

        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Collections.singletonList(model));

        return semanticServiceV3.getMetadata(request, format);
    }

    /**
     * 获取多个模型的元数据
     *
     * @param request 元数据请求
     * @param format  输出格式
     * @return 元数据响应
     */
    @ApiOperation("获取多个模型元数据（V3版本）")
    @PostMapping("/metadata")
    public SemanticMetadataResponse getMetadataPost(
            @RequestBody SemanticMetadataRequest request,
            @ApiParam("输出格式：json/markdown") @RequestParam(defaultValue = "markdown") String format) {

        return semanticServiceV3.getMetadata(request, format);
    }

    /**
     * 执行语义查询（V3版本）
     *
     * @param model   模型名称
     * @param request 查询请求
     * @param mode    执行模式：execute/validate
     * @return 查询响应
     */
    @ApiOperation("执行语义查询（V3版本）")
    @PostMapping("/query/{model}")
    public SemanticQueryResponse queryModel(
            @ApiParam("模型名称") @PathVariable String model,
            @RequestBody SemanticQueryRequest request,
            @ApiParam("执行模式：execute/validate") @RequestParam(defaultValue = "execute") String mode) {

        return semanticQueryServiceV3.queryModel(model, request, mode);
    }

    /**
     * 验证查询请求（V3版本）
     *
     * @param model   模型名称
     * @param request 查询请求
     * @return 验证响应
     */
    @ApiOperation("验证查询请求（V3版本）")
    @PostMapping("/validate/{model}")
    public SemanticQueryResponse validateQuery(
            @ApiParam("模型名称") @PathVariable String model,
            @RequestBody SemanticQueryRequest request) {

        return semanticQueryServiceV3.validateQuery(model, request);
    }
}
