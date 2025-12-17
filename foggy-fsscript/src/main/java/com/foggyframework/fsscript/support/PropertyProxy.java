package com.foggyframework.fsscript.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PropertyProxy extends PropertyProxySupport {

    Object proxyObj;

    @Override
    protected Object getProxyObject() {
        return proxyObj;
    }
}
