package com.microsoft.playwright.spring.boot.playwright.enums;

import lombok.Getter;

/**
 * 渲染状态
 * @author [@Loong Wan](https://github.com/loong10k)
 */
@Getter
public enum RenderState {

    /**
     * 等待中
     */
    WAITING(0, "等待中"),
    /**
     * 生成中
     */
    GENERATING(1, "生成中"),
    /**
     * 生成成功
     */
    SUCCESS(2, "生成成功"),
    /**
     * 生成失败
     */
    FAIL(3, "生成失败"),
    /**
     * 检查不通过
     */
    CHECK_FAIL(4, "检查不通过"),
    ;

    private int state ;
    private String desc ;

    RenderState(int state, String desc) {
        this.state = state;
        this.desc = desc;
    }

    public static RenderState getRenderState(int state) {
        for (RenderState renderState : RenderState.values()) {
            if (renderState.getState() == state) {
                return renderState;
            }
        }
        return null;
    }

}
