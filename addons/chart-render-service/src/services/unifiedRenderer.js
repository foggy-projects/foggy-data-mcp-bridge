const echarts = require('echarts');
const BaseRenderer = require('./baseRenderer');
const { RenderError } = require('../middleware/errorHandler');
const logger = require('../utils/logger');
const ResponsiveChart = require('../utils/responsiveChart');

/**
 * 统一语义图表渲染器
 * 将统一的图表语义转换为ECharts配置并渲染
 */
class UnifiedRenderer extends BaseRenderer {
  constructor(config) {
    super(config);
    this.responsiveChart = new ResponsiveChart();
  }

  /**
   * 渲染统一语义图表
   * @param {Object} unified 统一图表语义
   * @param {Array} data 数据数组
   * @param {Object} imageSpec 图片规格
   * @returns {Promise<Object>} 渲染结果
   */
  async render(unified, data, imageSpec) {
    try {
      // 确保 imageSpec 不为 undefined
      if (!imageSpec) {
        imageSpec = {
          format: 'png',
          width: 800,
          height: 600,
          backgroundColor: '#ffffff'
        };
        logger.warn('imageSpec was undefined, using defaults');
      }

      logger.debug('Starting unified chart render', {
        chartType: unified.type,
        dataLength: data.length,
        imageSpec
      });

      // 1. 数据预处理
      const processedData = this.preprocessData(data, unified);

      // 2. 转换为ECharts配置
      const echartsOption = this.convertToEChartsOption(unified, processedData, imageSpec);

      // 3. 使用基础渲染器渲染
      const result = await super.renderChart(echartsOption, imageSpec);

      logger.debug('Unified chart render completed', {
        chartType: unified.type,
        outputFormat: imageSpec.format
      });

      return result;
    } catch (error) {
      logger.logError(error, {
        chartType: unified.type,
        renderer: 'unified'
      });
      throw new RenderError(`Unified render failed: ${error.message}`);
    }
  }

  /**
   * 数据预处理
   * @param {Array} data 原始数据
   * @param {Object} unified 统一语义配置
   * @returns {Array} 处理后的数据
   */
  preprocessData(data, unified) {
    if (!Array.isArray(data)) {
      throw new RenderError('Data must be an array');
    }

    let processedData = [...data];

    // TopN 处理
    if (unified.topN && typeof unified.topN === 'number' && unified.topN > 0) {
      const topN = Math.min(unified.topN, this.config.MAX_TOP_N || 1000);

      if (processedData.length > topN) {
        // 根据图表类型确定排序字段
        const sortField = this.getSortField(unified);

        if (sortField) {
          processedData.sort((a, b) => {
            const aVal = this.getFieldValue(a, sortField);
            const bVal = this.getFieldValue(b, sortField);
            return (bVal || 0) - (aVal || 0); // 降序
          });
        }

        // 截取TopN，其余合并为"其他"
        const topData = processedData.slice(0, topN);
        const otherData = processedData.slice(topN);

        if (otherData.length > 0) {
          const otherItem = this.aggregateOthers(otherData, unified);
          topData.push(otherItem);
        }

        processedData = topData;
      }
    }

    return processedData;
  }

  /**
   * 转换为ECharts配置
   * @param {Object} unified 统一语义
   * @param {Array} data 处理后的数据
   * @param {Object} imageSpec 图片规格
   * @returns {Object} ECharts配置
   */
  convertToEChartsOption(unified, data, imageSpec) {
    const baseOption = {
      ...this.config.ECHARTS_DEFAULTS,
      title: this.buildTitle(unified, imageSpec),
      tooltip: this.buildTooltip(unified, imageSpec)
    };

    let chartSpecificOption = {};

    // 根据图表类型构建配置
    switch (unified.type) {
      case 'bar':
      case 'column':
        chartSpecificOption = this.buildBarChart(unified, data, imageSpec);
        break;

      case 'line':
        chartSpecificOption = this.buildLineChart(unified, data, imageSpec);
        break;

      case 'pie':
      case 'doughnut':
        chartSpecificOption = this.buildPieChart(unified, data, imageSpec);
        break;

      case 'scatter':
        chartSpecificOption = this.buildScatterChart(unified, data, imageSpec);
        break;

      case 'area':
        chartSpecificOption = this.buildAreaChart(unified, data, imageSpec);
        break;

      default:
        throw new RenderError(`Unsupported chart type: ${unified.type}`);
    }

    // 智能合并配置：如果chart-specific没有legend，使用base的legend
    const finalOption = { ...baseOption, ...chartSpecificOption };

    if (!chartSpecificOption.legend && (unified.showLegend !== false)) {
      finalOption.legend = this.buildLegend(unified, imageSpec);
    }

    console.log('=== CONFIG MERGE DEBUG ===');
    console.log('Base option keys:', Object.keys(baseOption));
    console.log('Chart specific keys:', Object.keys(chartSpecificOption));
    console.log('Chart specific has legend:', !!chartSpecificOption.legend);
    console.log('Final option keys:', Object.keys(finalOption));

    return finalOption;
  }

