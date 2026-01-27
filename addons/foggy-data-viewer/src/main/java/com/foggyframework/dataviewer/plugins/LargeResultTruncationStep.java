package com.foggyframework.dataviewer.plugins;

import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultStep;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 大数据集截断步骤
 *
 * <p>当 MCP 查询返回的数据量过大时（单元格数超过阈值），自动截断结果并生成查看链接。
 * 这样可以避免大量数据占用 LLM 的上下文窗口。
 *
 * <h3>工作原理：</h3>
 * <ul>
 *   <li>检测查询来源：仅对标记为 MCP 来源的查询生效</li>
 *   <li>计算数据量：行数 × 列数 = 单元格总数</li>
 *   <li>超过阈值时：截断数据 + 生成查看链接</li>
 *   <li>返回格式：截断后的数据 + truncationInfo 元数据</li>
 * </ul>
 *
 * <h3>配置项：</h3>
 * <pre>
 * foggy:
 *   data-viewer:
 *     thresholds:
 *       cell-threshold-for-truncation: 10000  # 单元格阈值
 *       truncated-row-limit: 100              # 截断后保留行数
 * </pre>
 *
 * @author foggy-data-viewer
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "foggy.data-viewer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LargeResultTruncationStep implements DataSetResultStep {

    private final QueryCacheService queryCacheService;
    private final DataViewerProperties properties;

    @Override
    public int process(ModelResultContext ctx) {
        // 1. 检查是否来自 MCP
        if (!isFromMcp(ctx)) {
            return CONTINUE;
        }

        // 2. 检查是否有查询结果
        if (ctx.getPagingResult() == null || ctx.getPagingResult().getItems() == null) {
            return CONTINUE;
        }

        List<?> items = ctx.getPagingResult().getItems();
        if (items.isEmpty()) {
            return CONTINUE;
        }

        // 3. 计算数据量（行数 × 列数）
        int rowCount = items.size();
        int columnCount = getColumnCount(ctx, items);
        long cellCount = (long) rowCount * columnCount;

        int threshold = properties.getThresholds().getCellThresholdForTruncation();

        log.debug("MCP query result: rows={}, columns={}, cells={}, threshold={}",
                rowCount, columnCount, cellCount, threshold);

        // 4. 如果未超过阈值，直接返回
        if (cellCount <= threshold) {
            return CONTINUE;
        }

        // 5. 数据量过大，执行截断
        log.info("MCP query result exceeds threshold: cells={} > {}, truncating to {} rows",
                cellCount, threshold, properties.getThresholds().getTruncatedRowLimit());

        truncateAndAddLink(ctx, rowCount, columnCount);

        return CONTINUE;
    }

    /**
     * 检查是否来自 MCP 查询
     */
    private boolean isFromMcp(ModelResultContext ctx) {
        Map<String, Object> extData = ctx.getExtData();
        if (extData == null) {
            return false;
        }

        Object fromMcp = extData.get("fromMcp");
        return Boolean.TRUE.equals(fromMcp);
    }

    /**
     * 获取列数（从第一行数据推断）
     */
    private int getColumnCount(ModelResultContext ctx, List<?> items) {
        if (items.isEmpty()) {
            return 0;
        }

        Object firstRow = items.get(0);
        if (firstRow instanceof Map) {
            return ((Map<?, ?>) firstRow).size();
        }

        // 如果不是 Map，使用请求的 columns 长度
        if (ctx.getRequest() != null && ctx.getRequest().getParam() != null) {
            List<String> columns = ctx.getRequest().getParam().getColumns();
            if (columns != null) {
                return columns.size();
            }
        }

        return 0;
    }

    /**
     * 截断数据并添加查看链接
     */
    @SuppressWarnings("unchecked")
    private void truncateAndAddLink(ModelResultContext ctx, int originalRowCount, int columnCount) {
        int truncatedRowLimit = properties.getThresholds().getTruncatedRowLimit();

        // 1. 截断数据
        List<?> items = ctx.getPagingResult().getItems();
        List<?> truncatedItems = items.subList(0, Math.min(truncatedRowLimit, items.size()));
        ctx.getPagingResult().setItems((List<Map<String, Object>>) truncatedItems);

        // 2. 生成查询链接
        String queryId = null;
        String viewerUrl = null;
        String apiUrl = null;

        try {
            // 构建缓存请求
            QueryCacheService.OpenInViewerRequest cacheRequest = buildCacheRequest(ctx);
            CachedQueryContext cachedQuery = queryCacheService.cacheQuery(cacheRequest, ctx.getAuthorization());

            queryId = cachedQuery.getQueryId();
            viewerUrl = buildViewerUrl(queryId);
            apiUrl = buildApiUrl(queryId);

            log.info("Created query link for truncated result: queryId={}, viewerUrl={}", queryId, viewerUrl);

        } catch (Exception e) {
            log.error("Failed to create query link for truncated result", e);
            // 链接生成失败不影响主流程，继续返回截断后的数据
        }

        // 3. 在 extData 中添加截断信息（供响应构建使用）
        Map<String, Object> extData = ctx.getExtData();
        if (extData == null) {
            extData = new HashMap<>();
            ctx.setExtData(extData);
        }

        Map<String, Object> truncationInfo = new HashMap<>();
        truncationInfo.put("truncated", true);
        truncationInfo.put("originalRowCount", originalRowCount);
        truncationInfo.put("truncatedRowCount", truncatedRowLimit);
        truncationInfo.put("columnCount", columnCount);
        truncationInfo.put("cellCount", (long) originalRowCount * columnCount);
        truncationInfo.put("message", String.format(
                "数据量较大（%d 行 × %d 列 = %d 单元格），已自动截断为 %d 行。",
                originalRowCount, columnCount, (long) originalRowCount * columnCount, truncatedRowLimit
        ));

        if (viewerUrl != null) {
            truncationInfo.put("viewerUrl", viewerUrl);
            truncationInfo.put("apiUrl", apiUrl);
            truncationInfo.put("hint", "您可以访问上述链接查看完整数据，或通过 API 分页获取（参数：start, limit）");
        }

        extData.put("truncationInfo", truncationInfo);
    }

    /**
     * 从 ModelResultContext 构建缓存请求
     */
    private QueryCacheService.OpenInViewerRequest buildCacheRequest(ModelResultContext ctx) {
        QueryCacheService.OpenInViewerRequest request = new QueryCacheService.OpenInViewerRequest();

        if (ctx.getRequest() != null && ctx.getRequest().getParam() != null) {
            var param = ctx.getRequest().getParam();

            // 从原始查询中提取模型名称
            String model = ctx.getQueryModel() != null
                    ? ctx.getQueryModel().getName()
                    : "unknown";

            request.setModel(model);
            request.setColumns(param.getColumns());
            request.setSlice(param.getSlice());
            request.setGroupBy(param.getGroupBy());
            request.setOrderBy(param.getOrderBy());
            request.setCalculatedFields(param.getCalculatedFields());
            request.setTitle("查询结果 - " + model);
        }

        return request;
    }

    /**
     * 构建 data-viewer 链接
     */
    private String buildViewerUrl(String queryId) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080/data-viewer";
        }
        return baseUrl + "/view/" + queryId;
    }

    /**
     * 构建 API 链接
     */
    private String buildApiUrl(String queryId) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080/data-viewer";
        }
        return baseUrl + "/api/query/" + queryId + "/data";
    }
}
