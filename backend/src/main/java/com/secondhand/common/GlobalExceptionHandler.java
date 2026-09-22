package com.secondhand.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 *
 * <p>把各种异常统一转成 {@link Result} 的 JSON 结构，保证：
 * <ol>
 *   <li>前端拿到的永远是同一种格式，不用为每种错误单独处理</li>
 *   <li>服务器内部细节（异常消息、堆栈）不会泄漏给前端</li>
 * </ol>
 *
 * <p>⚠️ 参考项目的反面教材：{@code CartController} / {@code UploadController}
 * 里直接 {@code e.printStackTrace()} 并把 {@code e.getMessage()} 拼进响应，
 * 会把数据库表名、SQL 片段这类内部信息暴露出去。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：Service 层主动抛的，code 和 message 都是给用户看的 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常 [{}] {}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验失败：@Valid 校验不通过时抛。
     * 只把【第一条】错误信息返回，避免一次弹一堆提示。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = (fieldError == null) ? "参数校验失败" : fieldError.getDefaultMessage();
        log.warn("参数校验失败：{}", message);
        return Result.error(Result.CODE_BAD_REQUEST, message);
    }

    /** @RequestBody 上的校验失败走这个（它继承自 BindException，但显式写出来更清楚） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = (fieldError == null) ? "参数校验失败" : fieldError.getDefaultMessage();
        log.warn("参数校验失败：{}", message);
        return Result.error(Result.CODE_BAD_REQUEST, message);
    }

    /**
     * 路径不存在。
     *
     * <p>Spring Boot 3.2 起，访问没有对应 Controller 的路径会抛这个异常。
     * 不处理的话，返回的是 Spring 默认的错误页 / 错误 JSON（格式和我们的 Result 不一样）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.error(Result.CODE_NOT_FOUND, "接口不存在：" + e.getResourcePath());
    }

    /** 兜底：任何没被上面接住的异常 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // 堆栈只打在后端日志里，绝不返回给前端
        log.error("系统异常", e);
        return Result.error(Result.CODE_ERROR, "服务器内部错误，请稍后重试");
    }
}
