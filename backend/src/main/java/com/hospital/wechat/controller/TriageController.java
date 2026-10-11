package com.hospital.wechat.controller;

import com.hospital.wechat.dto.Result;
import com.hospital.wechat.dto.triage.CreateSessionRequest;
import com.hospital.wechat.dto.triage.SendMessageRequest;
import com.hospital.wechat.dto.triage.SessionDetail;
import com.hospital.wechat.dto.triage.SessionSummary;
import com.hospital.wechat.dto.triage.TurnResponse;
import com.hospital.wechat.service.TriageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 肝病初筛会话
 */
@RestController
@RequestMapping("/api/triage")
@RequiredArgsConstructor
public class TriageController {

    private final TriageService triageService;

    @PostMapping("/sessions")
    public Result<TurnResponse> createSession(@RequestBody CreateSessionRequest request,
                                               @NonNull HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(triageService.createSession(userId, request));
    }

    @PostMapping("/sessions/{id}/messages")
    public Result<TurnResponse> sendMessage(@PathVariable("id") Long sessionId,
                                             @RequestBody SendMessageRequest request,
                                             @NonNull HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(triageService.sendMessage(userId, sessionId, request));
    }

    @GetMapping("/sessions")
    public Result<List<SessionSummary>> listSessions(@NonNull HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(triageService.listSessions(userId));
    }

    @GetMapping("/sessions/{id}")
    public Result<SessionDetail> getSession(@PathVariable("id") Long sessionId,
                                            @NonNull HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(triageService.getSession(userId, sessionId));
    }
}
