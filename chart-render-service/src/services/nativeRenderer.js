const BaseRenderer = require('./baseRenderer');
const { RenderError } = require('../middleware/errorHandler');
const logger = require('../utils/logger');

/**
 * 原生ECharts渲染器
 * 直接使用ECharts配置进行渲染
 */
class NativeRenderer extends BaseRenderer {
  constructor(config) {
    super(config);
  }

  /**
   * 渲染原生ECharts图表
   * @param {Object} echartsOption ECharts原生配置
   * @param {Array} data 可选的数据数组
   * @param {Object} imageSpec 图片规格
   * @returns {Promise<Object>} 渲染结果
   */
  async render(echartsOption, data, imageSpec) {
    try {
      logger.debug('Starting native chart render', {
        hasData: !!data,
        dataLength: data ? data.length : 0,
        imageSpec
      });

      // 1. 验证ECharts配置
      this.validateEChartsOption(echartsOption);

      // 2. 如果提供了数据，将数据注入到配置中
      let finalOption = { ...echartsOption };
      if (data && Array.isArray(data)) {
        finalOption = this.injectData(finalOption, data);
      }

      // 3. 应用默认配置和安全限制
      finalOption = this.applyDefaults(finalOption);

      // 4. 使用基础渲染器渲染
      const result = await super.renderChart(finalOption, imageSpec);

      logger.debug('Native chart render completed', {
        outputFormat: imageSpec.format
      });

      return result;
    } catch (error) {
      logger.logError(error, {
        renderer: 'native',
        hasOption: !!echartsOption
      });
      throw new RenderError(`Native render failed: ${error.message}`);
    }
  }

  /**
   * 验证ECharts配置
   * @param {Object} option ECharts配置
   */
  validateEChartsOption(option) {
    if (!option || typeof option !== 'object') {
      throw new RenderError('Invalid ECharts option: must be an object');
    }

    // 检查危险的配置项
    this.checkForDangerousOptions(option);

    // 检查复杂度限制
    this.checkComplexityLimits(option);
  }

  /**
   * 检查危险的配置项
   * @param {Object} option ECharts配置
   */
  checkForDangerousOptions(option) {
    const dangerousKeys = ['eval', 'Function', 'constructor', '__proto__'];

    const checkObject = (obj, path = '') => {
      if (!obj || typeof obj !== 'object') return;

      for (const [key, value] of Object.entries(obj)) {
        const fullPath = path ? `${path}.${key}` : key;

        // 检查危险键名
        if (dangerousKeys.some(danger => key.includes(danger))) {
          throw new RenderError(`Dangerous option detected: ${fullPath}`);
        }

        // 检查字符串值中的危险内容
        if (typeof value === 'string' && dangerousKeys.some(danger => value.includes(danger))) {
          throw new RenderError(`Dangerous value detected in: ${fullPath}`);
        }

        // 递归检查
        if (typeof value === 'object') {
          checkObject(value, fullPath);
        }
      }
    };

    checkObject(option);
  }

  /**
   * 检查复杂度限制
   * @param {Object} option ECharts配置
   */
  checkComplexityLimits(option) {
    // 检查series数量
    if (option.series && Array.isArray(option.series)) {
      if (option.series.length > 50) {
        throw new RenderError('Too many series: maximum 50 allowed');
      }

      // 检查每个series的数据点数量
      option.series.forEach((series, index) => {
        if (series.data && Array.isArray(series.data)) {
          if (series.data.length > 10000) {
            throw new RenderError(`Too many data points in series ${index}: maximum 10000 allowed`);
          }
        }
      });
    }

    // 检查颜色数组长度
    if (option.color && Array.isArray(option.color)) {
      if (option.color.length > 100) {
        throw new RenderError('Too many colors: maximum 100 allowed');
      }
    }

    // 检查深度嵌套
    const maxDepth = 20;
    const checkDepth = (obj, depth = 0) => {
      if (depth > maxDepth) {
        throw new RenderError(`Object nesting too deep: maximum ${maxDepth} levels allowed`);
      }

      if (obj && typeof obj === 'object') {
        for (const value of Object.values(obj)) {
          if (typeof value === 'object') {
            checkDepth(value, depth + 1);
          }
        }
      }
    };

    checkDepth(option);
  }

