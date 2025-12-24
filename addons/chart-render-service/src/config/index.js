const path = require('path');

// 加载环境变量
require('dotenv').config();

const config = {
  // 基础配置
  NODE_ENV: process.env.NODE_ENV || 'development',
  PORT: parseInt(process.env.PORT) || 3000,
  LOG_LEVEL: process.env.LOG_LEVEL || 'info',

  // 认证配置
  AUTH_TOKEN: process.env.RENDER_AUTH_TOKEN || '',

  // 渲染限制配置
  MAX_WIDTH: parseInt(process.env.MAX_WIDTH) || 4000,
  MAX_HEIGHT: parseInt(process.env.MAX_HEIGHT) || 4000,
  DEFAULT_WIDTH: parseInt(process.env.DEFAULT_WIDTH) || 800,
  DEFAULT_HEIGHT: parseInt(process.env.DEFAULT_HEIGHT) || 600,
  RENDER_TIMEOUT: parseInt(process.env.RENDER_TIMEOUT) || 15000,
  MAX_CONCURRENT_RENDERS: parseInt(process.env.MAX_CONCURRENT_RENDERS) || 10,

  // Puppeteer配置
  PUPPETEER_ARGS: (process.env.PUPPETEER_ARGS || '--no-sandbox,--disable-setuid-sandbox,--disable-dev-shm-usage').split(','),
  PUPPETEER_EXECUTABLE_PATH: process.env.PUPPETEER_EXECUTABLE_PATH,

  // 安全配置
  MAX_MEMORY_MB: parseInt(process.env.MAX_MEMORY_MB) || 512,
  RATE_LIMIT_WINDOW_MS: parseInt(process.env.RATE_LIMIT_WINDOW_MS) || 60000,
  RATE_LIMIT_MAX_REQUESTS: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS) || 100,

  // 开发配置
  ENABLE_DEBUG_ENDPOINT: process.env.ENABLE_DEBUG_ENDPOINT === 'true',
  SAVE_TEMP_FILES: process.env.SAVE_TEMP_FILES === 'true',

  // 图片本地保存配置
  SAVE_IMAGES_LOCALLY: process.env.SAVE_IMAGES_LOCALLY === 'true',
  LOCAL_IMAGES_DIR: process.env.LOCAL_IMAGES_DIR || path.join(__dirname, '../../images'),
  IMAGE_FILENAME_PREFIX: process.env.IMAGE_FILENAME_PREFIX || 'chart_',

  // 支持的图片格式
  SUPPORTED_FORMATS: ['png', 'svg'],

  // 支持的渲染模式
  SUPPORTED_MODES: ['unified', 'native'],

  // ECharts默认配置
  ECHARTS_DEFAULTS: {
    backgroundColor: '#ffffff',
    animation: false, // 关闭动画以提升渲染性能
    grid: {
      containLabel: true,
      left: '10%',
      right: '10%',
      top: '10%',
      bottom: '10%'
    },
    textStyle: {
      fontFamily: 'NotoSansCJK'
    }
  },

  // 统一图表语义的默认TopN限制
  DEFAULT_TOP_N: 100,
  MAX_TOP_N: 1000,

  // 临时文件路径
  TEMP_DIR: path.join(__dirname, '../../temp'),

  // 是否开发环境
  get isDevelopment() {
    return this.NODE_ENV === 'development';
  },

  // 是否生产环境
  get isProduction() {
    return this.NODE_ENV === 'production';
  }
};

// 配置验证
const validateConfig = () => {
  const errors = [];

  if (config.MAX_WIDTH > 8000 || config.MAX_HEIGHT > 8000) {
    errors.push('MAX_WIDTH and MAX_HEIGHT should not exceed 8000');
  }

  if (config.RENDER_TIMEOUT > 60000) {
    errors.push('RENDER_TIMEOUT should not exceed 60000ms');
  }

  if (config.MAX_CONCURRENT_RENDERS > 50) {
    errors.push('MAX_CONCURRENT_RENDERS should not exceed 50');
  }

  if (errors.length > 0) {
    throw new Error(`Configuration validation failed: ${errors.join(', ')}`);
  }
};

// 在生产环境中验证配置
if (config.isProduction) {
  validateConfig();
}

module.exports = config;