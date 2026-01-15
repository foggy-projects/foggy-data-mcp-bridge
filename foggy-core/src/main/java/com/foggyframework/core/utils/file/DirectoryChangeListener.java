/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved.
 * You must accept the terms of that agreement to use this software.
 ******************************************************************************/
package com.foggyframework.core.utils.file;

import java.io.File;

/**
 * 目录变化监听器
 *
 * <p>用于监听目录下的文件创建、修改、删除事件。
 * 与 {@link FileChangeListener} 不同，此接口专注于目录级别的监听，
 * 特别是监听新文件的创建。
 *
 * @author Foggy
 * @since 2.0.0
 * @see WatchServiceFileTracer
 */
public interface DirectoryChangeListener {

    /**
     * 目录下有新文件创建
     *
     * @param file 新创建的文件
     */
    void onFileCreated(File file);

    /**
     * 目录下有文件被修改
     *
     * @param file 被修改的文件
     */
    default void onFileModified(File file) {
        // 默认空实现，子类可覆盖
    }

    /**
     * 目录下有文件被删除
     *
     * @param file 被删除的文件
     */
    default void onFileDeleted(File file) {
        // 默认空实现，子类可覆盖
    }
}