  /**
   * 构建标题配置
   */
  buildTitle(unified, imageSpec = {}) {
    if (!unified.title) return null;

    const { width = 800, height = 600 } = imageSpec;

    return this.responsiveChart.buildResponsiveTitle({
      title: unified.title,
      width,
      height
    });
  }

  /**
   * 构建提示框配置
   */
  buildTooltip(unified, imageSpec = {}) {
    const { width = 800 } = imageSpec;

    const baseConfig = this.responsiveChart.buildResponsiveTooltip({ width });

    return {
      ...baseConfig,
      trigger: unified.type === 'pie' || unified.type === 'doughnut' ? 'item' : 'axis'
    };
  }

  /**
   * 构建图例配置
   */
  buildLegend(unified, imageSpec = {}) {
    if (!unified.showLegend) return null;

    const { width = 800, height = 600 } = imageSpec;

    return this.responsiveChart.buildResponsiveLegend({
      width,
      height,
      seriesCount: 1,
      seriesNames: ['数值'],
      showLegend: unified.showLegend !== false
    });
  }

  /**
   * 构建柱状图配置
   */
  buildBarChart(unified, data, imageSpec) {
    // 检查是否为多系列图表
    if (unified.seriesField || unified.groupBy) {
      return this.buildMultiSeriesBarChart(unified, data, imageSpec);
    }
    
    const categories = data.map(item => this.getFieldValue(item, unified.xField));
    const values = data.map(item => this.getFieldValue(item, unified.yField));

    const isHorizontal = unified.type === 'bar';
    
    return {
      xAxis: {
        type: isHorizontal ? 'value' : 'category',
        data: isHorizontal ? null : categories,
        name: !isHorizontal ? (unified.xAxis?.label || unified.xLabel || '') : (unified.yAxis?.label || unified.yLabel || ''),
        nameTextStyle: {
          fontSize: 12,
          color: '#666'
        },
        axisLabel: {
          rotate: !isHorizontal && categories.some(cat => String(cat).length > 4) ? 45 : 0,
          formatter: isHorizontal ? function(value) {
            if (value >= 1000000) {
              return (value / 1000000).toFixed(1) + 'M';
            } else if (value >= 1000) {
              return (value / 1000).toFixed(1) + 'K';
            }
            return value;
          } : null
        }
      },
      yAxis: {
        type: isHorizontal ? 'category' : 'value',
        data: isHorizontal ? categories : null,
        name: isHorizontal ? (unified.xAxis?.label || unified.xLabel || '') : (unified.yAxis?.label || unified.yLabel || ''),
        nameTextStyle: {
          fontSize: 12,
          color: '#666'
        },
        axisLabel: {
          formatter: !isHorizontal ? function(value) {
            if (value >= 1000000) {
              return (value / 1000000).toFixed(1) + 'M';
            } else if (value >= 1000) {
              return (value / 1000).toFixed(1) + 'K';
            }
            return value;
          } : null
        }
      },
      series: [{
        name: unified.yAxis?.label || unified.yLabel || '数值',
        type: 'bar',
        data: values,
        itemStyle: {
          color: unified.color || '#5470c6'
        },
        emphasis: {
          focus: 'series'
        }
      }]
    };
  }

