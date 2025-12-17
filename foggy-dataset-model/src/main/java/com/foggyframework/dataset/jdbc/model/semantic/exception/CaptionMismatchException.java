package com.foggyframework.dataset.jdbc.model.semantic.exception;

import com.foggyframework.dataset.jdbc.model.semantic.domain.SemanticNormalizeResponse.MismatchInfo;
import lombok.Getter;

import java.util.List;

/**
 * Caption匹配失败异常
 */
@Getter
public class CaptionMismatchException extends RuntimeException {
    
    private final List<MismatchInfo> mismatchInfos;
    
    public CaptionMismatchException(String message, List<MismatchInfo> mismatchInfos) {
        super(message);
        this.mismatchInfos = mismatchInfos;
    }
    
    public CaptionMismatchException(String message, List<MismatchInfo> mismatchInfos, Throwable cause) {
        super(message, cause);
        this.mismatchInfos = mismatchInfos;
    }
    
    /**
     * 构建详细的错误消息
     */
    public String getDetailMessage() {
        if (mismatchInfos == null || mismatchInfos.isEmpty()) {
            return getMessage();
        }
        
        StringBuilder sb = new StringBuilder(getMessage());
        sb.append("\n匹配失败详情：");
        
        for (MismatchInfo info : mismatchInfos) {
            sb.append("\n - 字段: ").append(info.getFieldName());
            sb.append(", 失败值: ").append(info.getFailedCaptions());
            if (info.getAvailableOptions() != null && !info.getAvailableOptions().isEmpty()) {
                sb.append(", 可用选项数: ").append(info.getAvailableOptions().size());
            }
        }
        
        return sb.toString();
    }
}