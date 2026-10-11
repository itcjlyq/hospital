package com.hospital.wechat.service;

import com.hospital.wechat.dto.triage.CreateSessionRequest;
import com.hospital.wechat.dto.triage.SendMessageRequest;
import com.hospital.wechat.dto.triage.SessionDetail;
import com.hospital.wechat.dto.triage.SessionSummary;
import com.hospital.wechat.dto.triage.TurnResponse;

import java.util.List;

/**
 * AI 初筛会话服务
 */
public interface TriageService {

    TurnResponse createSession(Long userId, CreateSessionRequest request);

    TurnResponse sendMessage(Long userId, Long sessionId, SendMessageRequest request);

    List<SessionSummary> listSessions(Long userId);

    SessionDetail getSession(Long userId, Long sessionId);
}
