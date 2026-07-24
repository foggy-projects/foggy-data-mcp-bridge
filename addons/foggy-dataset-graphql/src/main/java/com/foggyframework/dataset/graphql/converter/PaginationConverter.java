package com.foggyframework.dataset.graphql.converter;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import graphql.language.Argument;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.language.VariableReference;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 分页参数转换器
 * <p>
 * 支持两种分页模式：
 * 1. 偏移分页：limit + offset
 * 2. 游标分页：first + after / last + before（待实现）
 * </p>
 */
@Slf4j
public class PaginationConverter {

    /**
     * 转换分页参数
     * <p>
     * GraphQL 支持的参数：
     * - limit: Int (偏移分页)
     * - offset: Int (偏移分页)
     * - first: Int (游标分页，向后取N条)
     * - after: String (游标分页，起始游标)
     * - last: Int (游标分页，向前取N条)
     * - before: String (游标分页，结束游标)
     * </p>
     */
    public void convertPagination(
            Map<String, Argument> arguments,
            PagingRequest<DbQueryRequestDef> pagingRequest,
            Map<String, Object> variables
    ) {
        // 优先处理偏移分页
        if (arguments.containsKey("limit") || arguments.containsKey("offset")) {
            convertOffsetPagination(arguments, pagingRequest, variables);
        }
        // 游标分页
        else if (arguments.containsKey("first") || arguments.containsKey("last")) {
            convertCursorPagination(arguments, pagingRequest, variables);
        }
        // 默认分页
        else {
            pagingRequest.setPage(1);
            pagingRequest.setPageSize(10);
        }
    }

    /**
     * 转换偏移分页
     * <p>
     * GraphQL: limit: 20, offset: 40
     * DSL: start: 40, limit: 20
     * </p>
     */
    private void convertOffsetPagination(
            Map<String, Argument> arguments,
            PagingRequest<DbQueryRequestDef> pagingRequest,
            Map<String, Object> variables
    ) {
        Integer limit = null;
        Integer offset = null;

        if (arguments.containsKey("limit")) {
            limit = extractIntValue(arguments.get("limit"), variables);
        }

        if (arguments.containsKey("offset")) {
            offset = extractIntValue(arguments.get("offset"), variables);
        }

        // 设置分页参数
        if (limit != null) {
            pagingRequest.setLimit(limit);
            pagingRequest.setPageSize(limit);
        }

        if (offset != null) {
            pagingRequest.setStart(offset);
            // 根据 offset 和 limit 计算 page
            if (limit != null && limit > 0) {
                int page = (offset / limit) + 1;
                pagingRequest.setPage(page);
            }
        }

        log.debug("偏移分页: limit={}, offset={}", limit, offset);
    }

    /**
     * 转换游标分页
     * <p>
     * GraphQL: first: 20, after: "cursor_abc"
     * DSL: 需要解码游标，转换为 slice 条件 + limit
     * </p>
     *
     * TODO: 完整的游标分页实现需要：
     * 1. 游标编解码器（CursorCodec）
     * 2. 将游标转换为 slice 过滤条件
     * 3. 在响应中生成新的游标
     */
    private void convertCursorPagination(
            Map<String, Argument> arguments,
            PagingRequest<DbQueryRequestDef> pagingRequest,
            Map<String, Object> variables
    ) {
        Integer first = null;
        Integer last = null;
        String after = null;
        String before = null;

        if (arguments.containsKey("first")) {
            first = extractIntValue(arguments.get("first"), variables);
        }

        if (arguments.containsKey("last")) {
            last = extractIntValue(arguments.get("last"), variables);
        }

        if (arguments.containsKey("after")) {
            after = extractStringValue(arguments.get("after"), variables);
        }

        if (arguments.containsKey("before")) {
            before = extractStringValue(arguments.get("before"), variables);
        }

        // 暂时简化处理：将 first 映射为 limit
        // 完整实现需要解码 after 游标并添加到 slice 条件
        if (first != null) {
            // 多取1条用于判断 hasNextPage
            pagingRequest.setLimit(first + 1);
            pagingRequest.setPageSize(first + 1);
            pagingRequest.setPage(1);

            log.debug("游标分页（向后）: first={}, after={}", first, after);

            if (after != null) {
                log.warn("游标分页暂未完整实现，after 参数将被忽略: {}", after);
                // TODO: 解码 after 游标，添加到 slice 条件
                // List<SliceRequestDef> cursorSlices = decodeCursor(after, orderBy);
                // pagingRequest.getParam().getSlice().addAll(cursorSlices);
            }
        } else if (last != null) {
            // 向前分页需要反转排序
            pagingRequest.setLimit(last + 1);
            pagingRequest.setPageSize(last + 1);
            pagingRequest.setPage(1);

            log.debug("游标分页（向前）: last={}, before={}", last, before);

            if (before != null) {
                log.warn("游标分页暂未完整实现，before 参数将被忽略: {}", before);
                // TODO: 解码 before 游标，反转排序，添加到 slice 条件
            }
        }
    }

    /**
     * 提取整数值
     */
    private Integer extractIntValue(Argument argument, Map<String, Object> variables) {
        if (argument.getValue() instanceof IntValue) {
            return ((IntValue) argument.getValue()).getValue().intValue();
        } else if (argument.getValue() instanceof VariableReference) {
            String varName = ((VariableReference) argument.getValue()).getName();
            Object value = variables.get(varName);
            return value instanceof Number ? ((Number) value).intValue() : null;
        }
        return null;
    }

    /**
     * 提取字符串值
     */
    private String extractStringValue(Argument argument, Map<String, Object> variables) {
        if (argument.getValue() instanceof StringValue) {
            return ((StringValue) argument.getValue()).getValue();
        } else if (argument.getValue() instanceof VariableReference) {
            String varName = ((VariableReference) argument.getValue()).getName();
            Object value = variables.get(varName);
            return value != null ? value.toString() : null;
        }
        return null;
    }
}
