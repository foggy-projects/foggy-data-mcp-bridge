package com.foggyframework.dataset.vector;

import com.foggyframework.dataset.vector.funs.VectorFileFsscriptLoader;
import com.foggyframework.dataset.vector.support.VectorFscriptDataSetModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VectorKey 单元测试
 */
@ExtendWith(MockitoExtension.class)
class VectorKeyTest {

    @Mock
    private VectorStore vectorStore;

    @Test
    void testVectorKeyCreation() {
        VectorKey key = new VectorKey(vectorStore, "test query", 10, 0.7, 0, 20);

        assertEquals("test query", key.getQuery());
        assertEquals(10, key.getTopK());
        assertEquals(0.7, key.getThreshold());
        assertEquals(0, key.getStart());
        assertEquals(20, key.getLimit());
        assertNotNull(key.getVectorStore());
    }

    @Test
    void testVectorKeyWithPaging() {
        VectorKey key = new VectorKey(vectorStore, "销售数据查询", 5, 0.8, 10, 10);

        assertEquals("销售数据查询", key.getQuery());
        assertEquals(5, key.getTopK());
        assertEquals(0.8, key.getThreshold());
        assertEquals(10, key.getStart());
        assertEquals(10, key.getLimit());
    }

    @Test
    void testVectorKeyDefaultValues() {
        VectorKey key = new VectorKey(vectorStore, "", 0, 0.0, 0, 0);

        assertEquals("", key.getQuery());
        assertEquals(0, key.getTopK());
        assertEquals(0.0, key.getThreshold());
    }
}
