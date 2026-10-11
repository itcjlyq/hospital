package com.hospital.wechat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.wechat.ai.AiCallException;
import com.hospital.wechat.ai.DeepSeekClient;
import com.hospital.wechat.ai.LiverTriagePromptService;
import com.hospital.wechat.ai.RedFlagGuard;
import com.hospital.wechat.ai.TriageAiResult;
import com.hospital.wechat.dto.triage.AssessmentView;
import com.hospital.wechat.dto.triage.CreateSessionRequest;
import com.hospital.wechat.dto.triage.MessageView;
import com.hospital.wechat.dto.triage.SendMessageRequest;
import com.hospital.wechat.dto.triage.SessionDetail;
import com.hospital.wechat.dto.triage.SessionSummary;
import com.hospital.wechat.dto.triage.TurnResponse;
import com.hospital.wechat.entity.HealthProfile;
import com.hospital.wechat.entity.HealthProfileMapper;
import com.hospital.wechat.entity.TriageMessage;
import com.hospital.wechat.entity.TriageMessageMapper;
import com.hospital.wechat.entity.TriageSession;
import com.hospital.wechat.entity.TriageSessionMapper;
import com.hospital.wechat.service.TriageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 初筛会话编排：红旗前置拦截 → 多轮问诊 → 结构化结论落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriageServiceImpl implements TriageService {

    private final TriageSessionMapper sessionMapper;
    private final TriageMessageMapper messageMapper;
    private final HealthProfileMapper profileMapper;
    private final RedFlagGuard redFlagGuard;
    private final LiverTriagePromptService promptService;
    private final ObjectMapper objectMapper;

    @Override
    public TurnResponse createSession(Long userId, CreateSessionRequest request) {
        if (request == null || request.profileId() == null) {
            throw new IllegalArgumentException("请选择健康档案");
        }
        String complaint = request.chiefComplaint() == null ? "" : request.chiefComplaint().trim();
        if (complaint.isBlank()) {
            throw new IllegalArgumentException("请先描述你的主要不适");
        }
        if (complaint.length() > 500) {
            throw new IllegalArgumentException("症状描述请控制在500字以内");
        }

        HealthProfile profile = getOwnedProfile(userId, request.profileId());

        TriageSession session = new TriageSession();
        session.setUserId(userId);
        session.setProfileId(profile.getId());
        session.setChiefComplaint(complaint);
        session.setStatus("ONGOING");
        sessionMapper.insert(session);

        // 用户主诉落库
        saveUserMessage(session.getId(), complaint);

        // 急症红旗前置
        var redFlag = redFlagGuard.detect(complaint);
        if (redFlag.isPresent()) {
            return handleEmergency(session, redFlag.get());
        }

        return runAiTurn(session, profile);
    }

    @Override
    public TurnResponse sendMessage(Long userId, Long sessionId, SendMessageRequest request) {
        String content = request == null || request.content() == null ? "" : request.content().trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("内容不能为空");
        }
        if (content.length() > 500) {
            throw new IllegalArgumentException("内容请控制在500字以内");
        }

        TriageSession session = getOwnedSession(userId, sessionId);
        if (!"ONGOING".equals(session.getStatus())) {
            throw new IllegalStateException("本次初筛已结束，请发起新的初筛");
        }
        HealthProfile profile = getOwnedProfile(userId, session.getProfileId());

        saveUserMessage(session.getId(), content);

        var redFlag = redFlagGuard.detect(content);
        if (redFlag.isPresent()) {
            return handleEmergency(session, redFlag.get());
        }

        return runAiTurn(session, profile);
    }

    @Override
    public List<SessionSummary> listSessions(Long userId) {
        List<TriageSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<TriageSession>()
                .eq(TriageSession::getUserId, userId)
                .orderByDesc(TriageSession::getId));
        return sessions.stream()
                .map(s -> new SessionSummary(s.getId(), s.getProfileId(), s.getChiefComplaint(),
                        s.getStatus(), s.getRiskLevel(), s.getCreateTime()))
                .toList();
    }

    @Override
    public SessionDetail getSession(Long userId, Long sessionId) {
        TriageSession session = getOwnedSession(userId, sessionId);
        List<TriageMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<TriageMessage>()
                .eq(TriageMessage::getSessionId, sessionId)
                .orderByAsc(TriageMessage::getId));

        return new SessionDetail(
                session.getId(),
                session.getProfileId(),
                session.getChiefComplaint(),
                session.getStatus(),
                toAssessmentView(session),
                messages.stream().map(this::toMessageView).toList(),
                session.getCreateTime()
        );
    }

    // ------------------------------------------------------------------
    // 核心流程
    // ------------------------------------------------------------------

    private TurnResponse runAiTurn(TriageSession session, HealthProfile profile) {
        List<TriageMessage> history = messageMapper.selectList(new LambdaQueryWrapper<TriageMessage>()
                .eq(TriageMessage::getSessionId, session.getId())
                .orderByAsc(TriageMessage::getId));

        List<DeepSeekClient.Message> conversation = new ArrayList<>();
        for (TriageMessage m : history) {
            if ("USER".equals(m.getRole())) {
                conversation.add(DeepSeekClient.Message.user(m.getContent()));
            } else if ("ASSISTANT".equals(m.getRole())) {
                conversation.add(DeepSeekClient.Message.assistant(m.getContent()));
            }
        }

        TriageAiResult result;
        try {
            result = promptService.nextTurn(profile, conversation);
        } catch (AiCallException e) {
            // 服务异常：给一条可继续的保守系统提示，不把错误抛给用户
            log.warn("初筛 AI 调用失败 sessionId={}: {}", session.getId(), e.getMessage());
            String fallback = "智能助手暂时无法响应。为不延误病情，若不适持续或加重，请直接前往医院消化内科/肝病科就诊；出现呕血、黑便、意识改变等紧急情况请立即拨打120。";
            TriageMessage sysMsg = saveAssistantMessage(session.getId(), fallback, List.of());
            return new TurnResponse(session.getId(), session.getStatus(), session.getRiskLevel(),
                    null, toMessageView(sysMsg), null);
        }

        TriageMessage assistantMsg = saveAssistantMessage(session.getId(), result.reply(), result.options());

        AssessmentView assessmentView = null;
        if (result.finished() && result.assessment() != null) {
            TriageAiResult.Assessment a = result.assessment();
            session.setStatus("FINISHED");
            session.setRiskLevel(a.riskLevel());
            session.setPossibleDirections(joinList(a.possibleDirections()));
            session.setSuggestedDepartments(joinList(a.suggestedDepartments()));
            session.setSuggestedExams(joinList(a.suggestedExams()));
            session.setLifestyleAdvice(joinList(a.lifestyleAdvice()));
            session.setConclusion(a.conclusion());
            sessionMapper.updateById(session);
            assessmentView = new AssessmentView(
                    a.riskLevel(), a.possibleDirections(), a.suggestedDepartments(),
                    a.suggestedExams(), a.lifestyleAdvice(), a.conclusion());
        }

        return new TurnResponse(session.getId(), session.getStatus(), session.getRiskLevel(),
                null, toMessageView(assistantMsg), assessmentView);
    }

    private TurnResponse handleEmergency(TriageSession session, RedFlagGuard.RedFlagHit hit) {
        session.setStatus("EMERGENCY");
        session.setRiskLevel("EMERGENCY");
        session.setPossibleDirections("疑似急症（" + hit.category() + "）");
        session.setConclusion(hit.advice());
        sessionMapper.updateById(session);

        TriageMessage sysMsg = new TriageMessage();
        sysMsg.setSessionId(session.getId());
        sysMsg.setRole("SYSTEM");
        sysMsg.setContent(hit.advice());
        sysMsg.setOptionsJson(null);
        messageMapper.insert(sysMsg);

        TurnResponse.EmergencyView emergency = new TurnResponse.EmergencyView(hit.category(), hit.advice());
        return new TurnResponse(session.getId(), "EMERGENCY", "EMERGENCY",
                emergency, toMessageView(sysMsg), null);
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private HealthProfile getOwnedProfile(Long userId, Long profileId) {
        HealthProfile profile = profileMapper.selectById(profileId);
        if (profile == null || !userId.equals(profile.getUserId())) {
            throw new IllegalArgumentException("健康档案不存在");
        }
        return profile;
    }

    private TriageSession getOwnedSession(Long userId, Long sessionId) {
        TriageSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new IllegalArgumentException("初筛记录不存在");
        }
        return session;
    }

    private void saveUserMessage(Long sessionId, String content) {
        TriageMessage m = new TriageMessage();
        m.setSessionId(sessionId);
        m.setRole("USER");
        m.setContent(content);
        messageMapper.insert(m);
    }

    private TriageMessage saveAssistantMessage(Long sessionId, String content, List<String> options) {
        TriageMessage m = new TriageMessage();
        m.setSessionId(sessionId);
        m.setRole("ASSISTANT");
        m.setContent(content);
        if (options != null && !options.isEmpty()) {
            try {
                m.setOptionsJson(objectMapper.writeValueAsString(options));
            } catch (Exception e) {
                m.setOptionsJson(null);
            }
        }
        messageMapper.insert(m);
        return m;
    }

    private MessageView toMessageView(TriageMessage m) {
        List<String> options = Collections.emptyList();
        if (m.getOptionsJson() != null && !m.getOptionsJson().isBlank()) {
            try {
                options = objectMapper.readValue(m.getOptionsJson(), new TypeReference<List<String>>() {
                });
            } catch (Exception ignored) {
                options = Collections.emptyList();
            }
        }
        return new MessageView(m.getRole(), m.getContent(), options, m.getCreateTime());
    }

    private AssessmentView toAssessmentView(TriageSession s) {
        if (s.getRiskLevel() == null) {
            return null;
        }
        return new AssessmentView(
                s.getRiskLevel(),
                split(s.getPossibleDirections()),
                split(s.getSuggestedDepartments()),
                split(s.getSuggestedExams()),
                split(s.getLifestyleAdvice()),
                s.getConclusion()
        );
    }

    private String joinList(List<String> list) {
        return list == null ? null : String.join("||", list);
    }

    private List<String> split(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : s.split("\\|\\|")) {
            if (!part.isBlank()) {
                result.add(part.trim());
            }
        }
        return result;
    }
}
