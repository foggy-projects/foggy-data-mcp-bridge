const express = require('express');
const path = require('path');
const fs = require('fs');
const logger = require('../utils/logger');

const router = express.Router();

/**
 * 静态资源服务路由
 * 提供本地ECharts等静态资源，避免CDN依赖
 */

// ECharts完整版本
router.get('/echarts.min.js', (req, res) => {
  try {
    const echartsPath = path.join(__dirname, '../../node_modules/echarts/dist/echarts.min.js');

    if (!fs.existsSync(echartsPath)) {
      logger.warn('ECharts file not found', { path: echartsPath });
      return res.status(404).json({ error: 'ECharts file not found' });
    }

    res.setHeader('Content-Type', 'application/javascript; charset=utf-8');
    res.setHeader('Cache-Control', 'public, max-age=86400'); // 缓存1天
    res.sendFile(echartsPath);

    logger.debug('Served ECharts static resource', { userAgent: req.get('User-Agent') });
  } catch (error) {
    logger.error('Error serving ECharts static resource', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// ECharts简化版本（如果需要更小的文件）
router.get('/echarts.simple.min.js', (req, res) => {
  try {
    const echartsPath = path.join(__dirname, '../../node_modules/echarts/dist/echarts.simple.min.js');

    if (!fs.existsSync(echartsPath)) {
      logger.warn('ECharts simple file not found', { path: echartsPath });
      return res.status(404).json({ error: 'ECharts simple file not found' });
    }

    res.setHeader('Content-Type', 'application/javascript; charset=utf-8');
    res.setHeader('Cache-Control', 'public, max-age=86400');
    res.sendFile(echartsPath);

    logger.debug('Served ECharts simple static resource', { userAgent: req.get('User-Agent') });
  } catch (error) {
    logger.error('Error serving ECharts simple static resource', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// 健康检查：验证静态资源可用性
router.get('/health', (req, res) => {
  try {
    const echartsPath = path.join(__dirname, '../../node_modules/echarts/dist/echarts.min.js');
    const echartsSimplePath = path.join(__dirname, '../../node_modules/echarts/dist/echarts.simple.min.js');

    const status = {
      'echarts.min.js': fs.existsSync(echartsPath),
      'echarts.simple.min.js': fs.existsSync(echartsSimplePath)
    };

    const allAvailable = Object.values(status).every(available => available);

    res.status(allAvailable ? 200 : 503).json({
      status: allAvailable ? 'healthy' : 'degraded',
      resources: status,
      timestamp: new Date().toISOString()
    });
  } catch (error) {
    logger.error('Error checking assets health', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

module.exports = router;