package com.yizhangtu.common;

import lombok.Getter;

/**
 * 业务异常：消息直接透出给前端展示。
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务错误码（默认 500） */
    private final int code;

    public BizException(String message) {
        super(message);
        this.code = 500;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
