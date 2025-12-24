package com.foggyframework.dataset.client.test.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单表单 - 用于 OnDuplicate 测试
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderForm {
    private Long id;
    private String orderNo;
    private BigDecimal amount;
    private Integer status;
    private Long version;
}
