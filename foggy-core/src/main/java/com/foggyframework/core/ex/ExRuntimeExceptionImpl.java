package com.foggyframework.core.ex;


public class ExRuntimeExceptionImpl extends RuntimeException implements ExRuntimeException {

    private static final long serialVersionUID = 3461859194220554289L;

    /**
     * 下面的Level是兼容旧系统的，一般我们认为A类异常，都是属于INFO_LEVEL
     * B/C类属于ERROR_LEVEL
     * 所以系统会记录B/C类异常的堆栈信息，A类不记录
     */
    public static final int DEBUG_LEVEL = 1;

    public static final int INFO_LEVEL = 2;

    /**
     * 警告，意料之中的的业务状态等，但程序可以继续运行。例如用户想修改某个参数，但修改的次数超过了限制。
     */
    public static final int WARN_LEVEL = 3;

    /**
     * 错误级别，意外的业务状态等，但程序可以忽略或自动纠正并处理，不影响业务继续
     */
    public static final int ERROR_LEVEL = 4;
    /**
     * 影响系统运行级别的异常，如数据库连接失败等。
     */
    public static final int FATL_LEVEL = 8;

    int code;

    String exCode;

    Object item;

    String userTip;

    Object et;

    @Override
    public Object getEt() {
        return et;
    }

    public void setEt(Object et) {
        this.et = et;
    }

    public String getUserTip() {
        return userTip;
    }

    public void setUserTip(String userTip) {
        this.userTip = userTip;
    }

    @Override
    public int getLevel() {
        if (exCode == null) {
            return WARN_LEVEL;
        }
        return exCode.startsWith("A") ? INFO_LEVEL : ERROR_LEVEL;
    }

    public ExRuntimeExceptionImpl(ExDefined defined) {
        this(defined.getCode(), defined.getExCode(), defined.getErrMsg(), defined.getUserTip(), null);
    }

    public ExRuntimeExceptionImpl(ExDefined defined, Object item) {
        this(defined.getCode(), defined.getExCode(), defined.getErrMsg(), defined.getUserTip(), item);
    }

    public ExRuntimeExceptionImpl(ExDefined defined, Object item, Throwable cause) {
        this(defined.getCode(), defined.getExCode(), defined.getErrMsg(), defined.getUserTip(), item, cause);
    }

    public ExRuntimeExceptionImpl(int code, String exCode, String errMsg, String userTip, Object item, Throwable cause) {
        super(errMsg, cause);
        this.code = code;
        this.exCode = exCode;
        this.item = item;
        this.userTip = userTip;
    }

    public ExRuntimeExceptionImpl(int code, String exCode, String errMsg, String userTip, Object item) {
        super(errMsg);
        this.code = code;
        this.exCode = exCode;
        this.item = item;
        this.userTip = userTip;
    }

    public ExRuntimeExceptionImpl(int code, String exCode, String errMsg, String userTip, Object item,Object et) {
        super(errMsg);
        this.code = code;
        this.exCode = exCode;
        this.item = item;
        this.userTip = userTip;
        this.et = et;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getExCode() {
        return exCode;
    }

    public void setExCode(String exCode) {
        this.exCode = exCode;
    }

    public Object getItem() {
        return item;
    }

    public void setItem(Object item) {
        this.item = item;
    }


}
