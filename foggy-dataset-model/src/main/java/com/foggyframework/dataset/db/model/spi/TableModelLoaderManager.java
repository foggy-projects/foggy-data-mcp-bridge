package com.foggyframework.dataset.db.model.spi;

public interface TableModelLoaderManager {
    void clearAll();

    TableModel load(String s);


    /**
     * 呃，加这个是因为ai经常直接使用getJdbcModel来获取模型，而不是找load
     *
     * @param s
     * @return
     */
    default TableModel getJdbcModel(String s) {
        return load(s);
    }
}
