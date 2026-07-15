-- ============================================
-- Foggy Dataset Model 预聚合测试表结构
-- 文件: 10-preagg-schema.sql
-- 说明: 创建预聚合表，用于测试预聚合功能
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==========================================
-- 1. 销售日汇总预聚合表（按日期+商品）
-- ==========================================
DROP TABLE IF EXISTS `preagg_daily_product_sales`;
CREATE TABLE `preagg_daily_product_sales` (
    `date_key`            INT NOT NULL COMMENT '日期维度键',
    `product_key`         INT NOT NULL COMMENT '商品维度键',
    `full_date`           DATE COMMENT '业务日期',
    `year`                INT COMMENT '年',
    `quarter`             INT COMMENT '季度',
    `month`               INT COMMENT '月',
    `month_name`          VARCHAR(20) COMMENT '月份名称',
    `product_id`          VARCHAR(50) COMMENT '商品业务ID',
    `product_name`        VARCHAR(200) COMMENT '商品名称',
    `category_id`         VARCHAR(50) COMMENT '品类ID',
    `category_name`       VARCHAR(100) COMMENT '商品品类名称',
    `brand`               VARCHAR(100) COMMENT '商品品牌',
    `quantity_sum`        BIGINT NOT NULL DEFAULT 0 COMMENT '销售数量(SUM)',
    `sales_amount_sum`    DECIMAL(20,4) NOT NULL DEFAULT 0 COMMENT '销售金额(SUM)',
    `sales_amount_formula_yuan_sum` DECIMAL(20,4) NOT NULL DEFAULT 0 COMMENT '销售金额换算(SUM)',
    `cost_amount_sum`     DECIMAL(20,4) DEFAULT 0 COMMENT '成本金额(SUM)',
    `profit_amount_sum`   DECIMAL(20,4) DEFAULT 0 COMMENT '利润金额(SUM)',
    `order_count`         BIGINT NOT NULL DEFAULT 0 COMMENT '订单数(COUNT)',
    `_preagg_row_count`   BIGINT DEFAULT 1 COMMENT '聚合行数',
    `_preagg_created_at`  TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `_preagg_updated_at`  TIMESTAMP NULL COMMENT '更新时间',
    PRIMARY KEY (`date_key`, `product_key`),
    INDEX `idx_preagg_daily_product_date` (`date_key`),
    INDEX `idx_preagg_daily_product_product` (`product_key`),
    INDEX `idx_preagg_daily_product_category` (`category_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售日汇总预聚合表（按日期+商品）';

-- ==========================================
-- 2. 销售月汇总预聚合表（按月+品类）
-- ==========================================
DROP TABLE IF EXISTS `preagg_monthly_category_sales`;
CREATE TABLE `preagg_monthly_category_sales` (
    `year_month`          INT NOT NULL COMMENT '年月 YYYYMM',
    `category_name`       VARCHAR(100) NOT NULL COMMENT '品类名称',
    `quantity_sum`        BIGINT NOT NULL DEFAULT 0 COMMENT '销售数量(SUM)',
    `sales_amount_sum`    DECIMAL(20,4) NOT NULL DEFAULT 0 COMMENT '销售金额(SUM)',
    `cost_amount_sum`     DECIMAL(20,4) DEFAULT 0 COMMENT '成本金额(SUM)',
    `profit_amount_sum`   DECIMAL(20,4) DEFAULT 0 COMMENT '利润金额(SUM)',
    `order_count`         BIGINT NOT NULL DEFAULT 0 COMMENT '订单数(COUNT)',
    `_preagg_row_count`   BIGINT DEFAULT 1 COMMENT '聚合行数',
    `_preagg_created_at`  TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `_preagg_updated_at`  TIMESTAMP NULL COMMENT '更新时间',
    PRIMARY KEY (`year_month`, `category_name`),
    INDEX `idx_preagg_monthly_category_ym` (`year_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售月汇总预聚合表（按月+品类）';

-- ==========================================
-- 3. 销售日汇总预聚合表（按日期+客户+渠道）
-- ==========================================
DROP TABLE IF EXISTS `preagg_daily_customer_channel_sales`;
CREATE TABLE `preagg_daily_customer_channel_sales` (
    `date_key`            INT NOT NULL COMMENT '日期维度键',
    `customer_key`        INT NOT NULL COMMENT '客户维度键',
    `channel_key`         INT NOT NULL COMMENT '渠道维度键',
    `province`            VARCHAR(50) COMMENT '客户省份',
    `city`                VARCHAR(50) COMMENT '客户城市',
    `channel_type`        VARCHAR(50) COMMENT '渠道类型',
    `quantity_sum`        BIGINT NOT NULL DEFAULT 0 COMMENT '销售数量(SUM)',
    `sales_amount_sum`    DECIMAL(20,4) NOT NULL DEFAULT 0 COMMENT '销售金额(SUM)',
    `order_count`         BIGINT NOT NULL DEFAULT 0 COMMENT '订单数(COUNT)',
    `_preagg_row_count`   BIGINT DEFAULT 1 COMMENT '聚合行数',
    `_preagg_created_at`  TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `_preagg_updated_at`  TIMESTAMP NULL COMMENT '更新时间',
    PRIMARY KEY (`date_key`, `customer_key`, `channel_key`),
    INDEX `idx_preagg_daily_cust_chan_date` (`date_key`),
    INDEX `idx_preagg_daily_cust_chan_customer` (`customer_key`),
    INDEX `idx_preagg_daily_cust_chan_channel` (`channel_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售日汇总预聚合表（按日期+客户+渠道）';

-- ==========================================
-- 4. 退货日汇总预聚合表（用于测试多表JOIN场景）
-- ==========================================
DROP TABLE IF EXISTS `preagg_daily_return`;
CREATE TABLE `preagg_daily_return` (
    `date_key`            INT NOT NULL COMMENT '日期维度键',
    `product_key`         INT NOT NULL COMMENT '商品维度键',
    `full_date`           DATE COMMENT '退货日期',
    `product_name`        VARCHAR(200) COMMENT '商品名称',
    `category_name`       VARCHAR(100) COMMENT '商品品类名称',
    `return_quantity_sum` BIGINT NOT NULL DEFAULT 0 COMMENT '退货数量(SUM)',
    `return_amount_sum`   DECIMAL(20,4) NOT NULL DEFAULT 0 COMMENT '退款金额(SUM)',
    `return_count`        BIGINT NOT NULL DEFAULT 0 COMMENT '退货单数(COUNT)',
    `_preagg_row_count`   BIGINT DEFAULT 1 COMMENT '聚合行数',
    `_preagg_created_at`  TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `_preagg_updated_at`  TIMESTAMP NULL COMMENT '更新时间',
    PRIMARY KEY (`date_key`, `product_key`),
    INDEX `idx_preagg_daily_return_date` (`date_key`),
    INDEX `idx_preagg_daily_return_product` (`product_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货日汇总预聚合表';

-- ==========================================
-- 5. 预聚合水位线记录表（用于混合查询）
-- ==========================================
DROP TABLE IF EXISTS `preagg_watermark`;
CREATE TABLE `preagg_watermark` (
    `preagg_name`         VARCHAR(100) NOT NULL COMMENT '预聚合名称',
    `model_name`          VARCHAR(100) NOT NULL COMMENT '模型名称',
    `last_refresh_time`   TIMESTAMP NULL COMMENT '最后刷新时间',
    `watermark_value`     VARCHAR(100) COMMENT '水位线值',
    `watermark_type`      VARCHAR(20) DEFAULT 'DATE' COMMENT '水位线类型',
    `refresh_strategy`    VARCHAR(20) DEFAULT 'FULL' COMMENT '刷新策略',
    `row_count`           BIGINT DEFAULT 0 COMMENT '行数',
    `created_at`          TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`preagg_name`, `model_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预聚合水位线记录表';

SET FOREIGN_KEY_CHECKS = 1;

SELECT 'Pre-aggregation schema created successfully!' AS message;
