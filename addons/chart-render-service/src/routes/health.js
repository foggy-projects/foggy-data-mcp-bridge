const express = require('express');
const os = require('os');
const { performance } = require('perf_hooks');
const logger = require('../utils/logger');
const config = require('../config');

const router = express.Router();

// 服务启动时间
const startTime = Date.now();

/**
 * 健康检查端点
 * GET /healthz
 */
router.get('/', async (req, res) => {
  try {
    const now = Date.now();
    const uptime = now - startTime;

    // 基础健康信息
    const health = {
      status: 'healthy',
      timestamp: new Date().toISOString(),
      uptime: `${Math.floor(uptime / 1000)}s`,
      version: require('../../package.json').version,
      environment: config.NODE_ENV
    };

    res.status(200).json(health);
  } catch (error) {
    logger.logError(error, { endpoint: 'health' });
    res.status(503).json({
      status: 'unhealthy',
      timestamp: new Date().toISOString(),
      error: error.message
    });
  }
});

/**
 * 详细健康检查端点
 * GET /healthz/detailed
 */
router.get('/detailed', async (req, res) => {
  try {
    const now = Date.now();
    const uptime = now - startTime;
    const memUsage = process.memoryUsage();

    // Puppeteer已移除 - 仅使用服务端Canvas渲染
    const puppeteerStatus = 'removed';

    // 详细健康信息
    const detailedHealth = {
      status: 'healthy',
      timestamp: new Date().toISOString(),
      uptime: `${Math.floor(uptime / 1000)}s`,
      version: require('../../package.json').version,
      environment: config.NODE_ENV,

      // 系统信息
      system: {
        platform: os.platform(),
        arch: os.arch(),
        nodeVersion: process.version,
        cpus: os.cpus().length,
        loadAverage: os.loadavg(),
        freeMemory: `${Math.round(os.freemem() / 1024 / 1024)}MB`,
        totalMemory: `${Math.round(os.totalmem() / 1024 / 1024)}MB`
      },

      // 进程信息
      process: {
        pid: process.pid,
        memoryUsage: {
          rss: `${Math.round(memUsage.rss / 1024 / 1024)}MB`,
          heapUsed: `${Math.round(memUsage.heapUsed / 1024 / 1024)}MB`,
          heapTotal: `${Math.round(memUsage.heapTotal / 1024 / 1024)}MB`,
          external: `${Math.round(memUsage.external / 1024 / 1024)}MB`
        },
        cpuUsage: process.cpuUsage()
      },

      // 服务配置
      config: {
        port: config.PORT,
        maxWidth: config.MAX_WIDTH,
        maxHeight: config.MAX_HEIGHT,
        renderTimeout: config.RENDER_TIMEOUT,
        maxConcurrentRenders: config.MAX_CONCURRENT_RENDERS,
        logLevel: config.LOG_LEVEL,
        puppeteerExecutablePath: config.PUPPETEER_EXECUTABLE_PATH,
        puppeteerArgs: config.PUPPETEER_ARGS,
        authToken: config.AUTH_TOKEN ? `${config.AUTH_TOKEN.substring(0, 8)}...` : 'not set'
      },

      // 依赖状态
      dependencies: {
        puppeteer: puppeteerStatus,
        echarts: 'available', // ECharts始终可用
        canvas: 'available' // 服务端Canvas渲染
      }
    };

    res.status(200).json(detailedHealth);
  } catch (error) {
    logger.logError(error, { endpoint: 'health-detailed' });
    res.status(503).json({
      status: 'unhealthy',
      timestamp: new Date().toISOString(),
      error: error.message
    });
  }
});

/**
 * 就绪检查端点
 * GET /healthz/ready
 */
router.get('/ready', async (req, res) => {
  try {
    // 检查关键依赖是否就绪
    const checks = [];

    // Puppeteer已移除 - 不再检查
    checks.push({ name: 'puppeteer', status: 'removed', note: 'Using server-side Canvas rendering' });

    // 检查ECharts
    try {
      const echarts = require('echarts');
      checks.push({ name: 'echarts', status: 'ready' });
    } catch (error) {
      checks.push({ name: 'echarts', status: 'not_ready', error: error.message });
    }

    // 检查内存使用情况
    const memUsage = process.memoryUsage();
    const memUsageMB = Math.round(memUsage.rss / 1024 / 1024);
    const memoryOk = memUsageMB < config.MAX_MEMORY_MB;

    checks.push({
      name: 'memory',
      status: memoryOk ? 'ready' : 'warning',
      details: {
        current: `${memUsageMB}MB`,
        limit: `${config.MAX_MEMORY_MB}MB`
      }
    });

    // 确定整体状态（忽略已移除的依赖）
    const allReady = checks.filter(c => c.status !== 'removed').every(check => check.status === 'ready');
    const hasWarnings = checks.some(check => check.status === 'warning');

    const readiness = {
      status: allReady ? (hasWarnings ? 'ready_with_warnings' : 'ready') : 'not_ready',
      timestamp: new Date().toISOString(),
      checks
    };

    const statusCode = allReady ? 200 : 503;
    res.status(statusCode).json(readiness);

  } catch (error) {
    logger.logError(error, { endpoint: 'health-ready' });
    res.status(503).json({
      status: 'not_ready',
      timestamp: new Date().toISOString(),
      error: error.message
    });
  }
});

/**
 * 存活检查端点
 * GET /healthz/live
 */
router.get('/live', (req, res) => {
  // 简单的存活检查
  res.status(200).json({
    status: 'alive',
    timestamp: new Date().toISOString(),
    pid: process.pid
  });
});

module.exports = router;