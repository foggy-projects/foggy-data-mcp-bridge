package com.foggyframework.dataset.db.model.def.dict;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Field-level runtime dictionary value discovery contract.
 */
@Data
public class DbDictionaryDiscoveryDef {

    public static final String STRATEGY_GROUP_BY = "group_by";
    public static final String STRATEGY_DISTINCT = "distinct";
    public static final int DEFAULT_MAX_VALUES = 50;
    public static final int MAX_ALLOWED_VALUES = 500;
    public static final long DEFAULT_REFRESH_TTL_SECONDS = 3600L;

    @ApiModelProperty("是否开启运行时字典值发现；默认关闭")
    Boolean enabled;

    @ApiModelProperty("发现策略：group_by 或 distinct；默认 group_by")
    String strategy;

    @ApiModelProperty("最大暴露值数量；默认 50，上限 500")
    Integer maxValues;

    @ApiModelProperty("缓存刷新周期，单位秒；默认 3600")
    Long refreshTtlSeconds;

    @ApiModelProperty("是否允许注入 LLM metadata；默认 true")
    Boolean exposeToLlm;

    @ApiModelProperty("是否敏感字段；敏感字段即使开启 discovery 也不向 LLM 暴露值或别名")
    Boolean sensitive;

    @ApiModelProperty("人工治理的业务别名，key 为别名，values 为对应底层值集合")
    Map<String, AliasDef> aliases;

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public String getEffectiveStrategy() {
        return StringUtils.isEmpty(strategy) ? STRATEGY_GROUP_BY : strategy;
    }

    public int getEffectiveMaxValues() {
        return maxValues == null ? DEFAULT_MAX_VALUES : maxValues;
    }

    public long getEffectiveRefreshTtlSeconds() {
        return refreshTtlSeconds == null ? DEFAULT_REFRESH_TTL_SECONDS : refreshTtlSeconds;
    }

    public boolean isExposeToLlm() {
        return exposeToLlm == null || Boolean.TRUE.equals(exposeToLlm);
    }

    public boolean isSensitive() {
        return Boolean.TRUE.equals(sensitive);
    }

    public boolean isLlmVisible() {
        return isEnabled() && isExposeToLlm() && !isSensitive();
    }

    public void validate(String ownerPath) {
        if (!isEnabled()) {
            return;
        }

        String effectiveStrategy = getEffectiveStrategy();
        if (!STRATEGY_GROUP_BY.equals(effectiveStrategy) && !STRATEGY_DISTINCT.equals(effectiveStrategy)) {
            throw RX.throwAUserTip(ownerPath + " dictionaryDiscovery.strategy 仅支持 group_by 或 distinct");
        }

        int effectiveMaxValues = getEffectiveMaxValues();
        if (effectiveMaxValues < 1 || effectiveMaxValues > MAX_ALLOWED_VALUES) {
            throw RX.throwAUserTip(ownerPath + " dictionaryDiscovery.maxValues 必须在 1 到 "
                    + MAX_ALLOWED_VALUES + " 之间");
        }

        long effectiveTtl = getEffectiveRefreshTtlSeconds();
        if (effectiveTtl < 0) {
            throw RX.throwAUserTip(ownerPath + " dictionaryDiscovery.refreshTtlSeconds 不能小于 0");
        }

        if (aliases != null) {
            for (Map.Entry<String, AliasDef> entry : aliases.entrySet()) {
                if (StringUtils.isEmpty(entry.getKey())) {
                    throw RX.throwAUserTip(ownerPath + " dictionaryDiscovery.aliases 不允许空别名");
                }
                AliasDef aliasDef = entry.getValue();
                if (aliasDef == null || aliasDef.getValues() == null || aliasDef.getValues().isEmpty()) {
                    throw RX.throwAUserTip(ownerPath + " dictionaryDiscovery.aliases." + entry.getKey()
                            + ".values 不能为空");
                }
            }
        }
    }

    @Data
    public static class AliasDef {
        @ApiModelProperty("该业务别名对应的底层取值集合")
        List<Object> values;

        @ApiModelProperty("业务别名说明")
        String description;
    }
}
