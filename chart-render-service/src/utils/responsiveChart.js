/**
 * 响应式图表配置工具
 * 根据画布尺寸、数据量、设备类型自动调整图表配置
 */

class ResponsiveChart {
  constructor() {
    this.mobileBreakpoint = 800;  // 移动端断点
    this.tabletBreakpoint = 1200; // 平板端断点
  }

  /**
   * 获取设备类型
   * @param {number} width 画布宽度
   * @param {number} height 画布高度
   * @returns {string} 设备类型：mobile/tablet/desktop
   */
  getDeviceType(width, height) {
    if (width <= this.mobileBreakpoint) return 'mobile';
    if (width <= this.tabletBreakpoint) return 'tablet';
    return 'desktop';
  }

  /**
   * 计算自适应字体大小
   * @param {number} baseSize 基础字体大小
   * @param {number} width 画布宽度
   * @param {string} deviceType 设备类型
   * @returns {number} 调整后的字体大小
   */
  getResponsiveFontSize(baseSize, width, deviceType) {
    const scaleFactor = Math.min(width / 1200, 2); // 最大放大2倍

    const deviceMultiplier = {
      mobile: 1.2,    // 移动端字体稍大
      tablet: 1.1,    // 平板端稍大
      desktop: 1.0    // 桌面端正常
    };

    return Math.round(baseSize * scaleFactor * deviceMultiplier[deviceType]);
  }

  /**
   * 构建响应式图例配置
   * @param {Object} params 配置参数
   * @param {number} params.width 画布宽度
   * @param {number} params.height 画布高度
   * @param {number} params.seriesCount 系列数量
   * @param {Array} params.seriesNames 系列名称
   * @param {boolean} params.showLegend 是否显示图例
   * @param {string} params.legendPosition 图例位置：'bottom', 'top', 'left', 'right'
   * @returns {Object|null} 图例配置
   */
  buildResponsiveLegend({ width, height, seriesCount, seriesNames = [], showLegend = true, legendPosition = 'bottom' }) {
    if (!showLegend || seriesCount <= 1) return null;

    const deviceType = this.getDeviceType(width, height);
    // 增大基础字体大小
    const baseFontSize = this.getResponsiveFontSize(16, width, deviceType); // 从12增加到16

    // 计算图例项的平均长度
    const avgNameLength = seriesNames.length > 0
      ? seriesNames.reduce((sum, name) => sum + String(name).length, 0) / seriesNames.length
      : 10;

    // 估算单个图例项宽度（字体大小 * 字符数 + 图标宽度 + 间距）
    const estimatedItemWidth = baseFontSize * avgNameLength * 0.6 + 50; // 增加间距

    // 计算每行可容纳的图例项数量
    const availableWidth = width * 0.9; // 留10%边距
    const itemsPerRow = Math.max(1, Math.floor(availableWidth / estimatedItemWidth));

    // 判断是否需要分行显示
    const needMultipleRows = seriesCount > itemsPerRow;

    // 根据指定位置决定布局，默认优先使用底部布局
    const useVerticalLayout = legendPosition === 'left' || legendPosition === 'right' ||
                              (deviceType === 'mobile' && seriesCount > 3 && legendPosition !== 'bottom');

    if (useVerticalLayout) {
      const position = legendPosition === 'left' ? 'left' : 'right';
      return {
        show: true,
        type: needMultipleRows ? 'scroll' : 'plain',
        orient: 'vertical',
        [position]: 15, // 增加边距
        top: 'center',
        textStyle: {
          fontSize: baseFontSize,
          color: '#333'
        },
        itemWidth: Math.min(25, baseFontSize + 6), // 增大图标
        itemHeight: Math.min(18, baseFontSize), // 增大图标
        itemGap: Math.max(8, baseFontSize / 2), // 增大间距
        pageButtonItemGap: 8,
        pageButtonGap: 12,
        pageButtonPosition: 'end',
        pageFormatter: '{current}/{total}',
        pageTextStyle: {
          fontSize: baseFontSize - 2,
          color: '#666'
        }
      };
    }

    // 水平布局配置（底部或顶部）
    const verticalPosition = legendPosition === 'top' ? 'top' : 'bottom';
    const config = {
      show: true,
      orient: 'horizontal',
      left: 'center',
      [verticalPosition]: verticalPosition === 'top' ? 15 : 20, // 增加边距
      textStyle: {
        fontSize: baseFontSize,
        color: '#333'
      },
      itemWidth: Math.min(30, baseFontSize + 8), // 增大图标
      itemHeight: Math.min(18, baseFontSize), // 增大图标
      itemGap: Math.max(15, baseFontSize) // 增大间距
    };

    // 优化多行处理：使用自动换行而非滚动，确保截图时所有图例项都可见
    if (needMultipleRows) {
      // 计算图例区域高度，确保有足够空间显示所有项
      const rowHeight = baseFontSize + 10; // 每行高度
      const estimatedRows = Math.ceil(seriesCount / itemsPerRow);
      const totalLegendHeight = estimatedRows * rowHeight;

      // 优先使用自动换行，只有在极端情况下才使用滚动
      // 只有当系列数量超过20个时才考虑滚动
      const useScroll = seriesCount > 20;

      if (useScroll) {
        config.type = 'scroll';
        config.pageButtonItemGap = 8;
        config.pageButtonGap = 12;
        config.pageButtonPosition = 'end';
        config.pageFormatter = '{current}/{total}';
        config.pageTextStyle = {
          fontSize: baseFontSize - 2,
          color: '#666'
        };
      } else {
        // 使用自动换行布局，增加图例区域高度
        config.itemGap = Math.max(10, baseFontSize * 0.8); // 稍微减小水平间距
        config.selectedMode = false; // 禁用点击切换，避免意外隐藏

        // 根据图例位置调整边距，为多行图例留出足够空间
        if (verticalPosition === 'bottom') {
          config.bottom = Math.max(20, totalLegendHeight + 10);
        } else {
          config.top = Math.max(20, totalLegendHeight + 10);
        }
      }
    }

    return config;
  }