  /**
   * 将数据注入到ECharts配置中
   * @param {Object} option ECharts配置
   * @param {Array} data 数据数组
   * @returns {Object} 注入数据后的配置
   */
  injectData(option, data) {
    const newOption = JSON.parse(JSON.stringify(option)); // 深拷贝

    // 如果配置中没有series或series为空，创建默认series
    if (!newOption.series || !Array.isArray(newOption.series) || newOption.series.length === 0) {
      newOption.series = [{
        type: 'bar',
        data: data
      }];
    } else {
      // 将数据注入到第一个series中（如果它没有数据）
      if (!newOption.series[0].data) {
        newOption.series[0].data = data;
      }
    }

    return newOption;
  }

  /**
   * 应用默认配置
   * @param {Object} option ECharts配置
   * @returns {Object} 应用默认配置后的选项
   */
  applyDefaults(option) {
    const defaultOption = {
      ...this.config.ECHARTS_DEFAULTS,
      ...option
    };

    // 确保关闭动画以提升性能
    defaultOption.animation = false;

    // 确保有适当的边距
    if (!defaultOption.grid) {
      defaultOption.grid = this.config.ECHARTS_DEFAULTS.grid;
    }

    // 强制设置全局字体 - 确保中文正确渲染
    const fontFamily = this.config.ECHARTS_DEFAULTS.textStyle?.fontFamily || 'NotoSansCJK';

    // 设置全局 textStyle
    defaultOption.textStyle = {
      ...defaultOption.textStyle,
      fontFamily: fontFamily
    };

    // 为 title 设置字体
    if (defaultOption.title) {
      defaultOption.title.textStyle = {
        ...defaultOption.title.textStyle,
        fontFamily: fontFamily
      };
      if (defaultOption.title.subtextStyle) {
        defaultOption.title.subtextStyle.fontFamily = fontFamily;
      } else {
        defaultOption.title.subtextStyle = { fontFamily: fontFamily };
      }
    }

    // 为 xAxis 设置字体
    if (defaultOption.xAxis) {
      const axes = Array.isArray(defaultOption.xAxis) ? defaultOption.xAxis : [defaultOption.xAxis];
      axes.forEach(axis => {
        axis.axisLabel = { ...axis.axisLabel, fontFamily: fontFamily };
        axis.nameTextStyle = { ...axis.nameTextStyle, fontFamily: fontFamily };
      });
    }

    // 为 yAxis 设置字体
    if (defaultOption.yAxis) {
      const axes = Array.isArray(defaultOption.yAxis) ? defaultOption.yAxis : [defaultOption.yAxis];
      axes.forEach(axis => {
        axis.axisLabel = { ...axis.axisLabel, fontFamily: fontFamily };
        axis.nameTextStyle = { ...axis.nameTextStyle, fontFamily: fontFamily };
      });
    }

    // 为 legend 设置字体
    if (defaultOption.legend) {
      defaultOption.legend.textStyle = {
        ...defaultOption.legend.textStyle,
        fontFamily: fontFamily
      };
      // 如果series数量过多，隐藏图例
      const seriesCount = defaultOption.series ? defaultOption.series.length : 0;
      if (seriesCount > 10 && !defaultOption.legend.hasOwnProperty('show')) {
        defaultOption.legend.show = false;
      }
    }

    // 为 tooltip 设置字体
    if (defaultOption.tooltip) {
      defaultOption.tooltip.textStyle = {
        ...defaultOption.tooltip.textStyle,
        fontFamily: fontFamily
      };
    }

    return defaultOption;
  }

  /**
   * 获取配置摘要信息（用于日志）
   * @param {Object} option ECharts配置
   * @returns {Object} 配置摘要
   */
  getOptionSummary(option) {
    const summary = {
      hasTitle: !!option.title,
      hasLegend: !!option.legend,
      hasTooltip: !!option.tooltip,
      seriesCount: option.series ? option.series.length : 0,
      seriesTypes: []
    };

    if (option.series) {
      summary.seriesTypes = option.series.map(s => s.type).filter(Boolean);
      summary.totalDataPoints = option.series.reduce((total, s) => {
        return total + (s.data ? s.data.length : 0);
      }, 0);
    }

    return summary;
  }
}

module.exports = NativeRenderer;