package com.foggyframework.fsscript.support;

import org.junit.jupiter.api.Test;

class MyTest {
    @Test
    void test(){
        System.err.println(tx(11));
    }

    int tx(int i){
        synchronized ("1"){
            if(i==0){
                return i;
            }
           return tx(i-1);
        }
    }
}
