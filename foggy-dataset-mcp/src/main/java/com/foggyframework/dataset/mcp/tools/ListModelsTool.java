package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.dataset.db.model.spi.DbQueryDimension;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 发现所有可用模型的轻量级路由工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListModelsTool implements McpTool {

    private final SemanticServiceResolver semanticServiceResolver;
    private final QueryModelLoader queryModelLoader;

    @Override
    public String getName() {
        return "dataset.list_models";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        return EnumSet.of(ToolCategory.METADATA);
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        log.info("Listing all models for AI routing, namespace={}", context.getNamespace());

        List<String> modelNames = semanticServiceResolver.getAllModelNames();
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 数据模型列表\n\n");
        markdown.append("| 模型 | 简称 | 说明 | 主时间轴 | 适用问题 | 推荐下一步 |\n");
        markdown.append("|------|------|------|----------|----------|------------|\n");

        for (String modelName : modelNames) {
            try {
                QueryModel qm = queryModelLoader.getJdbcQueryModel(modelName, context.getNamespace());
                if (qm != null) {
                    String shortAlias = qm.getShortAlias() != null ? qm.getShortAlias() : "";
                    String caption = qm.getCaption() != null ? qm.getCaption() : "";

                    String timeRoleCol = "";
                    if (qm.getQueryDimensions() != null) {
                        // 优先寻找 business_date
                        for (DbQueryDimension dim : qm.getQueryDimensions()) {
                            if (dim.getDimension() != null && "business_date".equals(dim.getDimension().getTimeRole())) {
                                timeRoleCol = dim.getDimension().getEffectiveName() + "$id";
                                break;
                            }
                        }
                        // 退化寻找带 date 的
                        if (timeRoleCol.isEmpty()) {
                            for (DbQueryDimension dim : qm.getQueryDimensions()) {
                                if (dim.getDimension() != null && dim.getDimension().getTimeRole() != null 
                                        && dim.getDimension().getTimeRole().contains("date")) {
                                    timeRoleCol = dim.getDimension().getEffectiveName() + "$id";
                                    break;
                                }
                            }
                        }
                    }

                    String recommendedQuestions = "";
                    if (qm.getAi() != null && qm.getAi().getPrompt() != null && !qm.getAi().getPrompt().isEmpty()) {
                        recommendedQuestions = qm.getAi().getPrompt();
                    } else if (qm.getDescription() != null) {
                        recommendedQuestions = qm.getDescription();
                    }

                    // 避免换行和管道符破坏 Markdown 表格
                    recommendedQuestions = recommendedQuestions.replace("\n", " ").replace("\r", " ").replace("|", "｜");
                    caption = caption.replace("\n", " ").replace("\r", " ").replace("|", "｜");

                    markdown.append(String.format("| %s | %s | %s | %s | %s | dataset.describe_model_internal |\n",
                            modelName, shortAlias, caption, timeRoleCol, recommendedQuestions));
                }
            } catch (Exception e) {
                log.warn("Failed to load model {} for list_models: {}", modelName, e.getMessage());
            }
        }

        markdown.append("\n## 使用规则\n\n");
        markdown.append("- 先根据用户问题选择一个最匹配模型。\n");
        markdown.append("- 不确定字段时，调用 `dataset.describe_model_internal` 获取单模型详情。\n");
        markdown.append("- 不要调用 `dataset.get_metadata` 作为首轮模型发现入口。\n");

        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("format", "markdown");
        dataMap.put("content", markdown.toString());
        dataMap.put("data", null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("data", dataMap);

        return result;
    }
}
