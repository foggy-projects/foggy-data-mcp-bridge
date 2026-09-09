package com.foggyframework.dataviewer.service.listpreset;

import com.foggyframework.dataviewer.service.ListPresetService.SaveListPresetRequest;

/**
 * 自定义列表字段校验扩展点。
 * <p>
 * 默认实现不做字段权限强校验；接入方可注册同类型 Bean，按当前用户、模型和业务页面校验
 * columns、columnSettings、slice、orderBy 中的字段是否存在且有权限。服务层会在更新时先合并
 * patch 与已存方案，再把完整方案交给此扩展点。路径中的 userId 只是调用方提供的作用域键，
 * 不应被默认实现视为已认证身份；宿主应用需要在控制器或此扩展点前置完成身份与租户授权。
 */
@FunctionalInterface
public interface ListPresetFieldValidator {

    void validate(String userId, String model, String businessKey, SaveListPresetRequest request);

    static ListPresetFieldValidator noop() {
        return (userId, model, businessKey, request) -> {
        };
    }
}
