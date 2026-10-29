package com.campus.platform.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体。
 * <ul>
 *   <li>成功：{@code code=200, message="success"}</li>
 *   <li>失败：使用 {@link ResultCode} 的 code/message，或自定义 message 覆盖</li>
 * </ul>
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer code;
    private String message;
    private T data;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(ResultCode.SUCCESS.getCode());
        r.setMessage(ResultCode.SUCCESS.getMessage());
        r.setData(data);
        return r;
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        R<T> r = new R<>();
        r.setCode(resultCode.getCode());
        r.setMessage(resultCode.getMessage());
        return r;
    }

    public static <T> R<T> fail(ResultCode resultCode, String message) {
        R<T> r = new R<>();
        r.setCode(resultCode.getCode());
        r.setMessage(message);
        return r;
    }

    public static <T> R<T> fail(Integer code, String message) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}
