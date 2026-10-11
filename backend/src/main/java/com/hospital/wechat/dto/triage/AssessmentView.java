package com.hospital.wechat.dto.triage;

import java.util.List;

/**
 * 初筛结论
 */
public record AssessmentView(
        String riskLevel,
        List<String> possibleDirections,
        List<String> suggestedDepartments,
        List<String> suggestedExams,
        List<String> lifestyleAdvice,
        String conclusion
) {
}
