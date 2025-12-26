package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.dataset.jdbc.model.def.dict.DbDictDef;

import java.util.Collection;

/**
 * 字典服务接口
 *
 * 提供字典的注册、查询功能，供 fsscript 中的 registerDict 函数调用。
 */
public interface DbModelDictService {

    /**
     * 注册字典
     *
     * @param dictDef 字典定义
     * @return 字典ID
     * @throws IllegalArgumentException 如果字典ID已存在
     */
    String registerDict(DbDictDef dictDef);

    /**
     * 根据ID获取字典定义
     *
     * @param dictId 字典ID
     * @return 字典定义，如果不存在返回null
     */
    DbDictDef getDictById(String dictId);

    /**
     * 检查字典是否存在
     *
     * @param dictId 字典ID
     * @return 是否存在
     */
    boolean hasDict(String dictId);

    /**
     * 获取所有已注册的字典
     *
     * @return 所有字典定义
     */
    Collection<DbDictDef> getAllDicts();

    /**
     * 清除所有字典（用于测试或重新加载）
     */
    void clearAll();

    /**
     * 根据字典ID和value获取label
     *
     * @param dictId 字典ID
     * @param value  值
     * @return label，如果字典或值不存在返回null
     */
    default String getLabelByValue(String dictId, Object value) {
        DbDictDef dict = getDictById(dictId);
        return dict != null ? dict.getLabelByValue(value) : null;
    }

    /**
     * 根据字典ID和label获取value
     *
     * @param dictId 字典ID
     * @param label  标签
     * @return value，如果字典或标签不存在返回null
     */
    default Object getValueByLabel(String dictId, String label) {
        DbDictDef dict = getDictById(dictId);
        return dict != null ? dict.getValueByLabel(label) : null;
    }
}
