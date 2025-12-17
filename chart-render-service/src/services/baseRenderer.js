const browserPool = require('./browserPool');
const fs = require('fs').promises;
const fsSync = require('fs');
const path = require('path');
const { RenderError, TimeoutError } = require('../middleware/errorHandler');
const logger = require('../utils/logger');

// 字体注册状态（只执行一次）
let fontRegistered = false;

/**
 * 在模块加载时注册中文字体
 * node-canvas 的 registerFont 应该在创建 canvas 之前调用，且只需调用一次
 */
function initializeFonts() {
  if (fontRegistered) return;

  const { registerFont } = require('canvas');

  // 中文字体路径列表（按优先级排序）
  // 使用统一的 family 名称 'NotoSansCJK'，避免混淆
  const fontConfigs = [
    { path: '/app/fonts/NotoSansCJK-Regular.ttc', family: 'NotoSansCJK' },
    { path: '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc', family: 'NotoSansCJK' },
  ];

  for (const config of fontConfigs) {
    if (fsSync.existsSync(config.path)) {
      try {
        registerFont(config.path, { family: config.family });
        logger.info('Chinese font registered', { path: config.path, family: config.family });
        fontRegistered = true;
        return;
      } catch (error) {
        logger.warn('Failed to register font', { path: config.path, family: config.family, error: error.message });
      }
    }
  }

  logger.warn('No Chinese fonts found, Chinese characters may not render correctly');
}

// 初始化字体
initializeFonts();

/**
 * 基础渲染器
 * 提供ECharts渲染的核心功能
 */
class BaseRenderer {
  constructor(config) {
    this.config = config;
  }

  /**
   * 渲染图表
   * @param {Object} echartsOption ECharts配置
   * @param {Object} imageSpec 图片规格
   * @returns {Promise<Object>} 渲染结果
   */
  async renderChart(echartsOption, imageSpec) {
    const renderStart = Date.now();

    try {
      // 尝试服务端ECharts渲染
      const buffer = await this.renderServerSide(echartsOption, imageSpec);

      const renderTime = Date.now() - renderStart;
      logger.debug('Server-side chart render completed', {
        renderTime: `${renderTime}ms`,
        format: imageSpec.format,
        size: `${imageSpec.width}x${imageSpec.height}`,
        bufferSize: buffer.length
      });

      return { buffer };

    } catch (error) {
      logger.error('Server-side render failed', {
        error: error.message,
        stack: error.stack
      });

      // 不再回退到浏览器渲染，直接抛出错误
      throw new RenderError(`Server-side ECharts rendering failed: ${error.message}`, error);
    }
  }

  /**
   * 服务端ECharts渲染
   * @param {Object} echartsOption ECharts配置
   * @param {Object} imageSpec 图片规格
   * @returns {Promise<Buffer>} 图片缓冲区
   */
  async renderServerSide(echartsOption, imageSpec) {
    const echarts = require('echarts');
    const { createCanvas, registerFont } = require('canvas');

    logger.debug('Starting server-side ECharts rendering', {
      width: imageSpec.width,
      height: imageSpec.height,
      seriesCount: echartsOption.series?.length || 0
    });

    // 确保字体已注册（在创建 canvas 之前）
    const fontPath = '/app/fonts/NotoSansCJK-Regular.ttc';
    if (fsSync.existsSync(fontPath)) {
      try {
        registerFont(fontPath, { family: 'NotoSansCJK' });
      } catch (e) {
        // 字体可能已经注册，忽略错误
      }
    }

    // 创建Canvas
    const canvas = createCanvas(imageSpec.width, imageSpec.height);

    // 使用新的 API 注册 canvas 创建器
    echarts.setPlatformAPI({
      createCanvas: () => createCanvas(imageSpec.width, imageSpec.height)
    });

    // 初始化ECharts实例
    const chart = echarts.init(canvas);

    // 设置配置
    chart.setOption(echartsOption);

    // 获取PNG buffer
    const buffer = canvas.toBuffer('image/png');

    // 释放图表实例
    chart.dispose();

    logger.debug('Server-side ECharts rendering completed successfully');

    return buffer;
  }

  /**
   * 浏览器渲染（回退方案）
   * @param {Object} echartsOption ECharts配置
   * @param {Object} imageSpec 图片规格
   * @returns {Promise<Object>} 渲染结果
   */
  async renderWithBrowser(echartsOption, imageSpec) {
    const renderStart = Date.now();
    let page = null;

    try {
      // 1. 从浏览器池创建页面
      page = await browserPool.createPage(this.config);
      await this.setupPage(page, imageSpec);

      // 3. 设置HTML内容 - 使用更快的加载策略
      const html = this.generateBrowserHTML(echartsOption, imageSpec);
      logger.debug('Setting page content', { htmlLength: html.length });
      await page.setContent(html, { waitUntil: 'domcontentloaded', timeout: 10000 });
      logger.debug('Page content loaded successfully');

      // 4. 等待图表渲染完成
      await this.waitForChartReady(page);

      // 5. 截图
      const buffer = await this.captureChart(page, imageSpec);

      const renderTime = Date.now() - renderStart;
      logger.debug('Browser chart render completed', {
        renderTime: `${renderTime}ms`,
        format: imageSpec.format,
        size: `${imageSpec.width}x${imageSpec.height}`,
        bufferSize: buffer.length
      });

      return { buffer };

    } catch (error) {
      const renderTime = Date.now() - renderStart;
      logger.logError(error, {
        renderTime: `${renderTime}ms`,
        imageSpec
      });
      throw error;
    } finally {
      // 确保页面关闭 - 使用浏览器池的关闭方法
      await browserPool.closePage(page);
    }
  }

