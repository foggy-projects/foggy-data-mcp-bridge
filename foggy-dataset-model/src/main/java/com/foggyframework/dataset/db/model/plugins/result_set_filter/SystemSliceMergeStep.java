package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 在权限检查完成后，将 system_slice 合并到请求 slice 中
 * <p>
 * system_slice 是系统注入的过滤条件（如 Odoo ir.rule），
 * 绕过 FieldAccessPermissionStep 的字段权限校验。
 * </p>
 * <p>
 * 对齐 Python 引擎的 system_slice 语义：
 * <ol>
 *   <li>FieldAccessPermissionStep (@Order -25) 只校验用户 slice</li>
 *   <li>本步骤 (@Order -15) 在权限校验后合并 system_slice</li>
 *   <li>后续步骤（SQL 构建）看到的是合并后的完整 slice</li>
 * </ol>
 *
 * @since 8.2.0
 */
@Slf4j
@Component
@Order(-15)
public class SystemSliceMergeStep implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        if (ctx == null) {
            return CONTINUE;
        }

        List<SliceRequestDef> systemSlice = ctx.getSystemSlice();
        if (systemSlice == null || systemSlice.isEmpty()) {
            return CONTINUE;
        }

        DbQueryRequestDef request = ctx.getRequest().getParam();

        // 合并：用户 slice + system_slice
        List<SliceRequestDef> mergedSlice = new ArrayList<>();
        if (request.getSlice() != null) {
            mergedSlice.addAll(request.getSlice());
        }
        mergedSlice.addAll(systemSlice);
        request.setSlice(mergedSlice);

        if (log.isDebugEnabled()) {
            log.debug("Merged {} system_slice conditions into request slice (total: {})",
                    systemSlice.size(), mergedSlice.size());
        }

        return CONTINUE;
    }
}
