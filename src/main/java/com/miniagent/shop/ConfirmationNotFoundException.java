package com.miniagent.shop;

/** 确认 id 不存在或已经处理过，不能再提交。 */
public class ConfirmationNotFoundException extends RuntimeException {

    public ConfirmationNotFoundException(String confirmationId) {
        super("confirmation not found: " + confirmationId);
    }
}
