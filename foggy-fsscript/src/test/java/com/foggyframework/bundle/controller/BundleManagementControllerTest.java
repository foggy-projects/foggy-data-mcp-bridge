package com.foggyframework.bundle.controller;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.core.ex.RX;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BundleManagementController单元测试
 * <p>
 * 测试REST API的正确性
 * </p>
 */
public class BundleManagementControllerTest {

    @TempDir
    Path tempDir;

    @Mock
    private SystemBundlesContext systemBundlesContext;

    @InjectMocks
    private BundleManagementController controller;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testListBundles() {
        // Mock数据
        List<BundleDefinition> mockBundles = new ArrayList<>();

        // Create ExternalBundleDefinition instead of mocking
        ExternalBundleDefinition mockDef1 = new ExternalBundleDefinition("bundle1", "dev", "/path1", false);
        ExternalBundleDefinition mockDef2 = new ExternalBundleDefinition("bundle2", "test", "/path2", false);

        mockBundles.add(mockDef1);
        mockBundles.add(mockDef2);

        when(systemBundlesContext.listExternalBundles()).thenReturn(mockBundles);

        // 调用接口
        RX<List<BundleManagementController.BundleInfo>> result = controller.listBundles();

        // 验证
        assertTrue(result._isSuccess());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().size());

        verify(systemBundlesContext, times(1)).listExternalBundles();
    }

    @Test
    public void testListBundlesEmpty() {
        // Mock空列表
        when(systemBundlesContext.listExternalBundles()).thenReturn(new ArrayList<>());

        // 调用接口
        RX<List<BundleManagementController.BundleInfo>> result = controller.listBundles();

        // 验证
        assertTrue(result._isSuccess());
        assertNotNull(result.getData());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    public void testAddBundleSuccess() {
        // Mock成功添加
        when(systemBundlesContext.addExternalBundle(
                anyString(), anyString(), anyString(), anyBoolean()
        )).thenReturn(true);

        // 创建请求
        BundleManagementController.AddBundleRequest request =
                new BundleManagementController.AddBundleRequest(
                        "test-bundle",
                        "dev",
                        "/data/test",
                        true
                );

        // 调用接口
        RX<String> result = controller.addBundle(request);

        // 验证
        assertTrue(result._isSuccess());
        assertEquals("Bundle添加成功", result.getData());

        verify(systemBundlesContext, times(1)).addExternalBundle(
                "test-bundle", "dev", "/data/test", true
        );
    }

    @Test
    public void testAddBundleFailed() {
        // Mock添加失败
        when(systemBundlesContext.addExternalBundle(
                anyString(), anyString(), anyString(), anyBoolean()
        )).thenReturn(false);

        // 创建请求
        BundleManagementController.AddBundleRequest request =
                new BundleManagementController.AddBundleRequest(
                        "test-bundle",
                        "dev",
                        "/invalid/path",
                        false
                );

        // 调用接口
        RX<String> result = controller.addBundle(request);

        // 验证
        assertFalse(result._isSuccess());
        assertEquals("Bundle添加失败，请检查日志", result.getMsg());
    }

    @Test
    public void testAddBundleWithDefaultNamespace() {
        // Mock成功添加
        when(systemBundlesContext.addExternalBundle(
                anyString(), anyString(), anyString(), anyBoolean()
        )).thenReturn(true);

        // 创建请求（使用默认namespace）
        BundleManagementController.AddBundleRequest request =
                new BundleManagementController.AddBundleRequest(
                        "default-bundle",
                        "",  // 默认命名空间
                        "/data/default",
                        false
                );

        // 调用接口
        RX<String> result = controller.addBundle(request);

        // 验证
        assertTrue(result._isSuccess());

        verify(systemBundlesContext, times(1)).addExternalBundle(
                "default-bundle", "", "/data/default", false
        );
    }

    @Test
    public void testRemoveBundleSuccess() {
        // Mock成功移除
        when(systemBundlesContext.removeBundle("test-bundle")).thenReturn(true);

        // 调用接口
        RX<String> result = controller.removeBundle("test-bundle");

        // 验证
        assertTrue(result._isSuccess());
        assertEquals("Bundle移除成功", result.getData());

        verify(systemBundlesContext, times(1)).removeBundle("test-bundle");
    }

    @Test
    public void testRemoveBundleFailed() {
        // Mock移除失败
        when(systemBundlesContext.removeBundle("nonexistent-bundle")).thenReturn(false);

        // 调用接口
        RX<String> result = controller.removeBundle("nonexistent-bundle");

        // 验证
        assertFalse(result._isSuccess());
        assertEquals("Bundle移除失败，请检查日志", result.getMsg());
    }

    @Test
    public void testExistsBundleTrue() {
        // Mock bundle存在
        when(systemBundlesContext.containBundle("existing-bundle")).thenReturn(true);

        // 调用接口
        RX<Boolean> result = controller.existsBundle("existing-bundle");

        // 验证
        assertTrue(result._isSuccess());
        assertTrue(result.getData());

        verify(systemBundlesContext, times(1)).containBundle("existing-bundle");
    }

    @Test
    public void testExistsBundleFalse() {
        // Mock bundle不存在
        when(systemBundlesContext.containBundle("nonexistent-bundle")).thenReturn(false);

        // 调用接口
        RX<Boolean> result = controller.existsBundle("nonexistent-bundle");

        // 验证
        assertTrue(result._isSuccess());
        assertFalse(result.getData());

        verify(systemBundlesContext, times(1)).containBundle("nonexistent-bundle");
    }

    @Test
    public void testBundleInfoDto() {
        // 测试DTO对象
        BundleManagementController.BundleInfo info =
                new BundleManagementController.BundleInfo(
                        "test-bundle",
                        "dev",
                        "/data/test",
                        true
                );

        assertEquals("test-bundle", info.getName());
        assertEquals("dev", info.getNamespace());
        assertEquals("/data/test", info.getPath());
        assertTrue(info.isWatch());
    }

    @Test
    public void testAddBundleRequestDto() {
        // 测试DTO对象
        BundleManagementController.AddBundleRequest request =
                new BundleManagementController.AddBundleRequest(
                        "test-bundle",
                        "dev",
                        "/data/test",
                        true
                );

        assertEquals("test-bundle", request.getName());
        assertEquals("dev", request.getNamespace());
        assertEquals("/data/test", request.getPath());
        assertTrue(request.isWatch());
    }

    @Test
    public void testAddBundleRequestDefaultValues() {
        // 测试DTO默认值
        BundleManagementController.AddBundleRequest request =
                new BundleManagementController.AddBundleRequest();

        assertEquals("", request.getNamespace()); // 默认空字符串
        assertFalse(request.isWatch()); // 默认false
    }
}
