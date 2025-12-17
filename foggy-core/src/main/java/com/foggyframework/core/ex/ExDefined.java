package com.foggyframework.core.ex;

/**
 * 错误码参考自阿里手册
 * https://sdsh.yuque.com/lgg1k8/lieoog/arofnh
 */

public interface ExDefined {

    /**
     * A 表示错误来源于用户，比如参数错误，用户安装版本过低，用户支付
     * 超时等问题；
     */
     String SRC_TYPE_USER = "A";
    /**
     * B 表示错误来源于当前系统，往往是业务逻辑出错，或程序健壮性差等问题；
     */
     String SRC_TYPE_BUSINESS = "B";
    /**
     * C 表示错误来源于第三方服务，比如 CDN 服务出错，消息投递超时等问题；
     */
     String SRC_TYPE_THIRD = "C";
    /**
     * 系统内部错误
     */
    int INNER_ERROR_CODE = 500;
    /**
     * 通用错误
     */
    int COMMON_ERROR_CODE = 600;

    int STATE_ERROR_CODE = 601;

    /**
     * 返回错误数字码，注意，不限制4位！10000以下为系统预留，非特殊情况不得使用，业务模块应当使用分配的错误编码
     *
     * @return
     */
    int getCode();

    /**
     * 见https://sdsh.yuque.com/lgg1k8/lieoog/arofnh
     * A 开头表示来源于用户的错误
     * B 开头表示当前系统
     * C 开头表示第三方服务
     *
     * @return
     */
    String getExCode();

     String getErrMsg();

     void setErrMsg(String errMsg);

    /**
     * 返回显示给用户的异常信息
     * @return
     */
     String getUserTip();

     void setUserTip(String userTip);

    default String getMessage() {
        return getUserTip() == null ? getErrMsg() : getUserTip();
    }


}
