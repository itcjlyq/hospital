package com.hospital.wechat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.wechat.ai.AiCallException;
import com.hospital.wechat.ai.LiverTriagePromptService;
import com.hospital.wechat.dto.Result;
import com.hospital.wechat.dto.report.InterpretReportRequest;
import com.hospital.wechat.dto.report.MedicalReportView;
import com.hospital.wechat.entity.HealthProfile;
import com.hospital.wechat.entity.HealthProfileMapper;
import com.hospital.wechat.entity.MedicalReport;
import com.hospital.wechat.entity.MedicalReportMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检查报告/指标记录与 AI 解读
 */
@Slf4j
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class MedicalReportController {

    private final MedicalReportMapper reportMapper;
    private final HealthProfileMapper profileMapper;
    private final LiverTriagePromptService promptService;
    private final ObjectMapper objectMapper;

    @PostMapping("/interpret")
    public Result<MedicalReportView> interpret(@RequestBody InterpretReportRequest request,
                                               @NonNull HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (request.profileId() == null) {
            throw new IllegalArgumentException("请选择健康档案");
        }
        Map<String, String> indicators = sanitizeIndicators(request.indicators());
        if (indicators.isEmpty()) {
            throw new IllegalArgumentException("请至少填写一项检查指标");
        }

        HealthProfile profile = profileMapper.selectById(request.profileId());
        if (profile == null || !userId.equals(profile.getUserId())) {
            throw new IllegalArgumentException("健康档案不存在");
        }

        String interpretation;
        try {
            interpretation = promptService.interpretIndicators(profile, request.reportType(), indicators);
        } catch (AiCallException e) {
            log.warn("指标解读失败: {}", e.getMessage());
            interpretation = "解读服务暂时不可用。你的指标已保存，请携带报告前往消化内科或肝病科咨询医生，稍后也可重新尝试解读。";
        }

        MedicalReport report = new MedicalReport();
        report.setUserId(userId);
        report.setProfileId(profile.getId());
        report.setReportType(normalizeType(request.reportType()));
        report.setReportDate(parseDate(request.reportDate()));
        try {
            report.setIndicatorsJson(objectMapper.writeValueAsString(indicators));
        } catch (Exception e) {
            throw new IllegalArgumentException("指标数据格式不合法");
        }
        report.setAiInterpretation(interpretation);
        reportMapper.insert(report);

        return Result.success(toView(report, indicators));
    }

    @GetMapping
    public Result<List<MedicalReportView>> list(@RequestParam(required = false) Long profileId,
                                                @NonNull HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        LambdaQueryWrapper<MedicalReport> wrapper = new LambdaQueryWrapper<MedicalReport>()
                .eq(MedicalReport::getUserId, userId)
                .orderByDesc(MedicalReport::getId);
        if (profileId != null) {
            wrapper.eq(MedicalReport::getProfileId, profileId);
        }
        List<MedicalReport> reports = reportMapper.selectList(wrapper);
        return Result.success(reports.stream().map(r -> toView(r, readIndicators(r))).toList());
    }

    @GetMapping("/{id}")
    public Result<MedicalReportView> detail(@PathVariable Long id, @NonNull HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        MedicalReport report = reportMapper.selectById(id);
        if (report == null || !userId.equals(report.getUserId())) {
            throw new IllegalArgumentException("报告不存在");
        }
        return Result.success(toView(report, readIndicators(report)));
    }

    // ------------------------------------------------------------------

    private Map<String, String> sanitizeIndicators(Map<String, String> raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null) {
            return map;
        }
        raw.forEach((k, v) -> {
            if (k != null && !k.isBlank() && v != null && !v.isBlank()) {
                map.put(k.trim(), v.trim());
            }
        });
        return map;
    }

    private Map<String, String> readIndicators(MedicalReport report) {
        if (report.getIndicatorsJson() == null || report.getIndicatorsJson().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(report.getIndicatorsJson(), new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "OTHER";
        }
        return switch (type) {
            case "LIVER_FUNCTION", "AFP", "ULTRASOUND", "OTHER" -> type;
            default -> "OTHER";
        };
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private MedicalReportView toView(MedicalReport r, Map<String, String> indicators) {
        String date = r.getReportDate() == null ? null : r.getReportDate().toString();
        return new MedicalReportView(r.getId(), r.getProfileId(), r.getReportType(), date,
                indicators, r.getAiInterpretation(), r.getCreateTime());
    }
}
