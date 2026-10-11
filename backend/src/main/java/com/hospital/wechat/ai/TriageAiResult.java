package com.hospital.wechat.ai;

import java.util.List;

/**
 * 模型一轮初筛输出（解析后的结构）
 */
public record TriageAiResult(
        String reply,
        List<String> options,
        boolean finished,
        Assessment assessment
) {

    /**
     * 初筛结论（finished=true 时非空）
     */
    public record Assessment(
            String riskLevel,
            List<String> possibleDirections,
            List<String> suggestedDepartments,
            List<String> suggestedExams,
            List<String> lifestyleAdvice,
            String conclusion
    ) {
    }
}
