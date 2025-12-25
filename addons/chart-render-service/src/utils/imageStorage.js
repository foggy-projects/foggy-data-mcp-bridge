const fs = require('fs').promises;
const path = require('path');
const logger = require('./logger');

/**
 * 图片本地存储工具类
 */
class ImageStorage {
  constructor(config) {
    this.config = config;
    this.ensureDirectoryExists();
  }

  /**
   * 确保图片目录存在
   */
  async ensureDirectoryExists() {
    try {
      await fs.access(this.config.LOCAL_IMAGES_DIR);
    } catch (error) {
      // 目录不存在，创建它
      await fs.mkdir(this.config.LOCAL_IMAGES_DIR, { recursive: true });
      logger.info('Created images directory', { path: this.config.LOCAL_IMAGES_DIR });
    }
  }

  /**
   * 生成唯一的文件名
   * @param {string} format 图片格式 (png, svg)
   * @param {string} type 图表类型 (可选)
   * @returns {string} 文件名
   */
  generateFilename(format, type = '') {
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const randomSuffix = Math.random().toString(36).substring(2, 8);
    const typePrefix = type ? `${type}_` : '';

    return `${this.config.IMAGE_FILENAME_PREFIX}${typePrefix}${timestamp}_${randomSuffix}.${format}`;
  }

  /**
   * 保存图片到本地
   * @param {Buffer} buffer 图片buffer
   * @param {string} format 图片格式
   * @param {string} type 图表类型 (可选)
   * @param {Object} metadata 元数据 (可选)
   * @returns {Promise<Object>} 保存结果
   */
  async saveImage(buffer, format, type = '', metadata = {}) {
    if (!this.config.SAVE_IMAGES_LOCALLY) {
      return { saved: false, reason: 'Local image saving is disabled' };
    }

    try {
      await this.ensureDirectoryExists();

      const filename = this.generateFilename(format, type);
      const filepath = path.join(this.config.LOCAL_IMAGES_DIR, filename);

      await fs.writeFile(filepath, buffer);

      // 保存元数据文件 (JSON格式)
      if (Object.keys(metadata).length > 0) {
        const metadataFilename = filename.replace(`.${format}`, '_metadata.json');
        const metadataPath = path.join(this.config.LOCAL_IMAGES_DIR, metadataFilename);

        const metadataWithTimestamp = {
          ...metadata,
          savedAt: new Date().toISOString(),
          filename,
          format,
          type,
          fileSize: buffer.length
        };

        await fs.writeFile(metadataPath, JSON.stringify(metadataWithTimestamp, null, 2));
      }

      const result = {
        saved: true,
        filename,
        filepath,
        absolutePath: path.resolve(filepath),
        fileSize: buffer.length,
        savedAt: new Date().toISOString()
      };

      logger.info('Image saved locally', result);

      return result;
    } catch (error) {
      logger.error('Failed to save image locally', { error: error.message, type, format });
      return { saved: false, reason: error.message };
    }
  }

  /**
   * 清理旧图片文件
   * @param {number} maxAgeHours 最大保留时间(小时)
   * @returns {Promise<Object>} 清理结果
   */
  async cleanupOldImages(maxAgeHours = 24) {
    try {
      const files = await fs.readdir(this.config.LOCAL_IMAGES_DIR);
      const now = Date.now();
      const maxAge = maxAgeHours * 60 * 60 * 1000; // 转换为毫秒

      let deletedCount = 0;
      let totalSize = 0;

      for (const file of files) {
        const filepath = path.join(this.config.LOCAL_IMAGES_DIR, file);
        const stats = await fs.stat(filepath);

        if (now - stats.mtime.getTime() > maxAge) {
          await fs.unlink(filepath);
          deletedCount++;
          totalSize += stats.size;
        }
      }

      const result = {
        success: true,
        deletedFiles: deletedCount,
        freedSpace: totalSize,
        maxAgeHours
      };

      if (deletedCount > 0) {
        logger.info('Cleaned up old images', result);
      }

      return result;
    } catch (error) {
      logger.error('Failed to cleanup old images', { error: error.message });
      return { success: false, error: error.message };
    }
  }

  /**
   * 获取存储统计信息
   * @returns {Promise<Object>} 统计信息
   */
  async getStorageStats() {
    try {
      const files = await fs.readdir(this.config.LOCAL_IMAGES_DIR);

      let totalFiles = 0;
      let totalSize = 0;
      const formatCounts = {};

      for (const file of files) {
        if (file.endsWith('_metadata.json')) continue; // 跳过元数据文件

        const filepath = path.join(this.config.LOCAL_IMAGES_DIR, file);
        const stats = await fs.stat(filepath);

        totalFiles++;
        totalSize += stats.size;

        const ext = path.extname(file).substring(1);
        formatCounts[ext] = (formatCounts[ext] || 0) + 1;
      }

      return {
        totalFiles,
        totalSize,
        formatCounts,
        directory: this.config.LOCAL_IMAGES_DIR,
        enabled: this.config.SAVE_IMAGES_LOCALLY
      };
    } catch (error) {
      logger.error('Failed to get storage stats', { error: error.message });
      return { error: error.message };
    }
  }
}

module.exports = ImageStorage;