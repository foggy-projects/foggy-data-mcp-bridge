// 在最开始注册中文字体，确保在任何 canvas 创建之前完成
const { registerFont } = require('canvas');
const fs = require('fs');
const fontPath = '/app/fonts/NotoSansCJK-Regular.ttc';
if (fs.existsSync(fontPath)) {
  registerFont(fontPath, { family: 'NotoSansCJK' });
  console.log('[Font] Chinese font registered:', fontPath);
}

const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const compression = require('compression');
const rateLimit = require('express-rate-limit');
const path = require('path');

const logger = require('./utils/logger');
const config = require('./config');
const renderRoutes = require('./routes/render');
const healthRoutes = require('./routes/health');
const assetsRoutes = require('./routes/assets');
const { errorHandler, notFoundHandler } = require('./middleware/errorHandler');
const authMiddleware = require('./middleware/auth');

const app = express();

// 基础中间件
app.use(helmet({
  contentSecurityPolicy: false // 允许测试页面加载内联脚本
}));
app.use(compression());
app.use(cors({
  origin: process.env.CORS_ORIGIN || '*',
  credentials: true
}));

// 限流
const limiter = rateLimit({
  windowMs: config.RATE_LIMIT_WINDOW_MS,
  max: config.RATE_LIMIT_MAX_REQUESTS,
  message: {
    error: 'Too many requests',
    message: 'Rate limit exceeded. Please try again later.'
  }
});
app.use(limiter);

// 解析JSON
app.use(express.json({ limit: '10mb' }));

// 请求日志
app.use((req, res, next) => {
  logger.info('Incoming request', {
    method: req.method,
    url: req.url,
    userAgent: req.get('User-Agent'),
    ip: req.ip
  });
  next();
});

// 健康检查路由 (无需认证)
app.use('/healthz', healthRoutes);
app.use('/health', healthRoutes);

// 静态资源路由 (无需认证，供内部HTML页面使用)
app.use('/assets', assetsRoutes);

// 测试页面 (无需认证)
app.use('/test', express.static(path.join(__dirname, '../public')));

// 根路径重定向到测试页面
app.get('/', (req, res) => {
  res.redirect('/test/test.html');
});

// 认证中间件 (除健康检查和静态资源外的所有路由)
app.use(authMiddleware);

// 渲染服务路由
app.use('/render', renderRoutes);

// 错误处理
app.use(notFoundHandler);
app.use(errorHandler);

// 优雅关闭处理
let server;

const gracefulShutdown = (signal) => {
  logger.info(`Received ${signal}, starting graceful shutdown`);

  if (server) {
    server.close((err) => {
      if (err) {
        logger.error('Error during server close', { error: err.message });
        process.exit(1);
      }
      logger.info('Server closed successfully');
      process.exit(0);
    });

    // 强制关闭超时
    setTimeout(() => {
      logger.error('Forced shutdown after timeout');
      process.exit(1);
    }, 30000);
  } else {
    process.exit(0);
  }
};

// 监听关闭信号
process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

// 未捕获异常处理
process.on('uncaughtException', (err) => {
  logger.error('Uncaught Exception', { error: err.message, stack: err.stack });
  process.exit(1);
});

process.on('unhandledRejection', (reason, promise) => {
  logger.error('Unhandled Rejection', { reason, promise });
  process.exit(1);
});

// 启动服务器
const PORT = config.PORT || 3000;

server = app.listen(PORT, '0.0.0.0', () => {
  logger.info('Chart Render Service started', {
    port: PORT,
    env: process.env.NODE_ENV,
    version: require('../package.json').version
  });
  logger.info('Test page available at: http://localhost:' + PORT + '/test/test.html');
});

module.exports = app;