  /**
   * 构建折线图配置
   */
  buildLineChart(unified, data, imageSpec) {
    // 检查是否为多系列图表
    if (unified.seriesField || unified.groupBy) {
      return this.buildMultiSeriesLineChart(unified, data, imageSpec);
    }
    
    // 单系列图表
    const categories = data.map(item => this.getFieldValue(item, unified.xField));
    const values = data.map(item => this.getFieldValue(item, unified.yField));

    // 构建Y轴配置，包含标签
    const yAxisConfig = {
      type: 'value',
      name: unified.yAxis?.label || unified.yLabel || '',  // Y轴标题
      nameTextStyle: {
        fontSize: 12,
        color: '#666'
      },
      axisLabel: {
        formatter: function(value) {
          // 格式化大数字显示
          if (value >= 1000000) {
            return (value / 1000000).toFixed(1) + 'M';
          } else if (value >= 1000) {
            return (value / 1000).toFixed(1) + 'K';
          }
          return value;
        }
      }
    };

    // 构建series，包含名称用于图例
    const seriesConfig = {
      name: unified.yAxis?.label || unified.yLabel || '数值',  // 用于图例显示
      type: 'line',
      data: values,
      smooth: unified.smooth || false,
      itemStyle: {
        color: unified.color || '#5470c6'
      },
      lineStyle: {
        width: 2
      },
      emphasis: {
        focus: 'series'
      }
    };

    return {
      xAxis: {
        type: 'category',
        data: categories,
        name: unified.xAxis?.label || unified.xLabel || '',  // X轴标题
        nameTextStyle: {
          fontSize: 12,
          color: '#666'
        }
      },
      yAxis: yAxisConfig,
      series: [seriesConfig],
      legend: unified.showLegend !== false ? this.responsiveChart.buildResponsiveLegend({
        width: imageSpec?.width || 800,
        height: imageSpec?.height || 600,
        seriesCount: 1,
        seriesNames: [seriesConfig.name],
        showLegend: true,
        legendPosition: unified.legendPosition || 'bottom'
      }) : null,
      grid: this.responsiveChart.buildResponsiveGrid({
        width: imageSpec?.width || 800,
        height: imageSpec?.height || 600,
        hasLegend: unified.showLegend !== false,
        hasTitle: !!unified.title,
        seriesCount: 1,
        legendPosition: unified.legendPosition || 'bottom'
      })
    };
  }

  /**
   * 构建多系列柱状图配置
   */
  buildMultiSeriesBarChart(unified, data, imageSpec) {
    const seriesField = unified.seriesField || unified.groupBy;
    const isHorizontal = unified.type === 'bar';
    
    // 根据seriesField对数据进行分组
    const groupedData = {};
    const xCategories = new Set();
    
    data.forEach(item => {
      const seriesName = this.getFieldValue(item, seriesField) || '其他';
      const xValue = this.getFieldValue(item, unified.xField);
      const yValue = this.getFieldValue(item, unified.yField);
      
      if (!groupedData[seriesName]) {
        groupedData[seriesName] = {};
      }
      
      groupedData[seriesName][xValue] = yValue;
      xCategories.add(xValue);
    });
    
    // 将Set转换为有序数组
    const categories = Array.from(xCategories).sort();
    
    // 如果有太多系列，限制显示数量
    const maxSeries = 20;
    const allSeriesNames = Object.keys(groupedData);
    let selectedSeriesNames = allSeriesNames;
    
    if (allSeriesNames.length > maxSeries) {
      const seriesWithTotal = allSeriesNames.map(name => {
        const total = Object.values(groupedData[name]).reduce((sum, val) => sum + (val || 0), 0);
        return { name, total };
      });
      
      seriesWithTotal.sort((a, b) => b.total - a.total);
      selectedSeriesNames = seriesWithTotal.slice(0, maxSeries).map(item => item.name);
    }
    
    // 为每个系列构建series
    const series = selectedSeriesNames.map((seriesName, index) => {
      const seriesData = categories.map(category => {
        return groupedData[seriesName][category] || 0;  // 没有数据的点用0
      });
      
      // 使用默认颜色数组
      const colors = [
        '#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de',
        '#3ba272', '#fc8452', '#9a60b4', '#ea7ccc', '#ff9f7f'
      ];
      
      return {
        name: seriesName,
        type: 'bar',
        data: seriesData,
        itemStyle: {
          color: colors[index % colors.length]
        },
        emphasis: {
          focus: 'series'
        }
      };
    });
    
    // 构建Y轴配置
    const yAxisConfig = {
      type: 'value',
      name: unified.yAxis?.label || unified.yLabel || '',
      nameTextStyle: {
        fontSize: 12,
        color: '#666'
      },
      axisLabel: {
        formatter: function(value) {
          if (value >= 1000000) {
            return (value / 1000000).toFixed(1) + 'M';
          } else if (value >= 1000) {
            return (value / 1000).toFixed(1) + 'K';
          }
          return value;
        }
      }
    };
    
    const xAxisConfig = {
      type: 'category',
      data: categories,
      name: unified.xAxis?.label || unified.xLabel || '',
      nameTextStyle: {
        fontSize: 12,
        color: '#666'
      },
      axisTick: {
        alignWithLabel: true
      }
    };
    
    return isHorizontal ? {
      xAxis: yAxisConfig,
      yAxis: xAxisConfig,
      series: series
    } : {
      xAxis: xAxisConfig,
      yAxis: yAxisConfig,
      series: series
    };
  }

