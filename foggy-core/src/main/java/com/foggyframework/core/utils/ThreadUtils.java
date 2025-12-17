package com.foggyframework.core.utils;

import java.util.function.Function;

public class ThreadUtils {
    public static void run(Function run){
        new Thread(){
            @Override
            public void run() {
                run.apply(null);
            }
        }.start();
    }
}
