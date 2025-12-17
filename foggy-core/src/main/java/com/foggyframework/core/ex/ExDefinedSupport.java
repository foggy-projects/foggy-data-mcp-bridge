package com.foggyframework.core.ex;

import java.text.MessageFormat;

public class ExDefinedSupport implements ExDefined {

    protected int code;

    protected String exCode;
    /**
     * 错误信息,给开发看
     */
    protected String errMsg;
    /**
     * 提示信息，给用户看
     */
    protected String userTip;

    public ExDefinedSupport(int code, String srcType, String msg) {
        this(code, srcType, msg, msg);
    }

    public ExDefinedSupport(int code, String srcType, String errMsg, String userTip) {
        this.code = code;
        this.exCode = srcType + code;
        this.errMsg = errMsg;
        this.userTip = userTip;
        if (userTip == null) {
            userTip = errMsg;
        }
    }

    @Override
    public String getErrMsg() {
        return errMsg;
    }

    public String formatErrMsg(Object... args) {
        return errMsg == null ? null : MessageFormat.format(errMsg, args);
    }

    public String formatUserTip(Object... args) {
        return userTip == null ? null : MessageFormat.format(userTip, args);
    }

    @Override
    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    @Override
    public String getUserTip() {
        return userTip;
    }

    @Override
    public void setUserTip(String userTip) {
        this.userTip = userTip;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getExCode() {
        return exCode;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public void setExCode(String exCode) {
        this.exCode = exCode;
    }

    public ExRuntimeExceptionImpl throwError() {
        return new ExRuntimeExceptionImpl(this);
    }

    public ExRuntimeExceptionImpl throwError(Object item) {
        return new ExRuntimeExceptionImpl(this, item);
    }

    public ExRuntimeExceptionImpl throwError(Object item, Throwable cause) {
        return new ExRuntimeExceptionImpl(this, item, cause);
    }

    public ExRuntimeExceptionImpl throwErrorWithFormatArgs(Object... args) {
        return new ExRuntimeExceptionImpl(this.getCode(), this.getExCode(), this.formatErrMsg(args), this.formatErrMsg(args), null, null);
    }

    public ExRuntimeExceptionImpl throwErrorWithItemAndFormatArgs(Object item, Object... args) {
        return new ExRuntimeExceptionImpl(this.getCode(), this.getExCode(), this.formatErrMsg(args), this.formatErrMsg(args), item, null);
    }

    public ExRuntimeExceptionImpl throwErrorWithFormatArgs(Object item, Throwable cause, Object... args) {
        return new ExRuntimeExceptionImpl(this.getCode(), this.getExCode(), this.formatErrMsg(args), this.formatErrMsg(args), item, cause);
    }

    public RX formatR(Object... args) {
        return new RX(code, exCode, formatUserTip(args), null);
    }

    //    /**
//     * 使用formatR1
//     * @param item
//     * @param args
//     * @param <T>
//     * @return
//     */
//    @Deprecated
//    public <T> RX<T> formatR(T item, Object... args) {
//        return new RX(code, exCode, formatUserTip(args), item);
//    }
    public <T> RX<T> formatR1(T item, Object... args) {
        return new RX(code, exCode, formatUserTip(args), item);
    }

    public boolean isError(Throwable t) {
        return t instanceof ExRuntimeException && ((ExRuntimeException) t).getCode() == this.code;
    }
}
