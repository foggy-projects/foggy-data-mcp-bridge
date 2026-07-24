package com.foggyframework.dataset.model.controller;

import com.foggyframework.core.ex.ExRuntimeExceptionImpl;
import com.foggyframework.core.ex.RX;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FoggyDatasetExceptionHandler 单元测试
 *
 * <p>纯单元测试，不依赖 Spring Context。
 * 验证异常处理器对不同类型异常的返回格式。</p>
 */
@DisplayName("异常处理器单元测试")
class FoggyDatasetExceptionHandlerTest {

    private FoggyDatasetExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FoggyDatasetExceptionHandler();
    }

    // ==========================================
    // 业务异常 (ExRuntimeExceptionImpl) 处理
    // ==========================================

    @Test
    @DisplayName("业务异常 - 返回 RX.fail 格式")
    void testHandleBusinessException() {
        ExRuntimeExceptionImpl ex = new ExRuntimeExceptionImpl("模型未找到");

        RX<?> result = handler.handleExRuntimeException(ex);

        assertNotNull(result, "Result should not be null");
        assertNotEquals(200, result.getCode(), "Business exception code should not be 200");
        assertNotNull(result.getMsg(), "Message should not be null");
        assertTrue(result.getMsg().contains("模型未找到"),
                "Message should contain original error text");
    }

    @Test
    @DisplayName("业务异常 - 带自定义 code")
    void testHandleBusinessExceptionWithCode() {
        ExRuntimeExceptionImpl ex = new ExRuntimeExceptionImpl(404, "查询模型不存在");

        RX<?> result = handler.handleExRuntimeException(ex);

        assertNotNull(result);
        assertEquals(404, result.getCode(), "Should preserve custom error code");
        assertTrue(result.getMsg().contains("查询模型不存在"));
    }

    // ==========================================
    // 系统异常 (Exception) 处理
    // ==========================================

    @Test
    @DisplayName("系统异常 - 返回 500 错误码")
    void testHandleSystemException() {
        Exception ex = new RuntimeException("数据库连接超时");

        RX<?> result = handler.handleException(ex);

        assertNotNull(result);
        assertEquals(500, result.getCode(), "System exception should return 500");
        assertTrue(result.getMsg().contains("服务器内部错误"),
                "Message should contain '服务器内部错误'");
        assertTrue(result.getMsg().contains("数据库连接超时"),
                "Message should contain original exception message");
    }

    @Test
    @DisplayName("系统异常 - NullPointerException")
    void testHandleNPE() {
        NullPointerException ex = new NullPointerException("model is null");

        RX<?> result = handler.handleException(ex);

        assertNotNull(result);
        assertEquals(500, result.getCode());
    }

    @Test
    @DisplayName("系统异常 - 无消息的异常")
    void testHandleExceptionWithoutMessage() {
        Exception ex = new RuntimeException();

        RX<?> result = handler.handleException(ex);

        assertNotNull(result);
        assertEquals(500, result.getCode());
        // 即使异常无消息，也应返回有效响应
        assertNotNull(result.getMsg());
    }

    // ==========================================
    // RX 格式一致性验证
    // ==========================================

    @Test
    @DisplayName("所有返回均为 RX 类型")
    void testReturnTypeConsistency() {
        RX<?> bizResult = handler.handleExRuntimeException(new ExRuntimeExceptionImpl("biz error"));
        RX<?> sysResult = handler.handleException(new RuntimeException("sys error"));

        assertInstanceOf(RX.class, bizResult);
        assertInstanceOf(RX.class, sysResult);
    }
}
