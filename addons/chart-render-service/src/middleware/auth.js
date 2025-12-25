const config = require('../config');
const logger = require('../utils/logger');

/**
 * 认证中间件
 * 验证请求头中的认证token
 * 如果未配置 AUTH_TOKEN 或为空，则跳过认证
 */
const authMiddleware = (req, res, next) => {
  try {
    // 如果未配置 AUTH_TOKEN，跳过认证
    if (!config.AUTH_TOKEN) {
      return next();
    }

    // 获取Authorization头
    const authHeader = req.headers.authorization;

    if (!authHeader) {
      logger.warn('Missing authorization header', {
        url: req.url,
        method: req.method,
        ip: req.ip
      });
      return res.status(401).json({
        error: 'Unauthorized',
        message: 'Missing authorization header'
      });
    }

    // 支持两种格式：
    // 1. Bearer <token>
    // 2. <token>
    let token;
    if (authHeader.startsWith('Bearer ')) {
      token = authHeader.substring(7);
    } else {
      token = authHeader;
    }

    // 验证token
    if (token !== config.AUTH_TOKEN) {
      logger.warn('Invalid authorization token', {
        url: req.url,
        method: req.method,
        ip: req.ip,
        tokenPrefix: token.substring(0, 8) // 只记录前8位
      });
      return res.status(401).json({
        error: 'Unauthorized',
        message: 'Invalid authorization token'
      });
    }

    // 记录成功认证
    logger.debug('Authentication successful', {
      url: req.url,
      method: req.method,
      ip: req.ip
    });

    next();
  } catch (error) {
    logger.logError(error, {
      middleware: 'auth',
      url: req.url,
      method: req.method
    });

    res.status(500).json({
      error: 'Internal Server Error',
      message: 'Authentication error'
    });
  }
};

module.exports = authMiddleware;
