package com.fc.v2.common.exception;

/**
 * 排涝抢险作业单流转业务规则异常：跨档抢挪、同一档挪第二回、办结销号后再动、
 * 退回后又往已作废的旧记上添笔等红线一律抛此异常，由全局处理退回消息。
 *
 * @author fuce
 * @date 2026-09-27
 */
public class DrainWorkOrderException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DrainWorkOrderException(String message) {
        super(message);
    }
}
