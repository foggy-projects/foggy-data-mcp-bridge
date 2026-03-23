package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.dataset.db.model.spi.ModelLoadError;
import com.foggyframework.dataset.db.model.spi.ModelLoadStatus;
import com.foggyframework.dataset.db.model.spi.TableModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 错误收集机制测试
 *
 * <p>验证模型加载时的错误收集功能是否正常工作</p>
 */
@Slf4j
@DisplayName("错误收集机制测试")
class ErrorCollectionTest extends EcommerceTestSupport {

    @Test
    @DisplayName("验证模型加载成功且无错误")
    void testModelLoadSuccessWithoutErrors() {
        TableModel model = tableModelLoaderManager.load("DimDateModel");
        assertNotNull(model, "DimDateModel should load successfully");

        // 验证错误收集状态
        assertEquals(ModelLoadStatus.SUCCESS, model.getLoadStatus(),
                "Load status should be SUCCESS");
        assertFalse(model.hasErrors(),
                "Model should not have errors");
        assertTrue(model.getLoadErrors().isEmpty(),
                "Error list should be empty");

        log.info("DimDateModel loaded successfully without errors");
    }

    @Test
    @DisplayName("验证 FactSalesModel 加载情况")
    void testFactSalesModelLoading() {
        TableModel model = tableModelLoaderManager.load("FactSalesModel");
        assertNotNull(model, "FactSalesModel should load");

        log.info("FactSalesModel loaded with status: {}", model.getLoadStatus());
        log.info("Error count: {}", model.getLoadErrors().size());
        log.info("Has errors: {}", model.hasErrors());
        log.info("Has fatal errors: {}", model.hasFatalErrors());

        // 打印所有错误信息
        if (model.hasErrors()) {
            log.warn("FactSalesModel has {} errors:", model.getLoadErrors().size());
            for (ModelLoadError error : model.getLoadErrors()) {
                log.warn("  - {}", error.getFormattedMessage());
            }
        }

        // 模型应该能成功加载，即使有错误
        assertNotNull(model.getName(), "Model name should not be null");
        assertNotNull(model.getTableName(), "Table name should not be null");

        // 验证维度和度量仍然被创建
        assertNotNull(model.getDimensions(), "Dimensions should not be null");
        assertNotNull(model.getMeasures(), "Measures should not be null");

        log.info("FactSalesModel loaded: dimensions={}, measures={}, properties={}",
                model.getDimensions().size(),
                model.getMeasures().size(),
                model.getProperties().size());
    }

    @Test
    @DisplayName("验证 DimCustomerModel 加载情况")
    void testDimCustomerModelLoading() {
        TableModel model = tableModelLoaderManager.load("DimCustomerModel");
        assertNotNull(model, "DimCustomerModel should load");

        log.info("DimCustomerModel loaded with status: {}", model.getLoadStatus());
        log.info("Error count: {}", model.getLoadErrors().size());

        // 打印所有错误信息
        if (model.hasErrors()) {
            log.warn("DimCustomerModel has {} errors:", model.getLoadErrors().size());
            for (ModelLoadError error : model.getLoadErrors()) {
                log.warn("  - {}", error.getFormattedMessage());
            }
        } else {
            log.info("DimCustomerModel loaded successfully without errors");
        }

        // 验证模型基本信息
        assertEquals("DimCustomerModel", model.getName());
        assertEquals("dim_customer", model.getTableName());

        log.info("DimCustomerModel properties count: {}", model.getProperties().size());
    }

    // ==========================================
    // ModelLoadStatus 枚举测试
    // ==========================================

    @Test
    @DisplayName("ModelLoadStatus.SUCCESS - 所有方法返回正确值")
    void testModelLoadStatusSuccess() {
        assertTrue(ModelLoadStatus.SUCCESS.isSuccess());
        assertTrue(ModelLoadStatus.SUCCESS.isFullySuccess());
        assertFalse(ModelLoadStatus.SUCCESS.isFailed());
    }

    @Test
    @DisplayName("ModelLoadStatus.SUCCESS_WITH_WARNINGS - isSuccess=true, isFullySuccess=false")
    void testModelLoadStatusSuccessWithWarnings() {
        assertTrue(ModelLoadStatus.SUCCESS_WITH_WARNINGS.isSuccess());
        assertFalse(ModelLoadStatus.SUCCESS_WITH_WARNINGS.isFullySuccess());
        assertFalse(ModelLoadStatus.SUCCESS_WITH_WARNINGS.isFailed());
    }

