package com.hospital.wechat.config;

import com.hospital.wechat.dto.Result;
import com.hospital.wechat.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * JWT 请求过滤器
 * 对需要登录的接口进行 token 验证。本系统只有一种身份：微信用户（wx_user）。
 */
@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    /** 白名单路径（不需要token） */
    private static final List<String> WHITE_LIST = List.of(
            "/api/wx/login",
            "/api/wx/health"
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        // 白名单放行
        if (WHITE_LIST.stream().anyMatch(uri::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 获取token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "请先登录");
            return;
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            writeUnauthorized(response, "登录已过期，请重新登录");
            return;
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            writeUnauthorized(response, "登录已过期，请重新登录");
            return;
        }
        request.setAttribute("userId", userId);

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        Result<?> result = Result.fail(401, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