  /**
   * 构建多系列折线图配置
   */
  buildMultiSeriesLineChart(unified, data, imageSpec = {}) {
    const { width = 800, height = 600 } = imageSpec;
    const seriesField = unified.seriesField || unified.groupBy;

    // 根据seriesField对数据进行分组
    const groupedData = {};
    const xCategories = new Set();

    data.forEach((item, index) => {
      const seriesName = this.getFieldValue(item, seriesField) || '其他';
      const xValue = this.getFieldValue(item, unified.xField);
      const yValue = this.getFieldValue(item, unified.yField);

      if (!groupedData[seriesName]) {
        groupedData[seriesName] = {};
      }

      groupedData[seriesName][xValue] = yValue;
      xCategories.add(xValue);
    });

    // 将Set转换为有序数组（特别是日期）
    const categories = Array.from(xCategories).sort();

    // 如果有太多系列，限制显示数量以避免图表过于拥挤
    const maxSeries = 20;  // 最多显示20个系列
    const allSeriesNames = Object.keys(groupedData);
    let selectedSeriesNames = allSeriesNames;

    if (allSeriesNames.length > maxSeries) {
      // 选择数据量最大的前N个系列
      const seriesWithTotal = allSeriesNames.map(name => {
        const total = Object.values(groupedData[name]).reduce((sum, val) => sum + (val || 0), 0);
        return { name, total };
      });

      seriesWithTotal.sort((a, b) => b.total - a.total);
      selectedSeriesNames = seriesWithTotal.slice(0, maxSeries).map(item => item.name);

      logger.info(`Too many series (${allSeriesNames.length}), showing top ${maxSeries} by total value`);
    }

    // 为每个系列构建series
    const series = selectedSeriesNames.map((seriesName, index) => {
      const seriesData = categories.map(category => {
        return groupedData[seriesName][category] || null;  // 没有数据的点用null
      });

      // 使用默认颜色数组
      const colors = [
        '#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de',
        '#3ba272', '#fc8452', '#9a60b4', '#ea7ccc', '#ff9f7f'
      ];

      return {
        name: seriesName,
        type: 'line',
        data: seriesData,
        smooth: unified.smooth || false,
        itemStyle: {
          color: colors[index % colors.length]
        },
        lineStyle: {
          width: 2
        },
        emphasis: {
          focus: 'series'
        },
        connectNulls: false  // 不连接空数据点
      };
    });

    // 使用ResponsiveChart构建响应式配置
    const deviceType = this.responsiveChart.getDeviceType(width, height);

    // 构建Y轴配置，使用响应式标签
    const yAxisLabel = this.responsiveChart.buildResponsiveAxisLabel({
      width,
      categories: [],
      axisType: 'y'
    });

    const yAxisConfig = {
      type: 'value',
      name: unified.yAxis?.label || unified.yLabel || '',
      nameTextStyle: {
        fontSize: yAxisLabel.fontSize + 2,
        color: '#666'
      },
      axisLabel: yAxisLabel
    };

    // 构建X轴配置，使用响应式标签
    const xAxisLabel = this.responsiveChart.buildResponsiveAxisLabel({
      width,
      categories,
      axisType: 'x'
    });

    const xAxisConfig = {
      type: 'category',
      data: categories,
      name: unified.xAxis?.label || unified.xLabel || '',
      nameTextStyle: {
        fontSize: xAxisLabel.fontSize + 2,
        color: '#666'
      },
      axisTick: {
        alignWithLabel: true
      },
      axisLabel: {
        ...xAxisLabel,
        formatter: function(value) {
          // 如果是日期格式（YYYY-MM-DD），只显示MM-DD
          if (value && value.match(/^\d{4}-\d{2}-\d{2}/)) {
            return value.substring(5);  // 返回 MM-DD
          }
          return value;
        }
      }
    };

    // 使用ResponsiveChart构建响应式图例
    const responsiveLegend = this.responsiveChart.buildResponsiveLegend({
      width,
      height,
      seriesCount: selectedSeriesNames.length,
      seriesNames: selectedSeriesNames,
      showLegend: unified.showLegend !== false,
      legendPosition: unified.legendPosition || 'bottom' // 默认底部
    });

    // 使用ResponsiveChart构建响应式网格
    const responsiveGrid = this.responsiveChart.buildResponsiveGrid({
      width,
      height,
      hasLegend: !!responsiveLegend,
      hasTitle: !!unified.title,
      seriesCount: selectedSeriesNames.length,
      legendPosition: unified.legendPosition || 'bottom'
    });

    const echartsConfig = {
      xAxis: xAxisConfig,
      yAxis: yAxisConfig,
      series: series,
      legend: responsiveLegend,
      grid: responsiveGrid
    };

    console.log('=== COMPLETE ECHARTS CONFIG ===');
    console.log(JSON.stringify(echartsConfig, null, 2));

    logger.debug('Multi-series chart generated', {
      seriesCount: echartsConfig.series.length,
      seriesNames: echartsConfig.series.map(s => s.name),
      categories: categories.length,
      deviceType,
      width,
      height
    });

    return echartsConfig;
  }

