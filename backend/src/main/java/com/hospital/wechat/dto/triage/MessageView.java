package com.hospital.wechat.dto.triage;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话消息视图
 */
public record MessageView(
        String role,
        String content,
        List<String> options,
        LocalDateTime createTime
) {
}
