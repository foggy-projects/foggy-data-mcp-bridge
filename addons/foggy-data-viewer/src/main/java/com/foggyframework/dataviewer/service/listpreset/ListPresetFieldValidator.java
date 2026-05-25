package com.foggyframework.dataviewer.service.listpreset;

import com.foggyframework.dataviewer.service.ListPresetService.SaveListPresetRequest;

/**
 * 自定义列表字段校验扩展点。
 * <p>
 * 默认实现不做强校验；接入方可注册同类型 Bean，按当前用户、模型和业务页面校验
 * columns、columnSettings、slice、orderBy 中的字段是否存在且有权限。
 */
@FunctionalInterface
public interface ListPresetFieldValidator {

    void validate(String userId, String model, String businessKey, SaveListPresetRequest request);

    static ListPresetFieldValidator noop() {
        return (userId, model, businessKey, request) -> {
        };
    }
}
