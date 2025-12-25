package com.foggyframework.fsscript.client.test.support;

import org.springframework.stereotype.Component;

@Component
public class FsscriptTestService {

    public synchronized int intAA(int d) {
        return d + 1;
    }
}
