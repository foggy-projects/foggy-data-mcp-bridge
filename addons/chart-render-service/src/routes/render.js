const express = require('express');
const Joi = require('joi');
const { asyncHandler, ValidationError } = require('../middleware/errorHandler');
const logger = require('../utils/logger');
const config = require('../config');
const UnifiedRenderer = require('../services/unifiedRenderer');
const NativeRenderer = require('../services/nativeRenderer');
const RenderQueue = require('../services/renderQueue');
const ImageStorage = require('../utils/imageStorage');

const router = express.Router();

/**
 * 发送Buffer数据 - 简单方式，仅对大文件进行必要处理
 * @param {Response} res Express响应对象
 * @param {Buffer} buffer 要发送的数据
 */
function sendBuffer(res, buffer) {
  const size = buffer.length;

  // 小于64KB的直接发送，就像Java一样简单
  if (size <= 65536) {
    res.end(buffer);
    return;
  }

  // 大于64KB的需要分块，以兼容某些客户端的64KB缓冲区限制
  // 这是客户端的限制，不是服务端的问题
  const chunkSize = 32768; // 32KB chunks
  let offset = 0;

  while (offset < size) {
    const end = Math.min(offset + chunkSize, size);
    const chunk = buffer.slice(offset, end);
    res.write(chunk);
    offset = end;
  }

  res.end();
}

// 创建渲染队列实例
const renderQueue = new RenderQueue(config.MAX_CONCURRENT_RENDERS);

// 创建图片存储实例
const imageStorage = new ImageStorage(config);

// 图片规格验证schema
const imageSpecSchema = Joi.object({
  format: Joi.string().valid(...config.SUPPORTED_FORMATS).default('png'),
  width: Joi.number().integer().min(100).max(config.MAX_WIDTH).default(config.DEFAULT_WIDTH),
  height: Joi.number().integer().min(100).max(config.MAX_HEIGHT).default(config.DEFAULT_HEIGHT),
  quality: Joi.number().min(0.1).max(1.0).default(1.0), // 仅对JPG有效
  backgroundColor: Joi.string().default('#ffffff')
});

// 标准化图片规格（兼容大小写）
const normalizeImageSpec = (imageSpec) => {
  if (imageSpec && typeof imageSpec === 'object' && imageSpec.format) {
    imageSpec.format = imageSpec.format.toLowerCase();
  }
  return imageSpec;
};

// 标准化统一语义配置（兼容大小写）
const normalizeUnifiedSpec = (unifiedSpec) => {
  if (unifiedSpec && typeof unifiedSpec === 'object' && unifiedSpec.type) {
    unifiedSpec.type = unifiedSpec.type.toLowerCase();
  }
  return unifiedSpec;
};

// 统一语义渲染验证schema
const unifiedRenderSchema = Joi.object({
  unified: Joi.object().required(),
  data: Joi.array().required(),
  image: imageSpecSchema.default({})
});

// 原生ECharts渲染验证schema
const nativeRenderSchema = Joi.object({
  engine: Joi.string().valid('echarts').default('echarts'),
  engine_spec: Joi.object().required(),
  data: Joi.array().optional(),
  image: imageSpecSchema.default({})
});

/**
 * 统一语义图表渲染
 * POST /render/unified
 */
