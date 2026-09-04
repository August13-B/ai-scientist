package com.aiscientist.ai.verify;

/**
 * 引用核验状态（四态）。
 *
 * 幻觉检测的「引用真实性」关卡只认两态为疑似虚构：
 *  - NOT_FOUND：接口正常返回，确认不存在；
 *  - SUSPICIOUS：论文存在但标题等信息不一致；
 * UNVERIFIABLE 表示网络超时/限流/接口异常，不判虚构，提示重试或人工确认。
 */
public enum CitationStatus {
    /** 确认论文真实存在 */
    VERIFIED,
    /** 接口正常返回并确认不存在（疑似虚构） */
    NOT_FOUND,
    /** 论文存在但标题等信息不一致（疑似虚构） */
    SUSPICIOUS,
    /** 网络超时、限流、接口异常或信息不足（不判虚构） */
    UNVERIFIABLE
}
