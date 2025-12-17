package com.foggyframework.dataset.jdbc.model.service.impl;

import com.foggyframework.conversion.FsscriptConversionService;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.def.dict.JdbcDictDef;
import com.foggyframework.dataset.jdbc.model.spi.JdbcModelDictService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字典服务实现
 *
 * 提供 fsscript 中 registerDict 函数的后端支持，管理全局字典注册表。
 *
 * 使用方式（在 fsscript 中）：
 * <pre>
 * import { registerDict } from '@jdbcModelDictService';
 *
 * export const dicts = {
 *     payment_method: registerDict({
 *         id: 'payment_method',
 *         caption: '支付方式',
 *         items: [
 *             { value: '1', label: '现付' },
 *             { value: '2', label: '到付' }
 *         ]
 *     })
 * }
 * </pre>
 */
@Slf4j
@Service("jdbcModelDictService")
public class JdbcModelDictServiceImpl implements JdbcModelDictService {

    /**
     * 字典注册表：dictId -> JdbcDictDef
     */
    private final Map<String, JdbcDictDef> dictRegistry = new ConcurrentHashMap<>();

    /**
     * 注册字典（供 fsscript 调用）
     *
     * 该方法会被 fsscript 中的 registerDict 函数调用，参数为 Map 类型，
     * 需要转换为 JdbcDictDef。
     *
     * @param dictDefMap 字典定义（Map 形式，由 fsscript 传入）
     * @return 字典ID
     */
    public String registerDict(Map<String, Object> dictDefMap) {
        JdbcDictDef dictDef = FsscriptConversionService.getSharedInstance().convert(dictDefMap, JdbcDictDef.class);
        return registerDict(dictDef);
    }

    @Override
    public String registerDict(JdbcDictDef dictDef) {
        RX.notNull(dictDef, "字典定义不能为空");
        RX.hasText(dictDef.getId(), "字典ID不能为空");

        String dictId = dictDef.getId();

        // 检查ID是否重复
        if (dictRegistry.containsKey(dictId)) {
            JdbcDictDef existing = dictRegistry.get(dictId);
            // 如果是同一个对象，直接返回（支持重复加载同一个 fsscript 文件）
            if (existing == dictDef) {
                return dictId;
            }
            log.warn("字典ID [{}] 已存在，将被覆盖。原字典: {}, 新字典: {}",
                    dictId, existing.getCaption(), dictDef.getCaption());
        }

        dictRegistry.put(dictId, dictDef);
        log.debug("注册字典: {} ({}), 共 {} 项",
                dictId, dictDef.getCaption(),
                dictDef.getItems() != null ? dictDef.getItems().size() : 0);

        return dictId;
    }

    @Override
    public JdbcDictDef getDictById(String dictId) {
        if (StringUtils.isEmpty(dictId)) {
            return null;
        }
        return dictRegistry.get(dictId);
    }

    @Override
    public boolean hasDict(String dictId) {
        return dictId != null && dictRegistry.containsKey(dictId);
    }

    @Override
    public Collection<JdbcDictDef> getAllDicts() {
        return dictRegistry.values();
    }

    @Override
    public void clearAll() {
        dictRegistry.clear();
        log.info("已清除所有字典注册");
    }
}
