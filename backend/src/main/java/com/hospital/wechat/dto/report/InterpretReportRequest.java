package com.hospital.wechat.dto.report;

import java.util.Map;

/**
 * 提交检查指标并请求 AI 解读
 */
public record InterpretReportRequest(
        Long profileId,
        String reportType,
        String reportDate,
        Map<String, String> indicators
) {
}