  // getBrowser方法已移至browserPool管理
  // 旧的getBrowser方法已废弃，使用browserPool.createPage()替代

  /**
   * 设置页面配置
   * @param {Page} page Puppeteer页面实例
   * @param {Object} imageSpec 图片规格
   */
  async setupPage(page, imageSpec) {
    // 设置视窗大小
    await page.setViewport({
      width: imageSpec.width,
      height: imageSpec.height,
      deviceScaleFactor: 1
    });

    // 设置超时
    await page.setDefaultNavigationTimeout(this.config.RENDER_TIMEOUT);
    await page.setDefaultTimeout(this.config.RENDER_TIMEOUT);

    // 禁用不必要的资源加载以提升性能
    await page.setRequestInterception(true);
    page.on('request', (request) => {
      const resourceType = request.resourceType();
      const url = request.url();

      // 允许文档和本地脚本资源
      if (['document'].includes(resourceType)) {
        request.continue();
      } else if (resourceType === 'script' &&
        (url.includes('localhost') ||
         url.includes('/assets/echarts'))) {
        // 只允许本地脚本资源（包括本地ECharts）
        request.continue();
      } else {
        // 阻止所有外部请求（包括CDN）
        request.abort();
      }
    });

    // 监听控制台错误
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        logger.warn('Browser console error', { message: msg.text() });
      }
    });

    page.on('pageerror', (error) => {
      logger.warn('Page error', { error: error.message });
    });
  }

  /**
   * 生成浏览器渲染HTML内容
   * @param {Object} echartsOption ECharts配置
   * @param {Object} imageSpec 图片规格
   * @returns {string} HTML内容
   */
  generateBrowserHTML(echartsOption, imageSpec) {
    const optionJson = JSON.stringify(echartsOption, null, 2);

    return `
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Chart Render</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            background-color: ${imageSpec.backgroundColor || '#ffffff'};
            font-family: 'Noto Sans CJK SC', 'Microsoft YaHei', sans-serif;
            width: ${imageSpec.width}px;
            height: ${imageSpec.height}px;
            overflow: hidden;
        }
        #chart {
            width: ${imageSpec.width}px;
            height: ${imageSpec.height}px;
        }
    </style>
    <script src="/assets/echarts.min.js"></script>
</head>
<body>
    <div id="chart"></div>
    <script>
        try {
            // 初始化ECharts实例
            const chart = echarts.init(document.getElementById('chart'));

            // 设置配置选项
            const option = ${optionJson};

            // 使用配置项和数据显示图表
            chart.setOption(option);

            // 监听图表完成事件
            chart.on('finished', function() {
                window.chartReady = true;
            });

            // 确保在一定时间后标记为完成（防止事件不触发）
            setTimeout(() => {
                window.chartReady = true;
            }, 2000);

        } catch (error) {
            console.error('Chart render error:', error);
            // 即使出错也标记为完成，避免无限等待
            window.chartReady = true;
        }
    </script>
</body>
</html>
    `.trim();
  }


  /**
   * 等待图表渲染完成
   * @param {Page} page Puppeteer页面实例
   */
  async waitForChartReady(page) {
    try {
      // 缩短等待时间，因为我们已经使用了mock ECharts
      await page.waitForFunction(
        () => window.chartReady === true || window.chartError,
        { timeout: 3000 }  // 减少到3秒
      );

      // 检查是否有错误
      const chartError = await page.evaluate(() => window.chartError);
      if (chartError) {
        throw new RenderError(`Chart render error: ${chartError}`);
      }

      // 减少额外等待时间
      await page.waitForTimeout(100);

      logger.debug('Chart ready signal received');

    } catch (error) {
      if (error.name === 'TimeoutError') {
        throw new TimeoutError('Chart render timeout');
      }
      throw error;
    }
  }

  /**
   * 截取图表截图
   * @param {Page} page Puppeteer页面实例
   * @param {Object} imageSpec 图片规格
   * @returns {Promise<Buffer>} 图片缓冲区
   */
  async captureChart(page, imageSpec) {
    const screenshotOptions = {
      type: imageSpec.format,
      clip: {
        x: 0,
        y: 0,
        width: imageSpec.width,
        height: imageSpec.height
      },
      omitBackground: imageSpec.backgroundColor === 'transparent'
    };

    // JPEG格式设置质量 (PNG不支持quality参数)
    if ((imageSpec.format === 'jpeg' || imageSpec.format === 'jpg') && imageSpec.quality) {
      screenshotOptions.quality = Math.round(imageSpec.quality * 100);
    }

    const buffer = await page.screenshot(screenshotOptions);

    if (!buffer || buffer.length === 0) {
      throw new RenderError('Screenshot capture failed: empty buffer');
    }

    return buffer;
  }

  /**
   * 关闭浏览器
   */
  async close() {
    if (this.browser) {
      try {
        await this.browser.close();
        logger.debug('Browser closed successfully');
      } catch (error) {
        logger.warn('Failed to close browser', { error: error.message });
      } finally {
        this.browser = null;
      }
    }
  }

  /**
   * 获取浏览器状态
   * @returns {Object} 浏览器状态信息
   */
  async getBrowserStatus() {
    // 获取浏览器池状态
    return await browserPool.getStatus();
  }
}

module.exports = BaseRenderer;