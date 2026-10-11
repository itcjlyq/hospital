package com.hospital.wechat.dto.triage;

/**
 * 创建初筛会话请求
 */
public record CreateSessionRequest(Long profileId, String chiefComplaint) {
}
