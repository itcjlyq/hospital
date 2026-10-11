package com.hospital.wechat.dto.triage;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 初筛会话详情（含全部消息与结论）
 */
public record SessionDetail(
        Long id,
        Long profileId,
        String chiefComplaint,
        String status,
        AssessmentView assessment,
        List<MessageView> messages,
        LocalDateTime createTime
) {
}