    @Test
    @DisplayName("ModelLoadStatus.PARTIAL_SUCCESS - isSuccess=true, isFullySuccess=false")
    void testModelLoadStatusPartialSuccess() {
        assertTrue(ModelLoadStatus.PARTIAL_SUCCESS.isSuccess());
        assertFalse(ModelLoadStatus.PARTIAL_SUCCESS.isFullySuccess());
        assertFalse(ModelLoadStatus.PARTIAL_SUCCESS.isFailed());
    }

    @Test
    @DisplayName("ModelLoadStatus.FAILED - isFailed=true, isSuccess=false")
    void testModelLoadStatusFailed() {
        assertFalse(ModelLoadStatus.FAILED.isSuccess());
        assertFalse(ModelLoadStatus.FAILED.isFullySuccess());
        assertTrue(ModelLoadStatus.FAILED.isFailed());
    }

    // ==========================================
    // ModelLoadError 数据完整性测试
    // ==========================================

    @Test
    @DisplayName("ModelLoadError - ErrorType 枚举覆盖")
    void testModelLoadErrorTypes() {
        // 验证所有 ErrorType 枚举值存在
        ModelLoadError.ErrorType[] types = ModelLoadError.ErrorType.values();
        assertTrue(types.length >= 7, "Should have at least 7 error types");

        // 验证关键类型存在
        assertNotNull(ModelLoadError.ErrorType.valueOf("COLUMN_NOT_FOUND"));
        assertNotNull(ModelLoadError.ErrorType.valueOf("TABLE_NOT_FOUND"));
        assertNotNull(ModelLoadError.ErrorType.valueOf("DIMENSION_NOT_FOUND"));
        assertNotNull(ModelLoadError.ErrorType.valueOf("TYPE_MISMATCH"));
        assertNotNull(ModelLoadError.ErrorType.valueOf("FORMULA_ERROR"));
        assertNotNull(ModelLoadError.ErrorType.valueOf("PREAGG_CONFIG_ERROR"));
        assertNotNull(ModelLoadError.ErrorType.valueOf("OTHER"));
    }

    @Test
    @DisplayName("ModelLoadError - ErrorLevel 枚举覆盖")
    void testModelLoadErrorLevels() {
        ModelLoadError.ErrorLevel[] levels = ModelLoadError.ErrorLevel.values();
        assertTrue(levels.length >= 3, "Should have at least 3 error levels");

        assertNotNull(ModelLoadError.ErrorLevel.valueOf("WARNING"));
        assertNotNull(ModelLoadError.ErrorLevel.valueOf("ERROR"));
        assertNotNull(ModelLoadError.ErrorLevel.valueOf("FATAL"));
    }

    @Test
    @DisplayName("ModelLoadError - 格式化消息包含关键信息")
    void testModelLoadErrorFormattedMessage() {
        // 验证实际加载的模型错误（如果有的话）的格式化消息
        TableModel model = tableModelLoaderManager.load("FactSalesModel");
        if (model.hasErrors()) {
            for (ModelLoadError error : model.getLoadErrors()) {
                String msg = error.getFormattedMessage();
                assertNotNull(msg, "Formatted message should not be null");
                assertFalse(msg.isEmpty(), "Formatted message should not be empty");
                log.info("Error formatted message: {}", msg);
            }
        }
    }

    // ==========================================
    // 多模型加载错误收集
    // ==========================================

    @Test
    @DisplayName("多模型连续加载不互相干扰")
    void testMultipleModelLoadErrors() {
        TableModel dateModel = tableModelLoaderManager.load("DimDateModel");
        TableModel salesModel = tableModelLoaderManager.load("FactSalesModel");
        TableModel customerModel = tableModelLoaderManager.load("DimCustomerModel");

        // 每个模型的错误列表独立
        assertNotNull(dateModel.getLoadErrors());
        assertNotNull(salesModel.getLoadErrors());
        assertNotNull(customerModel.getLoadErrors());

        // DimDateModel 应该是成功的
        assertEquals(ModelLoadStatus.SUCCESS, dateModel.getLoadStatus());

        log.info("Date errors: {}, Sales errors: {}, Customer errors: {}",
                dateModel.getLoadErrors().size(),
                salesModel.getLoadErrors().size(),
                customerModel.getLoadErrors().size());
    }

    @Test
    @DisplayName("成功模型的 hasErrors 和 hasFatalErrors 为 false")
    void testSuccessModelNoErrors() {
        TableModel model = tableModelLoaderManager.load("DimDateModel");

        assertFalse(model.hasErrors(), "Successful model should not have errors");
        assertFalse(model.hasFatalErrors(), "Successful model should not have fatal errors");
        assertTrue(model.getLoadErrors().isEmpty(), "Error list should be empty");
    }
}