  /**
   * 构建饼图配置
   */
  buildPieChart(unified, data, imageSpec) {
    // 支持多种字段名称：nameField/valueField 或 xField/yField
    const nameField = unified.nameField || unified.xField;
    const valueField = unified.valueField || unified.yField;

    if (!nameField || !valueField) {
      throw new RenderError('Pie chart requires nameField/valueField or xField/yField');
    }

    const seriesData = data.map(item => ({
      name: this.getFieldValue(item, nameField),
      value: this.getFieldValue(item, valueField)
    }));

    return {
      series: [{
        type: 'pie',
        radius: unified.type === 'doughnut' ? ['40%', '70%'] : '70%',
        data: seriesData,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        },
        label: {
          show: unified.showLabel !== false,
          formatter: '{b}: {c} ({d}%)'
        }
      }]
    };
  }

  /**
   * 构建散点图配置
   */
  buildScatterChart(unified, data, imageSpec) {
    const seriesData = data.map(item => [
      this.getFieldValue(item, unified.xField),
      this.getFieldValue(item, unified.yField)
    ]);

    return {
      xAxis: {
        type: 'value'
      },
      yAxis: {
        type: 'value'
      },
      series: [{
        type: 'scatter',
        data: seriesData,
        symbolSize: unified.symbolSize || 10,
        itemStyle: {
          color: unified.color || '#5470c6'
        }
      }]
    };
  }

  /**
   * 构建面积图配置
   */
  buildAreaChart(unified, data, imageSpec) {
    const categories = data.map(item => this.getFieldValue(item, unified.xField));
    const values = data.map(item => this.getFieldValue(item, unified.yField));

    return {
      xAxis: {
        type: 'category',
        data: categories
      },
      yAxis: {
        type: 'value'
      },
      series: [{
        type: 'line',
        data: values,
        areaStyle: {
          color: unified.color || '#5470c6',
          opacity: 0.6
        },
        smooth: unified.smooth || false,
        itemStyle: {
          color: unified.color || '#5470c6'
        }
      }]
    };
  }

  /**
   * 获取排序字段
   */
  getSortField(unified) {
    switch (unified.type) {
      case 'bar':
      case 'column':
      case 'line':
      case 'area':
        return unified.yField;
      case 'pie':
      case 'doughnut':
        return unified.valueField;
      default:
        return null;
    }
  }

  /**
   * 获取字段值
   */
  getFieldValue(item, field) {
    if (!field) return null;

    // 对于包含$符号的字段，直接使用方括号访问避免特殊字符问题
    if (field.includes('$')) {
      console.log('Field contains $:', field, 'value:', item[field]);
      return item[field];
    }

    // 支持嵌套字段访问，如 'user.name'
    if (field.includes('.')) {
      return field.split('.').reduce((obj, key) => obj?.[key], item);
    }

    // 普通字段直接访问
    return item[field];
  }

  /**
   * 聚合其他数据
   */
  aggregateOthers(otherData, unified) {
    const sortField = this.getSortField(unified);

    if (!sortField) {
      return { [unified.nameField || 'name']: '其他', [unified.valueField || 'value']: otherData.length };
    }

    const totalValue = otherData.reduce((sum, item) => {
      return sum + (this.getFieldValue(item, sortField) || 0);
    }, 0);

    return {
      [unified.nameField || 'name']: '其他',
      [unified.valueField || sortField]: totalValue
    };
  }
}

module.exports = UnifiedRenderer;