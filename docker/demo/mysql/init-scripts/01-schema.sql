-- ============================================
-- Foggy Dataset Model 测试数据库表结构
-- 文件: 01-schema.sql
-- 说明: 创建维度表、事实表、字典表
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==========================================
-- 1. 日期维度表
-- ==========================================
DROP TABLE IF EXISTS `dim_date`;
CREATE TABLE `dim_date` (
    `date_key`        INT NOT NULL COMMENT '日期键 YYYYMMDD',
    `full_date`       DATE NOT NULL COMMENT '完整日期',
    `year`            SMALLINT NOT NULL COMMENT '年',
    `quarter`         TINYINT NOT NULL COMMENT '季度 1-4',
    `month`           TINYINT NOT NULL COMMENT '月 1-12',
    `month_name`      VARCHAR(20) NOT NULL COMMENT '月份名称',
    `week_of_year`    TINYINT NOT NULL COMMENT '年度第几周',
    `day_of_month`    TINYINT NOT NULL COMMENT '月度第几天',
    `day_of_week`     TINYINT NOT NULL COMMENT '周几 1-7',
    `day_name`        VARCHAR(20) NOT NULL COMMENT '星期名称',
    `is_weekend`      TINYINT NOT NULL DEFAULT 0 COMMENT '是否周末',
    `is_holiday`      TINYINT NOT NULL DEFAULT 0 COMMENT '是否节假日',
    `fiscal_year`     SMALLINT COMMENT '财年',
    `fiscal_quarter`  TINYINT COMMENT '财季',
    PRIMARY KEY (`date_key`),
    INDEX `idx_full_date` (`full_date`),
    INDEX `idx_year_month` (`year`, `month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日期维度表';

-- ==========================================
-- 2. 商品维度表
-- ==========================================
DROP TABLE IF EXISTS `dim_product`;
CREATE TABLE `dim_product` (
    `product_key`       INT AUTO_INCREMENT COMMENT '商品代理键',
    `product_id`        VARCHAR(32) NOT NULL COMMENT '商品业务ID',
    `product_name`      VARCHAR(200) NOT NULL COMMENT '商品名称',
    `category_id`       VARCHAR(32) COMMENT '一级品类ID',
    `category_name`     VARCHAR(100) COMMENT '一级品类名称',
    `sub_category_id`   VARCHAR(32) COMMENT '二级品类ID',
    `sub_category_name` VARCHAR(100) COMMENT '二级品类名称',
    `brand`             VARCHAR(100) COMMENT '品牌',
    `supplier_id`       VARCHAR(32) COMMENT '供应商ID',
    `unit_price`        DECIMAL(18,2) NOT NULL COMMENT '售价',
    `unit_cost`         DECIMAL(18,2) COMMENT '成本',
    `status`            VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    `created_at`        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`product_key`),
    UNIQUE KEY `uk_product_id` (`product_id`),
    INDEX `idx_category` (`category_id`),
    INDEX `idx_brand` (`brand`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品维度表';

-- ==========================================
-- 3. 客户维度表
-- ==========================================
DROP TABLE IF EXISTS `dim_customer`;
CREATE TABLE `dim_customer` (
    `customer_key`    INT AUTO_INCREMENT COMMENT '客户代理键',
    `customer_id`     VARCHAR(32) NOT NULL COMMENT '客户业务ID',
    `customer_name`   VARCHAR(100) NOT NULL COMMENT '客户名称',
    `customer_type`   VARCHAR(20) COMMENT '客户类型: VIP/NORMAL/NEW',
    `gender`          VARCHAR(10) COMMENT '性别',
    `age_group`       VARCHAR(20) COMMENT '年龄段',
    `province`        VARCHAR(50) COMMENT '省份',
    `city`            VARCHAR(50) COMMENT '城市',
    `district`        VARCHAR(50) COMMENT '区县',
    `register_date`   DATE COMMENT '注册日期',
    `member_level`    VARCHAR(20) COMMENT '会员等级',
    `status`          VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`customer_key`),
    UNIQUE KEY `uk_customer_id` (`customer_id`),
    INDEX `idx_province_city` (`province`, `city`),
    INDEX `idx_member_level` (`member_level`),
    INDEX `idx_customer_type` (`customer_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户维度表';

-- ==========================================
-- 4. 门店维度表
-- ==========================================
DROP TABLE IF EXISTS `dim_store`;
CREATE TABLE `dim_store` (
    `store_key`       INT AUTO_INCREMENT COMMENT '门店代理键',
    `store_id`        VARCHAR(32) NOT NULL COMMENT '门店业务ID',
    `store_name`      VARCHAR(100) NOT NULL COMMENT '门店名称',
    `store_type`      VARCHAR(20) COMMENT '门店类型: 直营/加盟',
    `province`        VARCHAR(50) COMMENT '省份',
    `city`            VARCHAR(50) COMMENT '城市',
    `district`        VARCHAR(50) COMMENT '区县',
    `address`         VARCHAR(500) COMMENT '详细地址',
    `manager_name`    VARCHAR(50) COMMENT '店长',
    `open_date`       DATE COMMENT '开店日期',
    `area_sqm`        DECIMAL(10,2) COMMENT '面积(平方米)',
    `status`          VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`store_key`),
    UNIQUE KEY `uk_store_id` (`store_id`),
    INDEX `idx_province_city` (`province`, `city`),
    INDEX `idx_store_type` (`store_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店维度表';

-- ==========================================
-- 5. 销售渠道维度表
-- ==========================================
DROP TABLE IF EXISTS `dim_channel`;
CREATE TABLE `dim_channel` (
    `channel_key`     INT AUTO_INCREMENT COMMENT '渠道代理键',
    `channel_id`      VARCHAR(32) NOT NULL COMMENT '渠道业务ID',
    `channel_name`    VARCHAR(100) NOT NULL COMMENT '渠道名称',
    `channel_type`    VARCHAR(50) COMMENT '渠道类型: ONLINE/OFFLINE',
    `platform`        VARCHAR(50) COMMENT '平台: 天猫/京东/线下门店',
    `status`          VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`channel_key`),
    UNIQUE KEY `uk_channel_id` (`channel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售渠道维度表';

-- ==========================================
-- 6. 促销活动维度表
-- ==========================================
DROP TABLE IF EXISTS `dim_promotion`;
CREATE TABLE `dim_promotion` (
    `promotion_key`   INT AUTO_INCREMENT COMMENT '促销代理键',
    `promotion_id`    VARCHAR(32) NOT NULL COMMENT '促销业务ID',
    `promotion_name`  VARCHAR(200) NOT NULL COMMENT '促销名称',
    `promotion_type`  VARCHAR(50) COMMENT '促销类型: 满减/折扣/赠品',
    `discount_rate`   DECIMAL(5,2) COMMENT '折扣率',
    `start_date`      DATE COMMENT '开始日期',
    `end_date`        DATE COMMENT '结束日期',
    `status`          VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`promotion_key`),
    UNIQUE KEY `uk_promotion_id` (`promotion_id`),
    INDEX `idx_date_range` (`start_date`, `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='促销活动维度表';

-- ==========================================
-- 7. 订单事实表（订单头）
-- ==========================================
DROP TABLE IF EXISTS `fact_order`;
CREATE TABLE `fact_order` (
    `order_key`       BIGINT AUTO_INCREMENT COMMENT '订单代理键',
    `order_id`        VARCHAR(32) NOT NULL COMMENT '订单业务ID',
    `date_key`        INT NOT NULL COMMENT '下单日期维度',
    `customer_key`    INT COMMENT '客户维度',
    `store_key`       INT COMMENT '门店维度',
    `channel_key`     INT COMMENT '渠道维度',
    `promotion_key`   INT COMMENT '促销维度',
    `total_quantity`  INT NOT NULL COMMENT '订单总数量',
    `total_amount`    DECIMAL(18,2) NOT NULL COMMENT '订单总额',
    `discount_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '折扣金额',
    `freight_amount`  DECIMAL(18,2) DEFAULT 0 COMMENT '运费',
    `pay_amount`      DECIMAL(18,2) NOT NULL COMMENT '应付金额',
    `order_status`    VARCHAR(20) NOT NULL COMMENT '订单状态: PENDING/PAID/SHIPPED/COMPLETED/CANCELLED',
    `payment_status`  VARCHAR(20) COMMENT '支付状态: UNPAID/PAID/REFUNDED',
    `order_time`      DATETIME NOT NULL COMMENT '下单时间',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`order_key`),
    UNIQUE KEY `uk_order_id` (`order_id`),
    INDEX `idx_date_key` (`date_key`),
    INDEX `idx_customer_key` (`customer_key`),
    INDEX `idx_store_key` (`store_key`),
    INDEX `idx_channel_key` (`channel_key`),
    INDEX `idx_order_status` (`order_status`),
    INDEX `idx_order_time` (`order_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单事实表（订单头）';

-- ==========================================
-- 8. 销售事实表（订单明细）
-- ==========================================
DROP TABLE IF EXISTS `fact_sales`;
CREATE TABLE `fact_sales` (
    `sales_key`       BIGINT AUTO_INCREMENT COMMENT '销售代理键',
    `order_id`        VARCHAR(32) NOT NULL COMMENT '订单ID',
    `order_line_no`   INT NOT NULL COMMENT '订单行号',
    `date_key`        INT NOT NULL COMMENT '销售日期维度',
    `product_key`     INT NOT NULL COMMENT '商品维度',
    `customer_key`    INT COMMENT '客户维度',
    `store_key`       INT COMMENT '门店维度',
    `channel_key`     INT COMMENT '渠道维度',
    `promotion_key`   INT COMMENT '促销维度',
    `quantity`        INT NOT NULL COMMENT '销售数量',
    `unit_price`      DECIMAL(18,2) NOT NULL COMMENT '单价',
    `unit_cost`       DECIMAL(18,2) COMMENT '单位成本',
    `discount_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '折扣金额',
    `sales_amount`    DECIMAL(18,2) NOT NULL COMMENT '销售金额',
    `cost_amount`     DECIMAL(18,2) COMMENT '成本金额',
    `profit_amount`   DECIMAL(18,2) COMMENT '利润金额',
    `tax_amount`      DECIMAL(18,2) DEFAULT 0 COMMENT '税额',
    `order_status`    VARCHAR(20) COMMENT '订单状态',
    `payment_method`  VARCHAR(50) COMMENT '支付方式',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`sales_key`),
    UNIQUE KEY `uk_order_line` (`order_id`, `order_line_no`),
    INDEX `idx_date_key` (`date_key`),
    INDEX `idx_product_key` (`product_key`),
    INDEX `idx_customer_key` (`customer_key`),
    INDEX `idx_store_key` (`store_key`),
    INDEX `idx_channel_key` (`channel_key`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售事实表（订单明细）';

-- ==========================================
-- 9. 支付事实表
-- ==========================================
DROP TABLE IF EXISTS `fact_payment`;
CREATE TABLE `fact_payment` (
    `payment_key`     BIGINT AUTO_INCREMENT COMMENT '支付代理键',
    `payment_id`      VARCHAR(32) NOT NULL COMMENT '支付业务ID',
    `order_id`        VARCHAR(32) NOT NULL COMMENT '订单ID',
    `date_key`        INT NOT NULL COMMENT '支付日期维度',
    `customer_key`    INT COMMENT '客户维度',
    `pay_amount`      DECIMAL(18,2) NOT NULL COMMENT '支付金额',
    `pay_method`      VARCHAR(50) NOT NULL COMMENT '支付方式: ALIPAY/WECHAT/CARD/CASH',
    `pay_channel`     VARCHAR(50) COMMENT '支付渠道',
    `pay_status`      VARCHAR(20) NOT NULL COMMENT '支付状态: SUCCESS/FAILED/REFUNDING/REFUNDED',
    `pay_time`        DATETIME NOT NULL COMMENT '支付时间',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`payment_key`),
    UNIQUE KEY `uk_payment_id` (`payment_id`),
    INDEX `idx_order_id` (`order_id`),
    INDEX `idx_date_key` (`date_key`),
    INDEX `idx_customer_key` (`customer_key`),
    INDEX `idx_pay_status` (`pay_status`),
    INDEX `idx_pay_time` (`pay_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付事实表';

-- ==========================================
-- 10. 退货事实表
-- ==========================================
DROP TABLE IF EXISTS `fact_return`;
CREATE TABLE `fact_return` (
    `return_key`      BIGINT AUTO_INCREMENT COMMENT '退货代理键',
    `return_id`       VARCHAR(32) NOT NULL COMMENT '退货业务ID',
    `order_id`        VARCHAR(32) NOT NULL COMMENT '原订单ID',
    `order_line_no`   INT COMMENT '原订单行号（空表示整单退）',
    `date_key`        INT NOT NULL COMMENT '退货日期维度',
    `product_key`     INT COMMENT '商品维度',
    `customer_key`    INT COMMENT '客户维度',
    `store_key`       INT COMMENT '门店维度',
    `return_quantity` INT NOT NULL COMMENT '退货数量',
    `return_amount`   DECIMAL(18,2) NOT NULL COMMENT '退款金额',
    `return_reason`   VARCHAR(200) COMMENT '退货原因',
    `return_type`     VARCHAR(20) COMMENT '退货类型: REFUND_ONLY/RETURN_REFUND',
    `return_status`   VARCHAR(20) NOT NULL COMMENT '退货状态: PENDING/APPROVED/COMPLETED/REJECTED',
    `return_time`     DATETIME NOT NULL COMMENT '退货时间',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`return_key`),
    UNIQUE KEY `uk_return_id` (`return_id`),
    INDEX `idx_order_id` (`order_id`),
    INDEX `idx_date_key` (`date_key`),
    INDEX `idx_product_key` (`product_key`),
    INDEX `idx_customer_key` (`customer_key`),
    INDEX `idx_return_status` (`return_status`),
    INDEX `idx_return_time` (`return_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货事实表';

-- ==========================================
-- 11. 库存快照事实表
-- ==========================================
DROP TABLE IF EXISTS `fact_inventory_snapshot`;
CREATE TABLE `fact_inventory_snapshot` (
    `snapshot_key`        BIGINT AUTO_INCREMENT COMMENT '快照代理键',
    `date_key`            INT NOT NULL COMMENT '快照日期维度',
    `product_key`         INT NOT NULL COMMENT '商品维度',
    `store_key`           INT NOT NULL COMMENT '门店维度',
    `quantity_on_hand`    INT NOT NULL COMMENT '在库数量',
    `quantity_reserved`   INT DEFAULT 0 COMMENT '预留数量',
    `quantity_available`  INT NOT NULL COMMENT '可用数量',
    `unit_cost`           DECIMAL(18,2) COMMENT '单位成本',
    `inventory_value`     DECIMAL(18,2) COMMENT '库存价值',
    `created_at`          DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`snapshot_key`),
    UNIQUE KEY `uk_snapshot` (`date_key`, `product_key`, `store_key`),
    INDEX `idx_date_key` (`date_key`),
    INDEX `idx_product_key` (`product_key`),
    INDEX `idx_store_key` (`store_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存快照事实表';

-- ==========================================
-- 12. 地区字典表（层级结构）
-- ==========================================
DROP TABLE IF EXISTS `dict_region`;
CREATE TABLE `dict_region` (
    `region_id`       VARCHAR(32) NOT NULL COMMENT '地区ID',
    `region_name`     VARCHAR(100) NOT NULL COMMENT '地区名称',
    `parent_id`       VARCHAR(32) COMMENT '父级ID',
    `region_level`    TINYINT NOT NULL COMMENT '层级: 1省 2市 3区',
    `region_code`     VARCHAR(20) COMMENT '行政区划代码',
    `sort_order`      INT DEFAULT 0 COMMENT '排序',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`region_id`),
    INDEX `idx_parent_id` (`parent_id`),
    INDEX `idx_region_level` (`region_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地区字典表';

-- ==========================================
-- 13. 商品品类字典表（层级结构）
-- ==========================================
DROP TABLE IF EXISTS `dict_category`;
CREATE TABLE `dict_category` (
    `category_id`     VARCHAR(32) NOT NULL COMMENT '品类ID',
    `category_name`   VARCHAR(100) NOT NULL COMMENT '品类名称',
    `parent_id`       VARCHAR(32) COMMENT '父级ID',
    `category_level`  TINYINT NOT NULL COMMENT '层级: 1大类 2中类 3小类',
    `sort_order`      INT DEFAULT 0 COMMENT '排序',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`category_id`),
    INDEX `idx_parent_id` (`parent_id`),
    INDEX `idx_category_level` (`category_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品品类字典表';

-- ==========================================
-- 14. 通用状态字典表
-- ==========================================
DROP TABLE IF EXISTS `dict_status`;
CREATE TABLE `dict_status` (
    `status_type`     VARCHAR(50) NOT NULL COMMENT '状态类型',
    `status_code`     VARCHAR(50) NOT NULL COMMENT '状态编码',
    `status_name`     VARCHAR(100) NOT NULL COMMENT '状态名称',
    `sort_order`      INT DEFAULT 0 COMMENT '排序',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`status_type`, `status_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用状态字典表';

-- ==========================================
-- 父子维度测试表 (Parent-Child Dimension)
-- ==========================================

-- 15. 团队维度表（支持父子层级结构）
DROP TABLE IF EXISTS `dim_team`;
CREATE TABLE `dim_team` (
    `team_id`         VARCHAR(32) NOT NULL COMMENT '团队ID',
    `team_name`       VARCHAR(100) NOT NULL COMMENT '团队名称',
    `parent_id`       VARCHAR(32) COMMENT '父级团队ID',
    `team_level`      INT NOT NULL DEFAULT 1 COMMENT '层级',
    `manager_name`    VARCHAR(50) COMMENT '负责人',
    `status`          VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`team_id`),
    INDEX `idx_team_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队维度表（父子层级）';

-- 16. 团队闭包表（存储层级关系）
DROP TABLE IF EXISTS `team_closure`;
CREATE TABLE `team_closure` (
    `parent_id`       VARCHAR(32) NOT NULL COMMENT '祖先团队ID',
    `team_id`         VARCHAR(32) NOT NULL COMMENT '后代团队ID',
    `distance`        INT DEFAULT 0 COMMENT '层级距离',
    PRIMARY KEY (`parent_id`, `team_id`),
    INDEX `idx_closure_parent_id` (`parent_id`),
    INDEX `idx_closure_team_id` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队闭包表（层级关系）';

-- 17. 团队销售事实表
DROP TABLE IF EXISTS `fact_team_sales`;
CREATE TABLE `fact_team_sales` (
    `sales_id`        BIGINT AUTO_INCREMENT COMMENT '销售ID',
    `team_id`         VARCHAR(32) NOT NULL COMMENT '团队ID',
    `date_key`        INT NOT NULL COMMENT '日期维度',
    `sales_amount`    DECIMAL(18,2) NOT NULL COMMENT '销售金额',
    `sales_count`     INT NOT NULL DEFAULT 1 COMMENT '销售笔数',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`sales_id`),
    INDEX `idx_team_sales_team_id` (`team_id`),
    INDEX `idx_team_sales_date_key` (`date_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队销售事实表';

-- ==========================================
-- 嵌套维度测试表 (Nested Dimension / Snowflake Schema)
-- ==========================================

-- 18. 品类组维度表（三级维度）
DROP TABLE IF EXISTS `dim_category_group`;
CREATE TABLE `dim_category_group` (
    `group_key`       INT AUTO_INCREMENT COMMENT '品类组代理键',
    `group_id`        VARCHAR(32) NOT NULL COMMENT '品类组业务ID',
    `group_name`      VARCHAR(100) NOT NULL COMMENT '品类组名称',
    `group_type`      VARCHAR(50) COMMENT '品类组类型',
    `status`          VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`group_key`),
    UNIQUE KEY `uk_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品类组维度表（三级）';

-- 19. 品类维度表（二级维度，关联品类组）
DROP TABLE IF EXISTS `dim_category_nested`;
CREATE TABLE `dim_category_nested` (
    `category_key`    INT AUTO_INCREMENT COMMENT '品类代理键',
    `category_id`     VARCHAR(32) NOT NULL COMMENT '品类业务ID',
    `category_name`   VARCHAR(100) NOT NULL COMMENT '品类名称',
    `category_level`  INT DEFAULT 1 COMMENT '品类层级',
    `group_key`       INT COMMENT '外键关联品类组',
    `status`          VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`category_key`),
    UNIQUE KEY `uk_category_id` (`category_id`),
    INDEX `idx_category_group_key` (`group_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品类维度表（二级，关联品类组）';

-- 20. 嵌套产品维度表（一级维度，关联品类）
DROP TABLE IF EXISTS `dim_product_nested`;
CREATE TABLE `dim_product_nested` (
    `product_key`     INT AUTO_INCREMENT COMMENT '商品代理键',
    `product_id`      VARCHAR(32) NOT NULL COMMENT '商品业务ID',
    `product_name`    VARCHAR(200) NOT NULL COMMENT '商品名称',
    `brand`           VARCHAR(100) COMMENT '品牌',
    `category_key`    INT COMMENT '外键关联品类',
    `unit_price`      DECIMAL(18,2) NOT NULL COMMENT '售价',
    `status`          VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`product_key`),
    UNIQUE KEY `uk_product_id` (`product_id`),
    INDEX `idx_product_category_key` (`category_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='嵌套产品维度表（关联品类）';

-- 21. 区域维度表（二级维度）
DROP TABLE IF EXISTS `dim_region_nested`;
CREATE TABLE `dim_region_nested` (
    `region_key`      INT AUTO_INCREMENT COMMENT '区域代理键',
    `region_id`       VARCHAR(32) NOT NULL COMMENT '区域业务ID',
    `region_name`     VARCHAR(100) NOT NULL COMMENT '区域名称',
    `province`        VARCHAR(50) COMMENT '省份',
    `city`            VARCHAR(50) COMMENT '城市',
    `status`          VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`region_key`),
    UNIQUE KEY `uk_region_id` (`region_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区域维度表（二级）';

-- 22. 嵌套门店维度表（一级维度，关联区域）
DROP TABLE IF EXISTS `dim_store_nested`;
CREATE TABLE `dim_store_nested` (
    `store_key`       INT AUTO_INCREMENT COMMENT '门店代理键',
    `store_id`        VARCHAR(32) NOT NULL COMMENT '门店业务ID',
    `store_name`      VARCHAR(100) NOT NULL COMMENT '门店名称',
    `store_type`      VARCHAR(20) COMMENT '门店类型',
    `region_key`      INT COMMENT '外键关联区域',
    `status`          VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`store_key`),
    UNIQUE KEY `uk_store_id` (`store_id`),
    INDEX `idx_store_region_key` (`region_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='嵌套门店维度表（关联区域）';

-- 23. 嵌套维度销售事实表
DROP TABLE IF EXISTS `fact_sales_nested`;
CREATE TABLE `fact_sales_nested` (
    `sales_key`       BIGINT AUTO_INCREMENT COMMENT '销售代理键',
    `date_key`        INT NOT NULL COMMENT '日期维度',
    `product_key`     INT NOT NULL COMMENT '商品维度（关联嵌套产品）',
    `store_key`       INT COMMENT '门店维度（关联嵌套门店）',
    `quantity`        INT NOT NULL COMMENT '销售数量',
    `sales_amount`    DECIMAL(18,2) NOT NULL COMMENT '销售金额',
    `cost_amount`     DECIMAL(18,2) COMMENT '成本金额',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`sales_key`),
    INDEX `idx_nested_sales_date_key` (`date_key`),
    INDEX `idx_nested_sales_product_key` (`product_key`),
    INDEX `idx_nested_sales_store_key` (`store_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='嵌套维度销售事实表';

SET FOREIGN_KEY_CHECKS = 1;

-- 完成提示
SELECT 'Schema created successfully!' AS message;
