package com.foggyframework.core.filter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SimpleFoggyFilterCtx<T, R> {
    /**
     * 参数
     */
    protected T args;

    /**
     * 结果
     */
   protected R result;
}
