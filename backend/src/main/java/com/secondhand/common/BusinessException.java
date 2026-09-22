package com.secondhand.common;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>Service 层发现业务规则不满足时抛出（例如"密码错误"、"商品已售出"），
 * 由 {@link GlobalExceptionHandler} 统一转成 {@link Result} 返回给前端。
 *
 * <p>这样做的好处：Service 里不用返回各种奇怪的对象来表示失败，
 * 直接抛异常打断流程，代码更干净。
 *
 * <p>注意：它继承 {@code RuntimeException}（非受检异常），
 * 所以不会强迫每个调用方写 try-catch。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        this(Result.CODE_ERROR, message);
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
