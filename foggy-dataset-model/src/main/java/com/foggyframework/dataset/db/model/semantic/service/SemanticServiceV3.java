package com.foggyframework.dataset.db.model.semantic.service;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;

/**
 * V3版本语义服务接口
 *
 * <p>与V2的核心区别：维度字段展开为独立的 $id 和 $caption 字段</p>
 *
 * <p>例如：维度 salesDate 会展开为两个字段：</p>
 * <ul>
 *   <li>salesDate$id - 销售日期ID，格式：yyyymmdd</li>
 *   <li>salesDate$caption - 销售日期显示名称，格式：yyyy年mm月dd日</li>
 * </ul>
 *
 * <p>这样AI无需判断是否需要添加后缀，直接使用字段名即可。</p>
 */
public interface SemanticServiceV3 {

    /**
     * 获取字段语义元数据（V3版本：维度展开）
     *
     * @param request 元数据请求
     * @param format  输出格式：json(为上游MCP服务)|markdown(为大语言模型)
     * @return 元数据响应（维度字段已展开为独立的 $id/$caption 字段）
     */
    SemanticMetadataResponse getMetadata(SemanticMetadataRequest request, String format);
}
