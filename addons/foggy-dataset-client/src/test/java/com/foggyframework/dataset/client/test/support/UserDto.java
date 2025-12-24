package com.foggyframework.dataset.client.test.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户实体类 - 用于测试
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private Integer age;
    private BigDecimal balance;
    private Date createTime;
    private Boolean enabled;
}
