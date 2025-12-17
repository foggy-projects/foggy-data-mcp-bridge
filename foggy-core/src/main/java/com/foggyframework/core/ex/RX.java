package com.foggyframework.core.ex;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.text.MessageFormat;

@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RX<T> implements Serializable {
    private static final long serialVersionUID = -6542088717000951967L;

    public static final String SYSTEM_ERROR_MSG = "服务器发生异常，请联系管理员";

    /**
     * 成功的请求
     */
    public static final int SUCCESS = 200;

    public static final RX<String> DEFAULT_SUCCESS = new RX(SUCCESS, null, null, null);

    /**
     * 重复的请求
     */
    public static final int REPEAT = 201;
    /**
     * 通用用户端异常，当未指定错误码抛出时，如failA(String msg)，将使用此错误码A600
     */
    public static final String A_COMMON = ExDefined.SRC_TYPE_USER + ExDefined.COMMON_ERROR_CODE;
    /**
     * 通用系统端异常，当未指定错误码抛出时，如failB(String msg)，将使用此错误码B600
     */
    public static final String B_COMMON = ExDefined.SRC_TYPE_BUSINESS + ExDefined.COMMON_ERROR_CODE;

    public static final String STATE_COMMON = ExDefined.SRC_TYPE_BUSINESS + ExDefined.STATE_ERROR_CODE;
    /**
     * 通用第三方异常，当未指定错误码抛出时，如failC(String msg)，将使用此错误码C600
     */
    public static final String C_COMMON = ExDefined.SRC_TYPE_THIRD + ExDefined.COMMON_ERROR_CODE;

    public static final ExDefinedSupport OPER_ERROR = new ExDefinedSupport(1100, ExDefined.SRC_TYPE_USER, "操作失败");
    public static final ExDefinedSupport SYSTEM_ERROR = new ExDefinedSupport(1101, ExDefined.SRC_TYPE_BUSINESS, "服务器错误,请联系管理员");
    public static final ExDefinedSupport NOT_NULL_ERROR = new ExDefinedSupport(1102, ExDefined.SRC_TYPE_BUSINESS, "{0}不能为空");
    public static final ExDefinedSupport OBJ_REQUEST_ERROR = new ExDefinedSupport(1103, ExDefined.SRC_TYPE_BUSINESS, "{0}未填写");
    public static final ExDefinedSupport OUT_OF_LENGTH_ERROR = new ExDefinedSupport(1104, ExDefined.SRC_TYPE_BUSINESS, "{0}超出长度");
    public static final ExDefinedSupport NOT_EXISTS_ERROR = new ExDefinedSupport(1105, ExDefined.SRC_TYPE_BUSINESS, "{0}查询的数据不存在");
    public static final ExDefinedSupport RESOURCE_NOT_FOUND = new ExDefinedSupport(1106, ExDefined.SRC_TYPE_BUSINESS, "资源{0}不存在");
    public static final ExDefinedSupport REPEAT_ERROR = new ExDefinedSupport(REPEAT, ExDefined.SRC_TYPE_BUSINESS, "重复的操作");
    public static final ExDefinedSupport PERMISSION_ERROR = new ExDefinedSupport(1999, ExDefined.SRC_TYPE_USER, "权限错误{0}");

    /**
     * 200表示正常返回，当exCode有值时，一般它是去掉错误产生来源的数字编号。
     * 如未指定，当错误发生时，一般固定为600
     */
    int code;

    @Deprecated
    Integer exLevel;

    String userTip;
    /**
     * 参考阿里手册https://sdsh.yuque.com/lgg1k8/lieoog/arofnh，但不限制为5位。
     */
    @Deprecated
    String exCode;
    /**
     * 给调用者显示的消息
     */
    String msg;
    /**
     * 返回给调用者的结果
     * 同时注意，实现序列化接口Serializable，并指定serialVersionUID
     */
    T data;
    @ApiModelProperty("错误的对象")
    Object et;

    public static RX error(String msg) {
        return failB(msg);
    }

    public static RX.DefaultBuilder notFound() {
        return status(HttpStatus.NOT_FOUND);
    }

    public static RX.DefaultBuilder status(HttpStatusCode status) {
        Assert.notNull(status, "HttpStatusCode must not be null");
        return new RX.DefaultBuilder().code(status.value());
    }

    public static RX.DefaultBuilder internalServerError() {
        return status(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static class DefaultBuilder {

        //TODO
        private int code;

        private String msg;

        public DefaultBuilder code(int code) {
            this.code = code;
            return this;
        }

        public DefaultBuilder msg(String msg) {
            this.msg = msg;
            return this;
        }

        public <T> RX<T> build() {
            return new RX<>(code, null, msg, null);
        }
    }


    public static RX ok() {
        return success();
    }

    public static RX ok(Object item) {
        return success(item);
    }

    public Object getEt() {
        return et;
    }

    public void setEt(Object et) {
        this.et = et;
    }

    public Integer getExLevel() {
        return exLevel;
    }

    public void setExLevel(Integer exLevel) {
        this.exLevel = exLevel;
    }

    public String getUserTip() {
        return userTip;
    }

    public void setUserTip(String userTip) {
        this.userTip = userTip;
    }

    /**
     * 如果结果是错误的，重复抛出异常
     *
     * @return
     */
    public T _getItemIfSuccess() {
        if (_isSuccess()) {
            return data;
        }
        throw new ExRuntimeExceptionImpl(this.code, this.exCode, this.msg, this.userTip, this.data);
    }

    public T _getSuccessWithItem() {
        if (_isSuccessWithItem()) {
            return data;
        }
        if (this.code == SUCCESS) {
            throw new ExRuntimeExceptionImpl(RESOURCE_NOT_FOUND.code, RESOURCE_NOT_FOUND.exCode, this.msg, this.userTip, this.data);
        } else {
            throw new ExRuntimeExceptionImpl(this.code, this.exCode, this.msg, this.userTip, this.data);
        }

    }

    public T _getSuccessWithItem(String nullMessage) {
        if (_isSuccessWithItem()) {
            return data;
        }
        if (this.code == SUCCESS) {
            throw new ExRuntimeExceptionImpl(RESOURCE_NOT_FOUND.code, RESOURCE_NOT_FOUND.exCode, StringUtils.isEmpty(this.msg) ? nullMessage : this.msg, StringUtils.isEmpty(this.userTip) ? nullMessage : this.userTip, this.data);
        } else {
            throw new ExRuntimeExceptionImpl(this.code, this.exCode, StringUtils.isEmpty(this.msg) ? nullMessage : this.msg, StringUtils.isEmpty(this.userTip) ? nullMessage : this.userTip, this.data);
        }

    }

    public T _getSuccessWithItem(int nullCode, String nullMessage) {
        return _getSuccessWithItem(nullCode, ExDefined.SRC_TYPE_BUSINESS, nullMessage);
    }

    public T _getSuccessWithItem(int nullCode, String srcType, String nullMessage) {
        if (_isSuccessWithItem()) {
            return data;
        }

        throw new ExRuntimeExceptionImpl(nullCode, srcType + nullCode, StringUtils.isEmpty(this.msg) ? nullMessage : this.msg, StringUtils.isEmpty(this.userTip) ? nullMessage : this.userTip, this.data);

    }

    public boolean _checkSuccessWithItem() {
        return _checkSuccessWithItem(true);
    }

    public boolean _checkSuccessWithItem(boolean throwError) {
        return _checkSuccessWithItem(throwError, null);
    }

    public boolean _checkSuccessWithoutItem() {
        return _checkSuccessWithoutItem(true);
    }

    public boolean _checkSuccessWithoutItem(boolean throwError) {
        if (_isSuccess()) {
            return true;
        }

        if (throwError) {
            throw new ExRuntimeExceptionImpl(this.code, this.exCode, this.msg, this.userTip, this.data);
        }
        return false;
    }

    public boolean _checkSuccess(boolean throwError, int... excludeCodes) {
        if (_isSuccess()) {
            return true;
        }
        for (int excludeCode : excludeCodes) {
            if (code == excludeCode) {
                return true;
            }
        }

        return _checkSuccessWithoutItem(throwError);
    }

    public boolean _checkSuccessWithItem(boolean throwError, String nullMessage) {
        if (_isSuccessWithItem()) {
            return true;
        }
        if (this.code == SUCCESS || this.code == REPEAT) {
            if (throwError) {
                throw new ExRuntimeExceptionImpl(RESOURCE_NOT_FOUND.code, RESOURCE_NOT_FOUND.exCode, StringUtils.isEmpty(this.msg) ? nullMessage : this.msg, StringUtils.isEmpty(this.userTip) ? nullMessage : this.userTip, this.data);
            } else {
                return false;
            }
        } else {
            if (throwError) {
                throw new ExRuntimeExceptionImpl(this.code, this.exCode, StringUtils.isEmpty(this.msg) ? nullMessage : this.msg, StringUtils.isEmpty(this.userTip) ? nullMessage : this.userTip, this.data);
            }
            return false;
        }

    }

    public T _getWithItem() {
        if (code == SUCCESS || code == REPEAT) {
            return data;
        }
        if (this.code == SUCCESS) {
            throw new ExRuntimeExceptionImpl(RESOURCE_NOT_FOUND.code, RESOURCE_NOT_FOUND.exCode, this.msg, this.userTip, this.data);
        } else {
            throw new ExRuntimeExceptionImpl(this.code, this.exCode, this.msg, this.userTip, this.data);
        }

    }

    public RX() {

    }

    public RX(int code, String exCode, String msg, T data) {
        this.code = code;
        this.exCode = exCode;
        this.msg = msg;
        this.data = data;
    }

    public RX(int code, String exCode, String msg, String userTip, T data) {
        this.code = code;
        this.exCode = exCode;
        this.msg = msg;
        this.data = data;
        this.userTip = userTip;
    }

    /**
     * code为0且item非空
     *
     * @return
     */
    public final boolean _isSuccessWithItem() {
        return data != null && _isSuccess();
    }

    /**
     * code为0
     *
     * @return
     */
    public final boolean _isSuccess() {
        return code == SUCCESS || code == REPEAT;
    }

    @JsonIgnore
    public final boolean isOk() {
        return _isSuccess();
    }

    @JsonIgnore
    public final boolean isOk(boolean throwError) {
        return throwError ? _checkSuccessWithoutItem() : _isSuccess();
    }

    public static void notNull(Object obj, String msg, Object... formatArgs) {
        if (obj == null) {
            throw RX.throwB(formatArgs.length > 0 ? String.format(msg, formatArgs) : msg);
        }
    }

    public static void notNull(Object obj) {
        if (obj == null) {
            throw RX.throwB("值不得为空");
        }
    }

    /**
     * 返回用户类异常
     *
     * @param msg
     * @return
     */
    public static RX failA(String msg) {
        return new RX(ExDefined.COMMON_ERROR_CODE, A_COMMON, msg, null);
    }

    public static RX failAUserTip(String msg, String userTip) {
        return new RX(ExDefined.COMMON_ERROR_CODE, A_COMMON, msg, userTip, null);
    }

    public static RX failAUserTip(String userTip) {
        return new RX(ExDefined.COMMON_ERROR_CODE, A_COMMON, userTip, userTip, null);
    }

    public static RX failA(String msg, Object item) {
        return new RX(ExDefined.COMMON_ERROR_CODE, A_COMMON, msg, item);
    }

    public static RX failAUserTip(String msg, String userTip, Object item) {
        return new RX(ExDefined.COMMON_ERROR_CODE, A_COMMON, msg, userTip, item);
    }

    public static RX failFormatA(String msg, Object... args) {
        return new RX(ExDefined.COMMON_ERROR_CODE, A_COMMON, MessageFormat.format(msg, args), null);
    }

    /**
     * 返回系统内部业务异常
     *
     * @param msg
     * @return
     */
    public static RX failB(String msg) {
        return new RX(ExDefined.COMMON_ERROR_CODE, B_COMMON, msg, null);
    }

    public static RX failB(int code, String msg) {
        return new RX(code, ExDefined.SRC_TYPE_BUSINESS + code, msg, null);
    }

    public static RX failBUserTip(String msg, String userTip) {
        return new RX(ExDefined.COMMON_ERROR_CODE, B_COMMON, msg, userTip, null);
    }

    public static RX failBUserTip(String userTip) {
        return new RX(ExDefined.COMMON_ERROR_CODE, B_COMMON, userTip, userTip, null);
    }

    public static RX failB(String msg, Object item) {
        return new RX(ExDefined.COMMON_ERROR_CODE, B_COMMON, msg, item);
    }

    public static RX failBUserTip(String msg, String userTip, Object item) {
        return new RX(ExDefined.COMMON_ERROR_CODE, B_COMMON, msg, userTip, item);
    }

    public static RX failState(String id, int errorState, int hopeState, Object item) {
        return new RX(ExDefined.STATE_ERROR_CODE, STATE_COMMON, String.format("对象【%s】期望状态【%s】，但和实际状态【%s】不一致: ", id, hopeState, errorState), item);
    }

    public static RX failFormatB(String msg, Object... args) {
        return new RX(ExDefined.COMMON_ERROR_CODE, B_COMMON, MessageFormat.format(msg, args), null);
    }

//    public static R failB(String msg, Object item, Object... args) {
//        return new R(ExDefined.COMMON_ERROR_CODE, B_COMMON, MessageFormat.format(msg, args), item);
//    }

    /**
     * 返回第三方异常
     *
     * @param msg
     * @return
     */
    public static RX failC(String msg) {
        return new RX(ExDefined.COMMON_ERROR_CODE, C_COMMON, msg, null);
    }

    public static RX failCUserTip(String msg, String userTip) {
        return new RX(ExDefined.COMMON_ERROR_CODE, C_COMMON, msg, userTip, null);
    }

    public static RX failCUserTip(String userTip) {
        return new RX(ExDefined.COMMON_ERROR_CODE, C_COMMON, userTip, userTip, null);
    }

    public static RX failC(String msg, Object item) {
        return new RX(ExDefined.COMMON_ERROR_CODE, C_COMMON, msg, item);
    }

    public static RX failCUserTip(String msg, String userTip, Object item) {
        return new RX(ExDefined.COMMON_ERROR_CODE, C_COMMON, msg, userTip, item);
    }

    public static RX failFormatC(String msg, Object... args) {
        return new RX(ExDefined.COMMON_ERROR_CODE, C_COMMON, MessageFormat.format(msg, args), null);
    }

//    public static R failC(String msg, Object item, Object... args) {
//        return new R(ExDefined.COMMON_ERROR_CODE, C_COMMON, MessageFormat.format(msg, args), item);
//    }


    public static ExRuntimeExceptionImpl throwA(String msg) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, A_COMMON, msg, msg, null);
    }

    public static ExRuntimeExceptionImpl throwA(int code, String msg) {
        return new ExRuntimeExceptionImpl(code, ExDefined.SRC_TYPE_USER + code, msg, msg, null);
    }

    public static ExRuntimeExceptionImpl throwB(int code, String msg) {
        return new ExRuntimeExceptionImpl(code, ExDefined.SRC_TYPE_BUSINESS + code, msg, msg, null);
    }

    public static ExRuntimeExceptionImpl throwAUserTip(String msg, String userTip) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, A_COMMON, msg, userTip, null);
    }

    public static ExRuntimeExceptionImpl throwAUserTip(String userTip) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, A_COMMON, userTip, userTip, null);
    }

    public static ExRuntimeExceptionImpl throwA(String msg, Object item) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, A_COMMON, msg, msg, item);
    }

    public static ExRuntimeExceptionImpl throwAUserTip(String msg, String userTip, Object item) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, A_COMMON, msg, userTip, item);
    }

    public static ExRuntimeExceptionImpl throwA(String msg, Object item, Throwable cause) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, A_COMMON, msg, msg, item, cause);
    }

    public static ExRuntimeExceptionImpl throwA(int code, String msg, Object item, Throwable cause) {
        return new ExRuntimeExceptionImpl(code, ExDefined.SRC_TYPE_USER + code, msg, msg, item, cause);
    }

    public static ExRuntimeExceptionImpl throwAUserTip(String msg, String userTip, Object item, Throwable cause) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, A_COMMON, msg, msg, item, cause);
    }

    public static ExRuntimeExceptionImpl throwAUserTip(int code, String msg, String userTip, Object item, Throwable cause) {
        return new ExRuntimeExceptionImpl(code, ExDefined.SRC_TYPE_USER + code, msg, msg, item, cause);
    }

    public static ExRuntimeExceptionImpl throwB(String msg) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, B_COMMON, msg, msg, null);
    }

    public static ExRuntimeExceptionImpl throwBUserTip(String msg, String userTip) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, B_COMMON, msg, userTip, null);
    }

    public static ExRuntimeExceptionImpl throwBUserTip(String userTip) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, B_COMMON, userTip, userTip, null);
    }

    public static ExRuntimeExceptionImpl throwB(String msg, Object item) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, B_COMMON, msg, msg, item);
    }

    public static ExRuntimeExceptionImpl throwB(int code, String msg, Object item) {
        return new ExRuntimeExceptionImpl(code, ExDefined.SRC_TYPE_BUSINESS + code, msg, msg, item);
    }

    public static ExRuntimeExceptionImpl throwB(int code, String msg, Object item, Object et) {
        return new ExRuntimeExceptionImpl(code, ExDefined.SRC_TYPE_BUSINESS + code, msg, msg, item, et);
    }

    public static ExRuntimeExceptionImpl throwEtB(int code, String msg, Object et) {
        return new ExRuntimeExceptionImpl(code, ExDefined.SRC_TYPE_BUSINESS + code, msg, msg, null, et);
    }

    public static ExRuntimeExceptionImpl throwBUserTip(String msg, String userTip, Object item) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, B_COMMON, msg, userTip, item);
    }


    public static ExRuntimeExceptionImpl throwB(String msg, Object item, Throwable cause) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, B_COMMON, msg, msg, item, cause);
    }

    public static ExRuntimeExceptionImpl throwBUserTip(String msg, String userTip, Object item, Throwable cause) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, B_COMMON, msg, userTip, item, cause);
    }

    public static ExRuntimeExceptionImpl throwBUserTip(int code, String msg, String userTip, Object item, Throwable cause) {
        return new ExRuntimeExceptionImpl(code, ExDefined.SRC_TYPE_BUSINESS + code, msg, userTip, item, cause);
    }

    public static ExRuntimeExceptionImpl throwB(Throwable cause) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, B_COMMON, cause.getMessage(), SYSTEM_ERROR_MSG, null, cause);
    }

    public static ExRuntimeExceptionImpl throwBUserTip(String userTip, Throwable cause) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, B_COMMON, cause.getMessage(), userTip, null, cause);
    }

    public static ExRuntimeExceptionImpl throwC(Throwable cause) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, C_COMMON, cause.getMessage(), SYSTEM_ERROR_MSG, null, cause);
    }

    public static ExRuntimeExceptionImpl throwCUserTip(String userTip, Throwable cause) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, C_COMMON, cause.getMessage(), userTip, null, cause);
    }

    public static ExRuntimeExceptionImpl throwC(String msg) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, C_COMMON, msg, msg, null);
    }

    public static ExRuntimeExceptionImpl throwCUserTip(String msg, String userTip) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, C_COMMON, msg, userTip, null);
    }

    public static ExRuntimeExceptionImpl throwCUserTip(String userTip) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, C_COMMON, userTip, userTip, null);
    }

    public static ExRuntimeExceptionImpl throwC(String msg, Object item) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, C_COMMON, msg, msg, item);
    }

    public static ExRuntimeExceptionImpl throwCUserTip(String msg, String userTip, Object item) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, C_COMMON, msg, userTip, item);
    }

    public static ExRuntimeExceptionImpl throwCUserTip(int code, String msg, String userTip, Object item) {
        return new ExRuntimeExceptionImpl(code, ExDefined.SRC_TYPE_THIRD + code, msg, userTip, item);
    }

    public static ExRuntimeExceptionImpl throwC(String msg, Object item, Throwable cause) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, C_COMMON, msg, msg, item, cause);
    }

    public static ExRuntimeExceptionImpl throwCUserTip(String msg, String userTip, Object item, Throwable cause) {
        return new ExRuntimeExceptionImpl(ExDefined.COMMON_ERROR_CODE, C_COMMON, msg, userTip, item, cause);
    }

    public ExRuntimeExceptionImpl toException() {
        return new ExRuntimeExceptionImpl(code, exCode, msg, userTip, data, null);
    }

    public static <T> RX<T> success(T item) {
        return new RX(SUCCESS, null, null, item);
    }

    /**
     * 如果item为空，则传入的msg会被放到msg字段
     *
     * @param item
     * @param <T>
     * @return
     */
    public static <T> RX<T> successWithNullMsg(T item, String msg) {
        return new RX(SUCCESS, null, item == null ? msg : null, item);
    }

    public static <T> RX<T> successUserTip(T item, String userTip) {
        return new RX(SUCCESS, null, null, userTip, item);
    }

    public static <T> RX<T> repeat(T item) {
        return new RX(REPEAT, null, null, item);
    }

    public static RX repeat() {
        return new RX(REPEAT, null, null, null);
    }

    public static RX success() {
        return DEFAULT_SUCCESS;
    }

    public static RX successMessage(String message) {
        return new RX(SUCCESS, null, null, message, null);
    }

    public static RX successUserTip(String userTip) {
        return new RX(SUCCESS, null, null, userTip, null);
    }
//    public static ExRuntimeExceptionImpl throwExDefined(ExDefined def) {
//        return new ExRuntimeExceptionImpl(def);
//    }
//
//    public static ExRuntimeExceptionImpl throwExDefined(ExDefined def, Object item) {
//        return new ExRuntimeExceptionImpl(def, item);
//    }
//
//    public static ExRuntimeExceptionImpl throwExDefined(ExDefined def, Object item, Throwable cause) {
//        return new ExRuntimeExceptionImpl(def, item, cause);
//    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
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

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "R{" +
                "code=" + code +
                ", msg='" + msg + '\'' +
                ", item=" + data +
                '}';
    }

    public static void hasText(@Nullable String text, String message) {
        if (!StringUtils.hasText(text)) {
            throw RX.throwAUserTip(message);
        }
    }

    public static void notNull(@Nullable Object object, String message) {
        if (object == null) {
            throw RX.throwAUserTip(message);
        }
    }

    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw RX.throwAUserTip(message);
        }
    }
}
