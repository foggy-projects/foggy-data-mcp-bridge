package com.foggyframework.core.utils;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

public class FileUtilsTest {
    @Test
    void test() {

        System.out.println(Pattern.matches("abc.*.jar", "abc123.jar"));
    }
}
