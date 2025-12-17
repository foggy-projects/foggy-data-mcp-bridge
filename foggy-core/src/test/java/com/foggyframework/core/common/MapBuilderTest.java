package com.foggyframework.core.common;

import lombok.Builder;
import lombok.Data;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MapBuilderTest {

    @Test
    void build() {
        Map mm = MapBuilder.builder().putObject(XX.builder().id("A").detail("D").build())
                .put("aa","aaa").put("bb","bbb").remove("bb").build();

        Assert.assertEquals(mm.size(),3);
        Assert.assertEquals(mm.get("aa"),"aaa");
        Assert.assertEquals(mm.get("id"),"A");
        Assert.assertEquals(mm.get("detail"),"D");
    }

    @Data
    @Builder
    public static class XX {
        String id;
        String detail;
    }

}