router.post('/unified', asyncHandler(async (req, res) => {
  const startTime = Date.now();

  try {
    // 标准化图片格式（兼容大小写）
    if (req.body.image) {
      req.body.image = normalizeImageSpec(req.body.image);
    }

    // 标准化统一语义配置（兼容大小写）
    if (req.body.unified) {
      req.body.unified = normalizeUnifiedSpec(req.body.unified);
    }

    // 参数验证
    const { error, value } = unifiedRenderSchema.validate(req.body);
    if (error) {
      throw new ValidationError(`Invalid request: ${error.details[0].message}`);
    }

    const { unified, data, image } = value;

    // 添加请求详细日志用于调试
    console.log('=== Unified Render Request ===');
    console.log('Unified spec:', JSON.stringify(unified, null, 2));
    console.log('Data rows count:', data ? data.length : 0);
    console.log('First 3 data rows:', data ? data.slice(0, 3) : []);

    logger.info('Starting unified chart render', {
      chartType: unified.type,
      dataPoints: data.length,
      imageSpec: image,
      requestId: req.id
    });

    // 添加到渲染队列
    const renderResult = await renderQueue.add(async () => {
      const renderer = new UnifiedRenderer(config);
      return await renderer.render(unified, data, image);
    });

    const duration = Date.now() - startTime;

    logger.logRender('unified', duration, {
      chartType: unified.type,
      dataPoints: data.length,
      format: image.format,
      size: `${image.width}x${image.height}`,
      outputSize: renderResult.buffer ? renderResult.buffer.length : renderResult.tempUrl?.length || 0
    });

    // 返回结果
    const response = {
      success: true,
      renderTime: duration,
      format: image.format,
      size: {
        width: image.width,
        height: image.height
      }
    };

    if (renderResult.buffer) {
      // 保存图片到本地
      const saveResult = await imageStorage.saveImage(
        renderResult.buffer,
        image.format,
        unified.type,
        {
          chartType: unified.type,
          dataPoints: data.length,
          width: image.width,
          height: image.height,
          renderTime: duration,
          requestId: req.id,
          endpoint: 'unified',
          // 保存完整入参，方便调试重现
          requestParams: {
            unified: unified,
            data: data.slice(0, 10), // 只保存前10条数据，避免文件过大
            image: image
          },
          dataPreview: data.length > 10 ? `${data.length} rows (showing first 10)` : `${data.length} rows (all shown)`
        }
      );

      // 返回base64编码的图片
      response.image = renderResult.buffer.toString('base64');
      response.mimeType = `image/${image.format}`;

      // 添加本地保存信息到响应
      if (saveResult.saved) {
        response.localSave = {
          saved: true,
          filename: saveResult.filename,
          absolutePath: saveResult.absolutePath
        };
      }
    } else if (renderResult.tempUrl) {
      // 返回临时文件URL
      response.tempUrl = renderResult.tempUrl;
    }

    res.json(response);

  } catch (error) {
    const duration = Date.now() - startTime;
    logger.logError(error, {
      endpoint: 'unified-render',
      duration,
      requestId: req.id
    });
    throw error;
  }
}));

/**
 * 原生ECharts图表渲染
 * POST /render/native
 */
router.post('/native', asyncHandler(async (req, res) => {
  const startTime = Date.now();

  try {
    // 标准化图片格式（兼容大小写）
    if (req.body.image) {
      req.body.image = normalizeImageSpec(req.body.image);
    }

    // 参数验证
    const { error, value } = nativeRenderSchema.validate(req.body);
    if (error) {
      throw new ValidationError(`Invalid request: ${error.details[0].message}`);
    }

    const { engine, engine_spec, data, image } = value;

    logger.info('Starting native chart render', {
      engine,
      hasData: !!data,
      dataPoints: data ? data.length : 0,
      imageSpec: image,
      requestId: req.id
    });

    // 添加到渲染队列
    const renderResult = await renderQueue.add(async () => {
      const renderer = new NativeRenderer(config);
      return await renderer.render(engine_spec, data, image);
    });

    const duration = Date.now() - startTime;

    logger.logRender('native', duration, {
      engine,
      format: image.format,
      size: `${image.width}x${image.height}`,
      outputSize: renderResult.buffer ? renderResult.buffer.length : renderResult.tempUrl?.length || 0
    });

    // 返回结果
    const response = {
      success: true,
      renderTime: duration,
      format: image.format,
      size: {
        width: image.width,
        height: image.height
      }
    };

    if (renderResult.buffer) {
      // 保存图片到本地
      const chartType = engine_spec.series && engine_spec.series[0] ? engine_spec.series[0].type : 'unknown';
      const saveResult = await imageStorage.saveImage(
        renderResult.buffer,
        image.format,
        chartType,
        {
          engine,
          chartType,
          dataPoints: data ? data.length : 0,
          width: image.width,
          height: image.height,
          renderTime: duration,
          requestId: req.id,
          hasCustomData: !!data
        }
      );

      // 返回base64编码的图片
      response.image = renderResult.buffer.toString('base64');
      response.mimeType = `image/${image.format}`;

      // 添加本地保存信息到响应
      if (saveResult.saved) {
        response.localSave = {
          saved: true,
          filename: saveResult.filename,
          absolutePath: saveResult.absolutePath
        };
      }
    } else if (renderResult.tempUrl) {
      // 返回临时文件URL
      response.tempUrl = renderResult.tempUrl;
    }

    res.json(response);

  } catch (error) {
    const duration = Date.now() - startTime;
    logger.logError(error, {
      endpoint: 'native-render',
      duration,
      requestId: req.id
    });
    throw error;
  }
}));

