const logger = require('../utils/logger');
const config = require('../config');

/**
 * 404错误处理中间件
 */
const notFoundHandler = (req, res, next) => {
  logger.warn('Route not found', {
    method: req.method,
    url: req.url,
    ip: req.ip
  });

  res.status(404).json({
    error: 'Not Found',
    message: `Route ${req.method} ${req.url} not found`,
    timestamp: new Date().toISOString()
  });
};

/**
 * 全局错误处理中间件
 */
const errorHandler = (err, req, res, next) => {
  // 记录错误
  logger.logError(err, {
    method: req.method,
    url: req.url,
    ip: req.ip,
    userAgent: req.get('User-Agent')
  });

  // 确定错误状态码和消息
  let statusCode = err.statusCode || err.status || 500;
  let message = err.message || 'Internal Server Error';

  // 特定错误类型处理
  if (err.name === 'ValidationError') {
    statusCode = 400;
    message = `Validation Error: ${err.message}`;
  } else if (err.name === 'SyntaxError' && err.status === 400) {
    statusCode = 400;
    message = 'Invalid JSON payload';
  } else if (err.code === 'LIMIT_FILE_SIZE') {
    statusCode = 413;
    message = 'Payload too large';
  } else if (err.name === 'TimeoutError') {
    statusCode = 408;
    message = 'Request timeout';
  }

  // 构建错误响应
  const errorResponse = {
    error: getErrorName(statusCode),
    message,
    timestamp: new Date().toISOString(),
    requestId: req.id || 'unknown'
  };

  // 开发环境包含错误栈
  if (config.isDevelopment && err.stack) {
    errorResponse.stack = err.stack;
  }

  // 发送错误响应
  res.status(statusCode).json(errorResponse);
};

/**
 * 根据状态码获取错误名称
 */
const getErrorName = (statusCode) => {
  const errorNames = {
    400: 'Bad Request',
    401: 'Unauthorized',
    403: 'Forbidden',
    404: 'Not Found',
    408: 'Request Timeout',
    413: 'Payload Too Large',
    422: 'Unprocessable Entity',
    429: 'Too Many Requests',
    500: 'Internal Server Error',
    502: 'Bad Gateway',
    503: 'Service Unavailable',
    504: 'Gateway Timeout'
  };

  return errorNames[statusCode] || 'Unknown Error';
};

/**
 * 异步错误处理包装器
 */
const asyncHandler = (fn) => {
  return (req, res, next) => {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
};

/**
 * 创建自定义错误
 */
class CustomError extends Error {
  constructor(message, statusCode = 500, code = null) {
    super(message);
    this.statusCode = statusCode;
    this.code = code;
    this.name = this.constructor.name;
    Error.captureStackTrace(this, this.constructor);
  }
}

/**
 * 验证错误
 */
class ValidationError extends CustomError {
  constructor(message, details = null) {
    super(message, 400);
    this.details = details;
  }
}

/**
 * 渲染错误
 */
class RenderError extends CustomError {
  constructor(message, details = null) {
    super(message, 422);
    this.details = details;
  }
}

/**
 * 超时错误
 */
class TimeoutError extends CustomError {
  constructor(message = 'Operation timeout') {
    super(message, 408);
  }
}

module.exports = {
  notFoundHandler,
  errorHandler,
  asyncHandler,
  CustomError,
  ValidationError,
  RenderError,
  TimeoutError
};