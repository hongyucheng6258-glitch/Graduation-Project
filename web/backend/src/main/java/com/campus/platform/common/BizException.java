package com.campus.platform.common;

import lombok.Getter;

/**
 * 业务异常：携带 ResultCode 或自定义 code/message，由全局异常处理器统一转 R。
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
