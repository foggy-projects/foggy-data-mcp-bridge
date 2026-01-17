package com.foggyframework.dataset.graphql.converter;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import graphql.language.*;
import graphql.parser.Parser;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GraphQL 查询 → DSL 转换器
 * <p>
 * 将 GraphQL 查询语句转换为 Foggy Dataset Model 的 JSON DSL 格式
 * </p>
 *
 * @author Foggy Framework
 */
@Slf4j
public class GraphqlToDslConverter {

    private final SelectionSetConverter selectionSetConverter = new SelectionSetConverter();
    private final ArgumentConverter argumentConverter = new ArgumentConverter();
    private final PaginationConverter paginationConverter = new PaginationConverter();

    /**
     * 转换 GraphQL 查询字符串为 DSL 请求
     *
     * @param graphqlQuery GraphQL 查询字符串
     * @param variables    查询变量
     * @return DSL 分页请求对象
     */
    public PagingRequest<DbQueryRequestDef> convert(String graphqlQuery, Map<String, Object> variables) {
        // 解析 GraphQL 查询
        Parser parser = new Parser();
        Document document = parser.parseDocument(graphqlQuery);

        // 获取第一个操作（通常是 query）
        OperationDefinition operation = (OperationDefinition) document.getDefinitions().get(0);

        // 获取根查询字段（如 factOrder）
        Field rootField = (Field) operation.getSelectionSet().getSelections().get(0);

        // 创建 DSL 请求对象
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);

        // 设置查询模型名称（从字段名推断）
        String queryModelName = deriveQueryModelName(rootField.getName());
        queryRequest.setQueryModel(queryModelName);

        // 转换参数
        Map<String, Argument> arguments = rootField.getArguments().stream()
                .collect(Collectors.toMap(Argument::getName, arg -> arg));

        // 1. 转换字段选择 (columns)
        SelectionSet selectionSet = rootField.getSelectionSet();
        if (selectionSet != null) {
            List<String> columns = selectionSetConverter.convertToColumns(selectionSet, rootField.getName());
            queryRequest.setColumns(columns);
        }

        // 2. 转换过滤条件 (where → slice)
        if (arguments.containsKey("where")) {
            queryRequest.setSlice(
                    argumentConverter.convertWhereToSlice(
                            (ObjectValue) arguments.get("where").getValue(),
                            variables
                    )
            );
        }

        // 3. 转换排序 (orderBy)
        if (arguments.containsKey("orderBy")) {
            queryRequest.setOrderBy(
                    argumentConverter.convertOrderBy(
                            (ArrayValue) arguments.get("orderBy").getValue(),
                            variables
                    )
            );
        }

        // 4. 转换分页参数
        paginationConverter.convertPagination(arguments, pagingRequest, variables);

        // 5. 检查是否需要返回总数
        if (selectionSet != null) {
            boolean needsTotal = selectionSet.getSelections().stream()
                    .filter(sel -> sel instanceof Field)
                    .map(sel -> ((Field) sel).getName())
                    .anyMatch(name -> "totalCount".equals(name) || "pageInfo".equals(name));
            queryRequest.setReturnTotal(needsTotal);
        }

        log.debug("GraphQL 转换完成: query={}, model={}, columns={}",
                rootField.getName(), queryModelName, queryRequest.getColumns());

        return pagingRequest;
    }

    /**
     * 从 GraphQL 字段名推断查询模型名称
     * <p>
     * 示例：factOrder → FactOrderQueryModel
     * </p>
     */
    private String deriveQueryModelName(String fieldName) {
        // 移除 _aggregate 后缀
        String baseName = fieldName.replaceAll("_aggregate$", "");

        // 首字母大写，添加 QueryModel 后缀
        String modelName = Character.toUpperCase(baseName.charAt(0)) + baseName.substring(1) + "QueryModel";

        return modelName;
    }
}
