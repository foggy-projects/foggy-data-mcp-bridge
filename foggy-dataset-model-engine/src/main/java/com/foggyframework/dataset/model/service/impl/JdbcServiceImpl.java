package com.foggyframework.dataset.model.service.impl;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.common.query.DimensionDataQueryForm;
import com.foggyframework.dataset.model.common.result.DbDataItem;
import com.foggyframework.dataset.model.def.dict.DbDictDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.dictionary.DictionaryBinding;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.semantic.member.SyntheticMemberQueryModelDescriptor;
import com.foggyframework.dataset.model.semantic.member.SyntheticMemberQueryModelResolver;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.semantic.permission.FieldPermissionResolution;
import com.foggyframework.dataset.model.semantic.permission.FieldPermissionResolver;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionException;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionService;
import com.foggyframework.dataset.model.semantic.permission.PermissionDecision;
import com.foggyframework.dataset.model.semantic.permission.RequestIdentity;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.service.JdbcService;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.spi.DbQueryDimension;
import com.foggyframework.dataset.model.spi.DbModelDictService;
import com.foggyframework.dataset.model.spi.DbProperty;
import com.foggyframework.dataset.model.spi.DbPropertyColumn;
import com.foggyframework.dataset.model.spi.DbQueryColumn;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JDBC 查询服务实现
 * <p>
 * 纯查询服务，不包含 Step 处理逻辑。
 * 如需完整的查询生命周期（beforeQuery -> query -> process），
 * 请使用 {@link AdvancedQueryFacade}。
 * </p>
 *
 * @see JdbcServiceImpl
 */
public class JdbcServiceImpl implements JdbcService {

    @Resource
    QueryModelLoader queryModelLoader;
    @Resource
    SystemBundlesContext systemBundlesContext;
    @Resource
    AdvancedQueryFacade queryFacade;
    @Resource
    DbModelDictService dbModelDictService;
    @Resource
    ModelPermissionService modelPermissionService;
    @Resource
    FieldPermissionResolver fieldPermissionResolver;

    private final SyntheticMemberQueryModelResolver syntheticMemberQueryModelResolver = new SyntheticMemberQueryModelResolver();

    @Override
    public PagingResultImpl<DbDataItem> queryDimensionData(PagingRequest<DimensionDataQueryForm> form) {
        return queryDimensionData(form, null, null);
    }

    @Override
    public PagingResultImpl<DbDataItem> queryDimensionData(
            PagingRequest<DimensionDataQueryForm> form,
            String authorization,
            String namespace
    ) {
        DimensionDataQueryForm qf = form.getParam();
        QueryModel sourceModel = queryModelLoader.getJdbcQueryModel(qf.getQueryModel(), namespace);
        PagingResultImpl<DbDataItem> dictionaryMembers = queryDictionaryMembers(
                form, qf, sourceModel, authorization, namespace);
        if (dictionaryMembers != null) {
            return dictionaryMembers;
        }
        SyntheticMemberQueryModelDescriptor descriptor = syntheticMemberQueryModelResolver.resolve(
                queryModelLoader,
                qf.getQueryModel(),
                qf.getDimension(),
                namespace
        );

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(descriptor.syntheticModelName());
        queryRequest.setColumns(List.of("id", "caption"));
        queryRequest.setReturnTotal(true);
        queryRequest.setExtData(qf.getExtData());
        SliceRequestDef hierarchySlice = resolveHierarchySlice(qf.getHierarchy());
        if (hierarchySlice != null) {
            queryRequest.setSlice(List.of(hierarchySlice));
        }

        PagingRequest<DbQueryRequestDef> memberRequest = form.copy(queryRequest);
        ModelResultContext context = new ModelResultContext(memberRequest, null);
        context.setNamespace(namespace);
        context.setRequestIdentity(RequestIdentity.fromAuthorization(authorization));
        context.setPermissionAction(PermissionAction.MEMBER_QUERY);
        PagingResultImpl rawResult = queryFacade.queryModelResult(context).getPagingResult();

        List<DbDataItem> dataItems = new ArrayList<>();
        if (rawResult.getItems() != null) {
            for (Object item : rawResult.getItems()) {
                if (item instanceof Map<?, ?> row) {
                    dataItems.add(new DbDataItem(row.get("id"), row.get("caption") == null ? null : String.valueOf(row.get("caption"))));
                }
            }
        }

        return PagingResultImpl.of(
                dataItems,
                memberRequest.getStart(),
                memberRequest.getLimit(),
                rawResult.getTotalData(),
                Math.toIntExact(rawResult.getTotal())
        );
    }

