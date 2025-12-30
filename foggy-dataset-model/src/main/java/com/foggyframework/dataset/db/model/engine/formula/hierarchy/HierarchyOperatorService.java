package com.foggyframework.dataset.db.model.engine.formula.hierarchy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 层级操作符服务
 *
 * <p>管理所有父子维度层级操作符，提供按名称查找功能
 */
public class HierarchyOperatorService {

    private final Map<String, HierarchyOperator> name2Operator = new HashMap<>();

    public HierarchyOperatorService() {
        // 注册内置操作符
        register(new ChildrenOfOperator());
        register(new DescendantsOfOperator());
        register(new SelfAndDescendantsOfOperator());
    }

    public HierarchyOperatorService(List<HierarchyOperator> operators) {
        this();
        if (operators != null) {
            operators.forEach(this::register);
        }
    }

    /**
     * 注册层级操作符
     */
    public void register(HierarchyOperator operator) {
        for (String name : operator.getNameList()) {
            name2Operator.put(name.toLowerCase(), operator);
        }
    }

    /**
     * 根据操作符名称获取层级操作符
     *
     * @param operatorName 操作符名称
     * @return 层级操作符，如果不存在返回 null
     */
    public HierarchyOperator get(String operatorName) {
        if (operatorName == null) {
            return null;
        }
        return name2Operator.get(operatorName.toLowerCase());
    }

    /**
     * 判断是否为层级操作符
     *
     * @param operatorName 操作符名称
     * @return true 如果是层级操作符
     */
    public boolean isHierarchyOperator(String operatorName) {
        return get(operatorName) != null;
    }
}
