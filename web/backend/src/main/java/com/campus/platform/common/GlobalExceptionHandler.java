package com.campus.platform.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器：统一将各类异常转换为 {@link R} 响应体。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常：直接透传 code/message。
     */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * @RequestBody 参数校验失败：取第一个字段错误信息。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : ResultCode.BAD_REQUEST.getMessage();
        return R.fail(ResultCode.BAD_REQUEST, msg);
    }

    /**
     * 表单参数绑定/校验失败：取第一个字段错误信息。
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : ResultCode.BAD_REQUEST.getMessage();
        return R.fail(ResultCode.BAD_REQUEST, msg);
    }

    /**
     * 缺少必填请求参数。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return R.fail(ResultCode.BAD_REQUEST, "缺少必要参数");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public R<Void> handleBadRequest(Exception e) {
        return R.fail(ResultCode.BAD_REQUEST, "请求参数格式错误");
    }

    /**
     * 上传文件超出大小限制。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleUploadSize(MaxUploadSizeExceededException e) {
        return R.fail(ResultCode.BAD_REQUEST, "文件大小超出限制");
    }

    /**
     * 静态资源/接口不存在：Spring 6.1 抛出 NoResourceFoundException。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        return R.fail(ResultCode.NOT_FOUND, "接口不存在");
    }

    /**
     * 兜底异常：记录错误日志后返回系统错误。
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail(ResultCode.SYSTEM_ERROR);
    }
}
