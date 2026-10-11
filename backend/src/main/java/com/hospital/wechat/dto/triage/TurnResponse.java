package com.hospital.wechat.dto.triage;

/**
 * 一轮交互的返回（创建会话 / 发送消息通用）
 */
public record TurnResponse(
        Long sessionId,
        String status,
        String riskLevel,
        EmergencyView emergency,
        MessageView message,
        AssessmentView assessment
) {

    /**
     * 急症拦截信息（命中时非空）
     */
    public record EmergencyView(String category, String advice) {
    }
}
