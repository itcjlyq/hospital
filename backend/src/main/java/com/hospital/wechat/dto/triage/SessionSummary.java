package com.hospital.wechat.dto.triage;

import java.time.LocalDateTime;

/**
 * 初筛会话列表项
 */
public record SessionSummary(
        Long id,
        Long profileId,
        String chiefComplaint,
        String status,
        String riskLevel,
        LocalDateTime createTime
) {
}
