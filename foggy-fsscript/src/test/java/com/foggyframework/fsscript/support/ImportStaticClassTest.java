package com.foggyframework.fsscript.support;

import lombok.Data;

@Data
public class ImportStaticClassTest {

    public static final String test(String test) {
        return test;
    }

    String aa;

    String aaa;

    public ImportStaticClassTest(String aa, String aaa) {
        this.aa = aa;
        this.aaa = aaa;
    }

    public ImportStaticClassTest() {
    }
}
