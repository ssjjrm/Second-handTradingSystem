package com.secondhand.common;

import lombok.Data;

/**
 * 统一响应体。
 *
 * <p>所有接口都返回这个结构，前端只需要判断一次 {@code code}：
 * <pre>
 * { "code": 200, "message": "success", "data": { ... } }
 * </pre>
 *
 * <p>约定：
 * <ul>
 *   <li>{@code 200} 成功</li>
 *   <li>{@code 400} 参数错误</li>
 *   <li>{@code 401} 未登录 / token 失效</li>
 *   <li>{@code 403} 无权限（非管理员访问后台等）</li>
 *   <li>{@code 404} 资源不存在</li>
 *   <li>{@code 500} 服务器内部错误</li>
 * </ul>
 */
@Data
public class Result<T> {

    public static final int CODE_SUCCESS = 200;
    public static final int CODE_BAD_REQUEST = 400;
    public static final int CODE_UNAUTHORIZED = 401;
    public static final int CODE_FORBIDDEN = 403;
    public static final int CODE_NOT_FOUND = 404;
    public static final int CODE_ERROR = 500;

    private Integer code;
    private String message;
    private T data;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功，无数据 */
    public static <T> Result<T> success() {
        return new Result<>(CODE_SUCCESS, "success", null);
    }

    /** 成功，带数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(CODE_SUCCESS, "success", data);
    }

    /** 成功，自定义提示语 */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(CODE_SUCCESS, message, data);
    }

    /** 失败，指定状态码 */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败，默认 500 */
    public static <T> Result<T> error(String message) {
        return new Result<>(CODE_ERROR, message, null);
    }
}
