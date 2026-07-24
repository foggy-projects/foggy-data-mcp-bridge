package com.foggyframework.dataset.model.controller;

import com.foggyframework.core.ex.ExRuntimeExceptionImpl;
import com.foggyframework.core.ex.RX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Foggy Dataset Model 模块的异常处理器
 * <p>
 * 统一处理本模块控制器抛出的异常，返回标准的 {code, msg} 格式。
 * <p>
 * 设计说明：
 * <ul>
 *   <li>使用 basePackages 限制作用范围，仅处理本模块的控制器异常</li>
 *   <li>可通过配置 foggy.dataset.exception-handler.enabled=false 禁用</li>
 *   <li>宿主应用可定义自己的异常处理器来覆盖本模块的处理逻辑</li>
 * </ul>
 */
@RestControllerAdvice(basePackages = "com.foggyframework.dataset.model.controller")
@ConditionalOnProperty(
    prefix = "foggy.dataset.exception-handler",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class FoggyDatasetExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(FoggyDatasetExceptionHandler.class);

    /**
     * 处理业务异常 (ExRuntimeExceptionImpl)
     */
    @ExceptionHandler(ExRuntimeExceptionImpl.class)
    public RX<?> handleExRuntimeException(ExRuntimeExceptionImpl ex) {
        logger.warn("业务异常: {}", ex.getMessage());
        return RX.failB(ex.getCode(), ex.getMessage());
    }

    /**
     * 处理其他未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public RX<?> handleException(Exception ex) {
        logger.error("系统异常: ", ex);
        return RX.failB(500, "服务器内部错误: " + ex.getMessage());
    }
}
