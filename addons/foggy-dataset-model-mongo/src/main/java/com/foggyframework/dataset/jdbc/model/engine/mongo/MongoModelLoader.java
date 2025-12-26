package com.foggyframework.dataset.jdbc.model.engine.mongo;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.dataset.jdbc.model.def.DbModelDef;
import com.foggyframework.dataset.jdbc.model.spi.TableModel;
import com.foggyframework.fsscript.parser.spi.Fsscript;

/**
 * MongoDB 模型加载器接口
 * <p>
 * 作为 SPI 定义在核心模块中，由 foggy-dataset-model-mongo 模块实现。
 * </p>
 */
public interface MongoModelLoader {
    TableModel load(Fsscript fScript, DbModelDef def, Bundle bundle);
}