    private PagingResultImpl<DbDataItem> queryDictionaryMembers(
            PagingRequest<DimensionDataQueryForm> form,
            DimensionDataQueryForm query,
            QueryModel sourceModel,
            String authorization,
            String namespace) {
        String fieldName = stripMemberSuffix(query.getDimension());
        DbQueryColumn queryColumn = sourceModel.findJdbcQueryColumnByName(fieldName, false);
        DbPropertyColumn propertyColumn = queryColumn == null
                ? null
                : queryColumn.getSelectColumn().getDecorate(DbPropertyColumn.class);
        DbProperty property = propertyColumn == null ? null : propertyColumn.getProperty();
        if (property == null || StringUtils.isEmpty(property.getDictRef())) {
            return null;
        }
        if (StringUtils.isNotEmpty(query.getHierarchy())) {
            throw RX.throwAUserTip("DICT_MEMBERS_HIERARCHY_UNSUPPORTED: 静态字典字段不支持层级成员查询: "
                    + fieldName);
        }

        RequestIdentity identity = RequestIdentity.fromAuthorization(authorization);
        PermissionDecision permission = modelPermissionService.evaluate(
                sourceModel, namespace, PermissionAction.MEMBER_QUERY, identity, null);
        if (!permission.isAllow()) {
            throw ModelPermissionException.denied();
        }
        ModelResultContext.SecurityContext securityContext =
                ModelResultContext.SecurityContext.fromAuthorization(authorization);
        if (!permission.getAttributes().isEmpty()) {
            securityContext.setAttributes(permission.getAttributes());
        }
        FieldPermissionResolution fieldPermission = fieldPermissionResolver.resolve(
                sourceModel, namespace, securityContext, null, null);
        if (fieldPermission.getEffectiveFieldAccess() != null
                && !fieldPermission.getEffectiveFieldAccess().contains(fieldName)) {
            throw ModelPermissionException.denied();
        }

        DbDictDef dictionary = dbModelDictService.getDictById(property.getDictRef());
        if (dictionary == null) {
            throw RX.throwAUserTip("DICT_NOT_FOUND: 字段 " + fieldName
                    + " 引用的字典未注册: " + property.getDictRef());
        }

        // Static dictionary items are model metadata, not fact-table rows, so
        // row predicates do not apply. Normalize ids with the source formatter
        // to keep members and label-filter parameters on the same Java type.
        DictionaryBinding binding = DictionaryBinding.bind(
                dictionary, queryColumn.getSelectColumn());
        List<DbDataItem> allItems = binding.getValueToLabel().entrySet().stream()
                .map(item -> new DbDataItem(item.getKey(), item.getValue()))
                .toList();
        int start = form.getStart() == null ? 0 : Math.max(0, form.getStart());
        int requestedLimit = form.getLimit() == null ? 10 : form.getLimit();
        int limit = requestedLimit < 0 ? allItems.size() : requestedLimit;
        int from = Math.min(start, allItems.size());
        int to = Math.min(from + limit, allItems.size());
        List<DbDataItem> page = new ArrayList<>(allItems.subList(from, to));
        return PagingResultImpl.of(page, start, limit, null, allItems.size());
    }

    private String stripMemberSuffix(String fieldName) {
        if (fieldName == null) {
            return null;
        }
        if (fieldName.endsWith("$caption") || fieldName.endsWith("$id")) {
            return fieldName.substring(0, fieldName.lastIndexOf('$'));
        }
        return fieldName;
    }

    @Override
    public PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form) {
        DbQueryRequestDef qf = form.getParam();
        String queryModelName = qf.getQueryModel();
        QueryModel jdbcQueryModel = queryModelLoader.getJdbcQueryModel(queryModelName, null);

        PagingResultImpl p = jdbcQueryModel.query(systemBundlesContext, form).getPagingResult();
        return p;
    }

    @Override
    public DbQueryResult queryModelResult(PagingRequest<DbQueryRequestDef> form) {
        DbQueryRequestDef qf = form.getParam();
        String queryModelName = qf.getQueryModel();
        QueryModel jdbcQueryModel = queryModelLoader.getJdbcQueryModel(queryModelName, null);

        DbQueryResult p = jdbcQueryModel.query(systemBundlesContext, form);
        return p;
    }

    /**
     * Converts the simple-entry hierarchy string into the canonical slice used by synthetic member-QM.
     * Supported forms include plain member id, {@code op:value}, and {@code op(value[,maxDepth])}.
     * When no operator is provided, it defaults to {@code selfAndDescendantsOf} and always targets the
     * synthetic model's canonical {@code id} field.
     */
    private SliceRequestDef resolveHierarchySlice(String hierarchy) {
        if (StringUtils.isEmpty(hierarchy)) {
            return null;
        }

        String trimmed = hierarchy.trim();
        String op = "selfAndDescendantsOf";
        String value = trimmed;
        Integer maxDepth = null;

        int colonIndex = trimmed.indexOf(':');
        if (colonIndex > 0) {
            op = trimmed.substring(0, colonIndex).trim();
            value = trimmed.substring(colonIndex + 1).trim();
        } else if (trimmed.endsWith(")") && trimmed.contains("(")) {
            int openIndex = trimmed.indexOf('(');
            op = trimmed.substring(0, openIndex).trim();
            String args = trimmed.substring(openIndex + 1, trimmed.length() - 1).trim();
            int commaIndex = args.indexOf(',');
            if (commaIndex >= 0) {
                value = args.substring(0, commaIndex).trim();
                String depthArg = args.substring(commaIndex + 1).trim();
                if (StringUtils.isNotEmpty(depthArg)) {
                    maxDepth = Integer.valueOf(depthArg);
                }
            } else {
                value = args;
            }
        }

        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("id");
        slice.setOp(op);
        slice.setValue(value);
        slice.setMaxDepth(maxDepth);
        return slice;
    }

}
