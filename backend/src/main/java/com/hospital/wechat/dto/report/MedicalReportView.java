package com.hospital.wechat.dto.report;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 检查报告记录视图
 */
public record MedicalReportView(
        Long id,
        Long profileId,
        String reportType,
        String reportDate,
        Map<String, String> indicators,
        String aiInterpretation,
        LocalDateTime createTime
) {
}
