package com.campus.platform.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体（黑马点评风格）。
 *
 * <ul>
 *   <li>成功：{@code success=true, code=200, message="success"}</li>
 *   <li>失败：{@code success=false, errorMsg=失败原因, code=业务码}</li>
 * </ul>
 *
 * <p>{@code code} / {@code message} 是给前端统一拦截器保留的兼容字段：
 * 前端仍按 {@code code===200} 判断成功、{@code code===401} 清理登录态，因此接口契约不变。
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否成功 */
    private Boolean success;

    /** 失败原因 */
    private String errorMsg;

    /** 业务数据 */
    private T data;

    /** 分页总数（可选） */
    private Long total;

    /** 兼容字段：业务码 */
    private Integer code;

    /** 兼容字段：提示信息 */
    private String message;

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setSuccess(true);
        r.setCode(ResultCode.SUCCESS.getCode());
        r.setMessage(ResultCode.SUCCESS.getMessage());
        r.setData(data);
        return r;
    }

    public static <T> Result<T> ok(T data, Long total) {
        Result<T> r = ok(data);
        r.setTotal(total);
        return r;
    }

    public static <T> Result<T> fail(String errorMsg) {
        return fail(ResultCode.BAD_REQUEST.getCode(), errorMsg);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> r = new Result<>();
        r.setSuccess(false);
        r.setCode(code);
        r.setErrorMsg(message);
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return fail(resultCode.getCode(), resultCode.getMessage());
    }

    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return fail(resultCode.getCode(), message);
    }
}
