package com.foggyframework.core.common;

import lombok.Builder;
import lombok.Data;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class MapBuilderTest {

    @Test
    void build() {
        Map mm = MapBuilder.builder().putObject(XX.builder().id("A").detail("D").build())
                .put("aa","aaa").put("bb","bbb").remove("bb").build();

        Assertions.assertEquals(mm.size(),3);
        Assertions.assertEquals(mm.get("aa"),"aaa");
        Assertions.assertEquals(mm.get("id"),"A");
        Assertions.assertEquals(mm.get("detail"),"D");
    }

    @Data
    @Builder
    public static class XX {
        String id;
        String detail;
    }

}
