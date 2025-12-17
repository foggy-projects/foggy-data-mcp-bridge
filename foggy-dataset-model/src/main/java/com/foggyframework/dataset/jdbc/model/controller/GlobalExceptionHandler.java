package com.foggyframework.dataset.jdbc.model.controller;

import com.foggyframework.core.ex.ExRuntimeExceptionImpl;
import com.foggyframework.core.ex.RX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一处理控制器抛出的异常，返回标准的 {code, msg} 格式
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
