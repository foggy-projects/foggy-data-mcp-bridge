const winston = require('winston');
const config = require('../config');

// 自定义日志格式
const logFormat = winston.format.combine(
  winston.format.timestamp({
    format: 'YYYY-MM-DD HH:mm:ss'
  }),
  winston.format.errors({ stack: true }),
  winston.format.json(),
  winston.format.prettyPrint()
);

// 控制台格式 (开发环境)
const consoleFormat = winston.format.combine(
  winston.format.colorize(),
  winston.format.timestamp({
    format: 'HH:mm:ss'
  }),
  winston.format.printf(({ timestamp, level, message, ...meta }) => {
    let metaStr = '';
    if (Object.keys(meta).length > 0) {
      metaStr = ' ' + JSON.stringify(meta);
    }
    return `[${timestamp}] ${level}: ${message}${metaStr}`;
  })
);

// 创建logger实例
const logger = winston.createLogger({
  level: config.LOG_LEVEL,
  format: logFormat,
  defaultMeta: { service: 'chart-render-service' },
  transports: [
    // 错误日志文件
    new winston.transports.File({
      filename: 'logs/error.log',
      level: 'error',
      maxsize: 5242880, // 5MB
      maxFiles: 5,
    }),

    // 综合日志文件
    new winston.transports.File({
      filename: 'logs/combined.log',
      maxsize: 5242880, // 5MB
      maxFiles: 5,
    })
  ],
});

// 开发环境添加控制台输出
if (config.isDevelopment) {
  logger.add(new winston.transports.Console({
    format: consoleFormat
  }));
} else {
  // 生产环境也输出到控制台，但使用JSON格式
  logger.add(new winston.transports.Console({
    format: winston.format.combine(
      winston.format.timestamp(),
      winston.format.json()
    )
  }));
}

// 创建logs目录
const fs = require('fs');
const path = require('path');
const logsDir = path.join(__dirname, '../../logs');
if (!fs.existsSync(logsDir)) {
  fs.mkdirSync(logsDir, { recursive: true });
}

// 添加自定义方法到logger实例
logger.logRequest = (req, res, responseTime) => {
  logger.info('Request completed', {
    method: req.method,
    url: req.url,
    statusCode: res.statusCode,
    responseTime: `${responseTime}ms`,
    userAgent: req.get('User-Agent'),
    ip: req.ip
  });
};

logger.logError = (error, context = {}) => {
  logger.error('Error occurred', {
    error: error.message,
    stack: error.stack,
    ...context
  });
};

logger.logRender = (type, duration, options = {}) => {
  logger.info('Chart rendered', {
    type,
    duration: `${duration}ms`,
    ...options
  });
};

logger.logPerformance = (operation, duration, metadata = {}) => {
  logger.info('Performance metric', {
    operation,
    duration: `${duration}ms`,
    ...metadata
  });
};

// 导出logger实例
module.exports = logger;