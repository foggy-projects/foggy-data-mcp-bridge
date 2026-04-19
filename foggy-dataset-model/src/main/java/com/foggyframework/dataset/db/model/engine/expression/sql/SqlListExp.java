package com.foggyframework.dataset.db.model.engine.expression.sql;

import com.foggyframework.dataset.db.model.engine.expression.SqlFragment;
import com.foggyframework.dataset.db.model.spi.DbColumnType;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.exp.NullExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SQL 列表字面量表达式。
 * <p>
 * 仅用作 {@code IN} / {@code NOT IN} 运算符的 RHS，渲染为 SQL 的
 * {@code (v1, v2, v3)} 括号列表。
 * </p>
 *
 * <p>典型来源：fsscript grammar 中的 {@code (1, 2, 3)}（{@code UnresolvedFunCall("()")}
 * 的多参数形态）。由 {@link com.foggyframework.dataset.db.model.engine.expression.SqlExpFactory}
 * 在 {@code createSqlExp("()", args)} 检测到 args.size() &gt;= 2 时生成。</p>
 *
 * <p>空列表 {@code ()} 在 {@link com.foggyframework.dataset.db.model.engine.expression.SqlExpFactory}
 * IN / NOT IN 处理分支被拒绝：{@code IN () } 在多数数据库里是语法错误；想要"恒为 false"
 * 请显式写 {@code 1 == 0}。</p>
 *
 * @author Foggy
 * @since 8.1.11.beta
 */
public class SqlListExp extends AbstractExp<String> {

    private static final long serialVersionUID = 1L;

    private final List<Exp> items;

    public SqlListExp(List<Exp> items) {
        super("()");
        this.items = items == null ? Collections.emptyList() : new ArrayList<>(items);
    }

    public List<Exp> getItems() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        List<SqlFragment> fragments = new ArrayList<>(items.size());
        for (Exp item : items) {
            // NullExp 显式渲染为 SQL `NULL` 字面量，避免 evalResult==null 被下方分支静默丢弃
            if (item instanceof NullExp) {
                fragments.add(SqlFragment.ofLiteral("NULL"));
                continue;
            }
            Object r = item.evalResult(evaluator);
            if (r instanceof SqlFragment) {
                fragments.add((SqlFragment) r);
            } else if (r != null) {
                // 极端情况：元素 eval 出非 SqlFragment，按字面量兜底
                fragments.add(SqlFragment.ofLiteral(String.valueOf(r)));
            } else {
                // eval 结果为 null 也按 SQL NULL 处理
                fragments.add(SqlFragment.ofLiteral("NULL"));
            }
        }

        SqlFragment f = new SqlFragment();
        f.setSql("("
                + fragments.stream().map(SqlFragment::getSql).collect(Collectors.joining(", "))
                + ")");
        // 列表里如果夹带列引用，也要把依赖传给上层；IN 的 LHS 依赖由 SqlBinaryExp 合并
        fragments.forEach(frag -> f.getReferencedColumns().addAll(frag.getReferencedColumns()));
        // 列表本身不单独参与类型推断，留给 IN/NOT IN 产出 BOOL
        f.setInferredType(DbColumnType.UNKNOWN);
        // 继承聚合 / 窗口标记（列表里理论上不应含聚合，但保守处理）
        f.setHasAggregate(fragments.stream().anyMatch(SqlFragment::isHasAggregate));
        f.setHasWindow(fragments.stream().anyMatch(SqlFragment::isHasWindow));
        return f;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return SqlFragment.class;
    }

    @Override
    public String toString() {
        return "[SqlList:(" + items.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")]";
    }
}