/**
 * 获取渲染队列状态
 * GET /render/queue/status
 */
router.get('/queue/status', asyncHandler(async (req, res) => {
  const queueStatus = renderQueue.getStatus();

  res.json({
    success: true,
    timestamp: new Date().toISOString(),
    queue: queueStatus
  });
}));

/**
 * 获取图片存储统计信息
 * GET /render/storage/stats
 */
router.get('/storage/stats', asyncHandler(async (req, res) => {
  const stats = await imageStorage.getStorageStats();

  res.json({
    success: true,
    timestamp: new Date().toISOString(),
    storage: stats
  });
}));

/**
 * 清理旧图片文件
 * DELETE /render/storage/cleanup
 */
router.delete('/storage/cleanup', asyncHandler(async (req, res) => {
  const maxAgeHours = parseInt(req.query.maxAge) || 24;
  const result = await imageStorage.cleanupOldImages(maxAgeHours);

  res.json({
    success: true,
    timestamp: new Date().toISOString(),
    cleanup: result
  });
}));

/**
 * 测试渲染端点 (仅开发环境)
 * POST /render/test
 */
if (config.isDevelopment || config.ENABLE_DEBUG_ENDPOINT) {
  router.post('/test', asyncHandler(async (req, res) => {
    const testChart = {
      engine_spec: {
        title: {
          text: '测试图表'
        },
        xAxis: {
          type: 'category',
          data: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
        },
        yAxis: {
          type: 'value'
        },
        series: [{
          data: [120, 200, 150, 80, 70, 110, 130],
          type: 'bar'
        }]
      },
      image: {
        format: 'png',
        width: 800,
        height: 600
      }
    };

    const startTime = Date.now();
    const renderer = new NativeRenderer(config);
    const result = await renderer.render(testChart.engine_spec, null, testChart.image);
    const duration = Date.now() - startTime;

    res.json({
      success: true,
      message: 'Test render completed',
      renderTime: duration,
      hasBuffer: !!result.buffer,
      bufferSize: result.buffer ? result.buffer.length : 0
    });
  }));
}

/**
 * 统一图表渲染 - 文件流模式
 * POST /render/unified/stream
 *
 * 与 /unified 接口相同的输入参数，但直接返回图片文件流而不是 JSON
 * 适用于需要直接获取图片文件的场景，避免 Base64 编码/解码的开销
 */