  /**
   * 构建响应式网格配置
   * @param {Object} params 配置参数
   * @param {number} params.width 画布宽度
   * @param {number} params.height 画布高度
   * @param {boolean} params.hasLegend 是否有图例
   * @param {boolean} params.hasTitle 是否有标题
   * @param {number} params.seriesCount 系列数量（用于计算图例高度）
   * @param {string} params.legendPosition 图例位置
   * @returns {Object} 网格配置
   */
  buildResponsiveGrid({ width, height, hasLegend, hasTitle, seriesCount = 1, legendPosition = 'bottom' }) {
    const deviceType = this.getDeviceType(width, height);

    // 基础间距
    const baseMargin = {
      mobile: { top: 15, right: 5, bottom: 15, left: 12 },
      tablet: { top: 12, right: 8, bottom: 12, left: 10 },
      desktop: { top: 10, right: 10, bottom: 10, left: 10 }
    };

    let margin = { ...baseMargin[deviceType] };

    // 根据内容调整间距
    if (hasTitle) {
      margin.top += deviceType === 'mobile' ? 8 : 5;
    }

    if (hasLegend) {
      // 计算图例所需空间
      const baseFontSize = this.getResponsiveFontSize(16, width, deviceType);
      const estimatedItemWidth = baseFontSize * 6 + 50; // 估算图例项宽度
      const availableWidth = width * 0.9;
      const itemsPerRow = Math.max(1, Math.floor(availableWidth / estimatedItemWidth));
      const needMultipleRows = seriesCount > itemsPerRow;

      if (needMultipleRows && !(deviceType === 'mobile' && seriesCount > 8)) {
        // 多行图例需要更多空间
        const estimatedRows = Math.ceil(seriesCount / itemsPerRow);
        const extraSpace = (estimatedRows - 1) * 4; // 每额外行增加4%空间

        if (legendPosition === 'top') {
          margin.top += Math.min(15, 8 + extraSpace);
        } else {
          margin.bottom += Math.min(20, 10 + extraSpace);
        }
      } else {
        // 单行图例或滚动图例
        if (legendPosition === 'top') {
          margin.top += deviceType === 'mobile' ? 8 : 5;
        } else {
          margin.bottom += deviceType === 'mobile' ? 15 : 10;
        }
      }
    }

    // 转换为百分比
    return {
      left: `${margin.left}%`,
      right: `${margin.right}%`,
      top: `${margin.top}%`,
      bottom: `${margin.bottom}%`,
      containLabel: true
    };
  }

  /**
   * 构建响应式标题配置
   * @param {Object} params 配置参数
   * @param {string} params.title 标题文本
   * @param {number} params.width 画布宽度
   * @param {number} params.height 画布高度
   * @returns {Object|null} 标题配置
   */
  buildResponsiveTitle({ title, width, height }) {
    if (!title) return null;

    const deviceType = this.getDeviceType(width, height);
    const baseFontSize = this.getResponsiveFontSize(16, width, deviceType);
    const subFontSize = Math.round(baseFontSize * 0.75);

    return {
      text: title,
      left: 'center',
      top: deviceType === 'mobile' ? 15 : 20,
      textStyle: {
        fontSize: baseFontSize,
        fontWeight: 'bold',
        color: '#333'
      }
    };
  }

  /**
   * 构建响应式轴标签配置
   * @param {Object} params 配置参数
   * @param {number} params.width 画布宽度
   * @param {Array} params.categories 分类数据
   * @param {string} params.axisType x轴或y轴
   * @returns {Object} 轴标签配置
   */
  buildResponsiveAxisLabel({ width, categories = [], axisType = 'x' }) {
    const deviceType = this.getDeviceType(width);
    // 增大轴标签基础字体大小
    const baseFontSize = this.getResponsiveFontSize(14, width, deviceType); // 从10增加到14

    if (axisType === 'x') {
      // X轴标签处理
      const maxLabelLength = categories.length > 0
        ? Math.max(...categories.map(cat => String(cat).length))
        : 0;

      const shouldRotate = deviceType === 'mobile' && (
        maxLabelLength > 4 || categories.length > 6
      );

      return {
        fontSize: baseFontSize,
        color: '#666',
        rotate: shouldRotate ? 45 : 0,
        interval: deviceType === 'mobile' && categories.length > 8 ? 'auto' : 0
      };
    } else {
      // Y轴标签处理
      return {
        fontSize: baseFontSize,
        color: '#666',
        formatter: function(value) {
          if (value >= 1000000) {
            return (value / 1000000).toFixed(1) + 'M';
          } else if (value >= 1000) {
            return (value / 1000).toFixed(1) + 'K';
          }
          return value;
        }
      };
    }
  }

  /**
   * 构建响应式tooltip配置
   * @param {Object} params 配置参数
   * @param {number} params.width 画布宽度
   * @returns {Object} tooltip配置
   */
  buildResponsiveTooltip({ width }) {
    const deviceType = this.getDeviceType(width);
    const baseFontSize = this.getResponsiveFontSize(12, width, deviceType);

    return {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#ccc',
      borderWidth: 1,
      textStyle: {
        fontSize: baseFontSize,
        color: '#333'
      },
      padding: deviceType === 'mobile' ? [8, 10] : [10, 15],
      confine: true // 确保tooltip不会超出容器边界
    };
  }
}

module.exports = ResponsiveChart;