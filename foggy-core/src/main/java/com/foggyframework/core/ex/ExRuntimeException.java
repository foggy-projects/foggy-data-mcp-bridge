package com.foggyframework.core.ex;

public interface ExRuntimeException {


    int getLevel();

    int getCode();

    String getExCode();

    String getMessage();
    String getUserTip();

    Object getItem();
    Object getEt();

    default RX toR(){
        return new RX(getCode(),getExCode(),getUserTip(),getItem());
    }
}