router.post('/unified/stream', asyncHandler(async (req, res) => {
  const startTime = Date.now();

  // 声明变量在外部作用域，方便错误处理时访问
  let data, unified, imageSpec;

  try {
    // 从请求体解构数据
    ({ data, unified, image = {} } = req.body);  // 提供默认空对象

    // 验证必需的参数
    if (!data || !unified) {
      throw new ValidationError('Missing required fields: data and unified are required');
    }

    // 验证并标准化图片规格
    const normalizedImageSpec = normalizeImageSpec(image);
    const { error: imageError, value: validatedImageSpec } = imageSpecSchema.validate(normalizedImageSpec || {});

    if (imageError) {
      throw new ValidationError(`Invalid image specification: ${imageError.details[0].message}`);
    }

    imageSpec = validatedImageSpec;

    logger.info('Starting unified render - stream mode', {
      dataPoints: data.length,
      chartType: unified.type,
      imageFormat: imageSpec.format,
      imageSize: `${imageSpec.width}x${imageSpec.height}`,
      requestId: req.id
    });

    // 创建渲染器并执行渲染
    const renderer = new UnifiedRenderer(config);
    const renderResult = await renderQueue.add(async () => {
      return await renderer.render(unified, data, imageSpec);
    });

    if (!renderResult.buffer) {
      throw new Error('Render failed to produce buffer');
    }

    const renderTime = Date.now() - startTime;

    logger.info('Unified render stream completed', {
      renderTime,
      format: imageSpec.format,
      size: `${imageSpec.width}x${imageSpec.height}`,
      bufferSize: renderResult.buffer.length,
      requestId: req.id
    });

    // 保存到本地备份（异步，不阻塞响应）
    const metadata = {
      chartType: unified.type || 'unified',
      renderTime,
      imageSpec,
      dataSize: data.length,
      requestId: req.id,
      endpoint: 'unified-stream',
      // 保存完整入参，方便调试重现
      requestParams: {
        unified: unified,
        data: data.slice(0, 10), // 只保存前10条数据，避免文件过大
        image: imageSpec
      },
      dataPreview: data.length > 10 ? `${data.length} rows (showing first 10)` : `${data.length} rows (all shown)`
    };

    imageStorage.saveImage(renderResult.buffer, imageSpec.format, unified.type || 'unified', metadata)
      .then(result => {
        if (result.saved) {
          logger.info('Chart saved locally for analysis', {
            filename: result.filename,
            chartType: unified.type || 'unified'
          });
        }
      })
      .catch(error => {
        logger.warn('Failed to save chart locally', { error: error.message });
      });

    // 设置响应头
    const mimeType = `image/${imageSpec.format}`;
    const filename = `chart_${unified.type || 'unified'}_${Date.now()}.${imageSpec.format}`;

    res.set({
      'Content-Type': mimeType,
      'Content-Length': renderResult.buffer.length,
      'Content-Disposition': `inline; filename="${filename}"`,
      'Cache-Control': 'public, max-age=3600', // 缓存1小时
      'X-Render-Time': renderTime,
      'X-Chart-Type': unified.type || 'unified',
      'X-Image-Format': imageSpec.format,
      'X-Image-Size': `${imageSpec.width}x${imageSpec.height}`
    });

    // 直接发送buffer，就像Java一样简单
    sendBuffer(res, renderResult.buffer);

  } catch (error) {
    const duration = Date.now() - startTime;

    // 保存错误请求的参数，方便调试
    try {
      const errorMetadata = {
        error: {
          message: error.message,
          stack: error.stack,
          name: error.name,
          timestamp: new Date().toISOString()
        },
        renderTime: duration,
        endpoint: 'unified-render-stream',
        requestId: req.id,
        userAgent: req.get('User-Agent'),
        clientIp: req.ip,
        // 保存完整入参，方便与上游核对
        requestParams: {
          unified: unified,
          data: data ? data.slice(0, 10) : undefined, // 只保存前10条数据
          image: imageSpec
        },
        dataPreview: data ? (data.length > 10 ? `${data.length} rows (showing first 10)` : `${data.length} rows (all shown)`) : 'no data',
        rawRequestBody: JSON.stringify(req.body).substring(0, 2000) // 限制长度，避免过大
      };

      // 使用 fs 直接保存错误信息（因为 imageStorage 主要用于图片）
      const fs = require('fs').promises;
      const path = require('path');

      const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
      const randomSuffix = Math.random().toString(36).substring(2, 8);
      const errorFileName = `error_${unified?.type || 'unknown'}_${timestamp}_${randomSuffix}.json`;
      const errorFilePath = path.join(config.LOCAL_IMAGES_DIR, errorFileName);

      await fs.writeFile(errorFilePath, JSON.stringify(errorMetadata, null, 2));

      logger.warn('Error request parameters saved for analysis', {
        errorFile: errorFileName,
        errorType: error.name,
        chartType: unified?.type || 'unknown'
      });

    } catch (saveError) {
      logger.error('Failed to save error request parameters', {
        saveError: saveError.message,
        originalError: error.message
      });
    }

    logger.logError(error, {
      endpoint: 'unified-render-stream',
      duration,
      requestId: req.id
    });
    throw error;
  }
}));

