package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.model.semantic.member.SyntheticMemberQueryModelResolver;
import com.foggyframework.dataset.model.semantic.member.permission.SyntheticMemberEffectivePermission;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在 SQL 构建阶段执行 synthetic member-QM 的内部 queryBuilder 脚本。
 * <p>
 * 按 TM → QM 顺序执行 queryBuilder 列表，每个 queryBuilder 可在 JdbcQuery 上追加 WHERE 条件。
 * <p>
 * 该步骤在 beforeQuery 后期执行（@Order(100)），确保所有 patch 已合并完毕。
 * queryBuilder 脚本访问的是 synthetic member-QM 的字段空间，不能回退到原业务 QM。
 */
@Component
@Order(100)
@Slf4j
public class SyntheticMemberQueryBuilderStep implements DataSetResultStep {

    @Resource
    private ApplicationContext applicationContext;

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        if (ctx == null) {
            return CONTINUE;
        }

        QueryModel queryModel = ctx.getQueryModel();
        if (!isSyntheticMemberQueryModel(queryModel)) {
            return CONTINUE;
        }

        SyntheticMemberEffectivePermission effective = resolveEffectivePermission(ctx);
        if (effective == null || !effective.hasQueryBuilders()) {
            return CONTINUE;
        }

        List<FsscriptFunction> builders = effective.getQueryBuilders();
        for (FsscriptFunction builder : builders) {
            executeQueryBuilder(builder, ctx);
        }

        return CONTINUE;
    }

    private void executeQueryBuilder(FsscriptFunction builder, ModelResultContext ctx) {
        try {
            DefaultExpEvaluator ee = DefaultExpEvaluator.newInstance(applicationContext);
            ee.setVar("context", ctx);
            builder.autoApply(ee);
        } catch (Exception e) {
            log.error("synthetic member-QM queryBuilder 执行异常", e);
            throw e;
        }
    }

    private boolean isSyntheticMemberQueryModel(QueryModel queryModel) {
        return queryModel != null
                && StringUtils.isNotEmpty(queryModel.getName())
                && queryModel.getName().contains(SyntheticMemberQueryModelResolver.MODEL_SEPARATOR);
    }

    private SyntheticMemberEffectivePermission resolveEffectivePermission(ModelResultContext ctx) {
        if (ctx.getExtData() == null) {
            return null;
        }
        Object obj = ctx.getExtData().get(SyntheticMemberInternalPatchStep.EFFECTIVE_PERMISSION_KEY);
        if (obj instanceof SyntheticMemberEffectivePermission ep) {
            return ep;
        }
        return null;
    }
}
