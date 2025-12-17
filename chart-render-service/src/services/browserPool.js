// Puppeteer已移除 - 使用服务端Canvas渲染
// const puppeteer = require('puppeteer');
const logger = require('../utils/logger');

/**
 * 浏览器池管理器 - 已移除Puppeteer依赖
 * 仅保留接口兼容性
 */
class BrowserPool {
  constructor() {
    // Puppeteer已移除，仅保留兼容性
  }

  /**
   * 创建页面 - 已移除Puppeteer
   */
  async createPage(config) {
    throw new Error('Puppeteer has been removed. Only server-side Canvas rendering is supported.');
  }

  /**
   * 关闭页面 - 已移除Puppeteer
   */
  async closePage(page) {
    // no-op
  }

  /**
   * 获取状态 - 返回移除状态
   */
  async getStatus() {
    return {
      browserConnected: false,
      puppeteerRemoved: true,
      renderMode: 'server-side-only'
    };
  }

  /**
   * 获取浏览器实例（已废弃）
   */
  async getBrowser(config) {
    throw new Error('getBrowser method is deprecated. Puppeteer has been removed.');
  }

}

// 导出单例
module.exports = new BrowserPool();