/**
 * 测试流传输端点 - 用于验证大文件传输
 * GET /render/test-stream
 */
router.get('/test-stream', (req, res) => {
  const fs = require('fs');
  const path = require('path');

  // 使用已经生成的82KB图片进行测试
  const imagePath = path.join(__dirname, '../../images/chart_pie_2025-09-25T08-35-33-060Z_z6pknq.png');

  try {
    const buffer = fs.readFileSync(imagePath);
    console.log(`Test stream: sending ${buffer.length} bytes`);

    res.setHeader('Content-Type', 'image/png');
    res.setHeader('Content-Length', buffer.length);

    // 使用我们的sendBuffer函数
    sendBuffer(res, buffer);
  } catch (error) {
    res.status(404).send('Test image not found');
  }
});

/**
 * 原生 ECharts 渲染 - 文件流模式
 * POST /render/native/stream
 *
 * 与 /native 接口相同的输入参数，但直接返回图片文件流而不是 JSON
 */
router.post('/native/stream', asyncHandler(async (req, res) => {
  const startTime = Date.now();

  try {
    const { engine_spec, data, image } = req.body;

    // 验证必需的参数
    if (!engine_spec) {
      throw new ValidationError('Missing required field: engine_spec is required');
    }

    // 验证并标准化图片规格
    const normalizedImageSpec = normalizeImageSpec(image);
    const { error: imageError, value: imageSpec } = imageSpecSchema.validate(normalizedImageSpec);

    if (imageError) {
      throw new ValidationError(`Invalid image specification: ${imageError.details[0].message}`);
    }

    logger.info('Starting native render - stream mode', {
      hasData: !!data,
      imageFormat: imageSpec.format,
      imageSize: `${imageSpec.width}x${imageSpec.height}`,
      requestId: req.id
    });

    // 创建渲染器并执行渲染
    const renderer = new NativeRenderer(config);
    const renderResult = await renderQueue.add(async () => {
      return await renderer.render(engine_spec, data, imageSpec);
    });

    if (!renderResult.buffer) {
      throw new Error('Render failed to produce buffer');
    }

    const renderTime = Date.now() - startTime;

    logger.info('Native render stream completed', {
      renderTime,
      format: imageSpec.format,
      size: `${imageSpec.width}x${imageSpec.height}`,
      bufferSize: renderResult.buffer.length,
      requestId: req.id
    });

    // 设置响应头
    const mimeType = `image/${imageSpec.format}`;
    const chartType = engine_spec.series && engine_spec.series[0] ? engine_spec.series[0].type : 'chart';
    const filename = `chart_${chartType}_${Date.now()}.${imageSpec.format}`;

    res.set({
      'Content-Type': mimeType,
      'Content-Length': renderResult.buffer.length,
      'Content-Disposition': `inline; filename="${filename}"`,
      'Cache-Control': 'public, max-age=3600', // 缓存1小时
      'X-Render-Time': renderTime,
      'X-Chart-Type': chartType,
      'X-Image-Format': imageSpec.format,
      'X-Image-Size': `${imageSpec.width}x${imageSpec.height}`
    });

    // 直接发送buffer，就像Java一样简单
    sendBuffer(res, renderResult.buffer);

  } catch (error) {
    const duration = Date.now() - startTime;
    logger.logError(error, {
      endpoint: 'native-render-stream',
      duration,
      requestId: req.id
    });
    throw error;
  }
}));

module.exports = router;