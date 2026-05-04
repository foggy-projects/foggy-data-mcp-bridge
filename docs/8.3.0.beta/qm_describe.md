# FactSalesQueryModel - 销售明细查询

## 模型信息
- 表名: fact_sales
- 主键: sales_key
- 说明: 销售事实表查询模型，支持按日期、商品、客户、门店、渠道、促销等维度查询

## 维度字段
| 字段名 | 名称 | 类型 | 层级 | 说明 |
|--------|------|------|------|------|
| product$id | 商品(ID) | 数值/文本 | - | 商品代理键，自增整数 |
| product$caption | 商品(名称) | TEXT | - | 商品显示名称 |
| product$categoryId | 一级品类ID | 文本 | - | 商品一级分类编码 |
| product$categoryName | 一级品类名称 | 文本 | - | 商品一级分类名称，如电子产品、服装 |
| product$subCategoryId | 二级品类ID | 文本 | - | 商品二级分类编码 |
| product$subCategoryName | 二级品类名称 | 文本 | - | 商品二级分类名称，如手机、T恤 |
| product$brand | 品牌 | 文本 | - | 商品品牌名称 |
| product$unitPrice | 商品售价 | 金额 | - | 商品标准售价 |
| product$unitCost | 商品成本 | 金额 | - | 商品采购成本 |
| customer$id | 客户(ID) | 数值/文本 | - | 客户代理键，自增整数 |
| customer$caption | 客户(名称) | TEXT | - | 客户显示名称 |
| customer$customerType | 客户类型 | 文本 | - | 客户类型：个人/企业 |
| customer$gender | 性别 | 文本 | - | 客户性别：男/女 |
| customer$ageGroup | 年龄段 | 文本 | - | 客户年龄段：18-25/26-35/36-45/46+ |
| customer$province | 省份 | 文本 | - | 客户所在省份 |
| customer$city | 城市 | 文本 | - | 客户所在城市 |
| customer$memberLevel | 会员等级 | 文本 | - | 客户会员等级：普通/银卡/金卡/钻石 |
| store$id | 门店(ID) | 数值/文本 | - | 门店代理键，自增整数 |
| store$caption | 门店(名称) | TEXT | - | 门店显示名称 |
| store$storeType | 门店类型 | 文本 | - | 门店类型：直营店/加盟店/旗舰店 |
| store$province | 省份 | 文本 | - | 门店所在省份 |
| store$city | 城市 | 文本 | - | 门店所在城市 |
| channel$id | 渠道(ID) | 数值/文本 | - | 渠道代理键，自增整数 |
| channel$caption | 渠道(名称) | TEXT | - | 渠道显示名称 |
| channel$channelType | 渠道类型 | 文本 | - | 渠道类型：线上/线下 |
| channel$platform | 平台 | 文本 | - | 销售平台：淘宝/京东/线下门店 |
| promotion$id | 促销活动(ID) | 数值/文本 | - | 促销活动代理键，自增整数 |
| promotion$caption | 促销活动(名称) | TEXT | - | 促销活动显示名称 |
| promotion$promotionType | 促销类型 | 文本 | - | 促销类型：满减/折扣/赠品 |
| promotion$discountRate | 折扣率 | 文本 | - | 促销折扣率，如0.8表示8折 |

## 时间维度与字段 (Time Dimensions & Fields)
> **提示**: 在进行时间趋势分析、同环比或窗口函数(Window Functions)时，请优先使用 `business_date` 角色的字段。

| 字段名 | 名称 | 类别 | 时间角色 | 推荐用途 | 说明 |
|--------|------|------|----------|----------|------|
| salesDate$id | 销售日期(ID) | 维度ID | business_date | 核心业务时间轴，用于同环比和窗口函数 | 日期主键，格式yyyyMMdd，如20240101 |
| salesDate$caption | 销售日期(名称) | 维度名称 | - | - | 销售日期显示名称 |
| salesDate$year | 年 | 维度属性 | - | - | 销售发生的年份 |
| salesDate$quarter | 季度 | 维度属性 | - | - | 销售发生的季度（1-4） |
| salesDate$month | 月 | 维度属性 | - | - | 销售发生的月份（1-12） |
| salesDate$monthName | 月份名称 | 维度属性 | - | - | 销售月份中文名（一月至十二月） |
| salesDate$dayOfWeek | 周几 | 维度属性 | - | - | 销售发生在周几（1=周一） |
| salesDate$isWeekend | 是否周末 | 维度属性 | - | - | 销售是否发生在周末 |

## 属性字段
| 字段名 | 名称 | 类型 | 说明 |
|--------|------|------|------|
| orderId | 订单ID | 文本 |  |
| orderLineNo | 订单行号 | 文本 |  |
| orderStatus | 订单状态 | 文本 |  (字典:order_status) |
| paymentMethod | 支付方式 | 文本 |  (字典:payment_method) |

## 度量字段
| 字段名 | 名称 | 类型 | 聚合 | 说明 |
|--------|------|------|------|------|
| quantity | 销售数量 | 文本 | SUM |  |
| unitPrice | 单价 | 金额 | SUM |  |
| unitCost | 单位成本 | 金额 | SUM |  |
| discountAmount | 折扣金额 | 金额 | SUM |  |
| salesAmount | 销售金额 | 金额 | SUM |  |
| costAmount | 成本金额 | 金额 | SUM |  |
| profitAmount | 利润金额 | 金额 | SUM |  |
| taxAmount | 税额 | 金额 | SUM |  |
| taxAmount2 | 税额*2 | 金额 | SUM | 用于测试计算字段 |
| uniqueCustomers | 独立客户数 | 文本 | COUNT_DISTINCT | 去重客户数量（COUNT DISTINCT） |

## 预定义公式字段
> 这些是预聚合度量。直接在 `columns[]` 中引用字段名，不要在 `calculatedFields[]` 中重复定义。

| 字段名 | 名称 | 类型 | 说明 |
|--------|------|------|------|
| profitRate | 利润率(%) | NUMBER |  |
| salesRank | 品类销售排名 | INTEGER |  |
| ma7 | 7日移动平均 | NUMBER |  |

## 字典定义
| ID | 名称 | 取值 |
|----|------|------|
| order_status | 订单状态 | PENDING=待处理, CONFIRMED=已确认, PROCESSING=处理中, SHIPPED=已发货, DELIVERED=已送达, COMPLETED=已完成, CANCELLED=已取消, REFUNDED=已退款 |
| payment_method | 支付方式 | 1=现付, 2=到付, 3=货到付款 |

## 使用提示
- 维度用 `xxx$id`(查询/过滤), `xxx$caption`(展示), `xxx$property`(维度属性)
- 度量支持内联聚合: `sum(salesAmount) as total`
- 系统自动处理 groupBy，通常无需手动指定
- 层级维度支持 `selfAndDescendantsOf`(值及其所有下级) 和 `selfAndAncestorsOf`(值及其所有上级) 操作符
