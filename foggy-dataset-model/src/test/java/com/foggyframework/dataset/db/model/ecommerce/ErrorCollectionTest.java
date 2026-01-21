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
}
