package com.hospital.wechat.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek Chat Completions 客户端（OpenAI 兼容协议）。
 * 仅负责真实 HTTP 调用；是否走 mock 由 {@link #available()} 体现，由上层决定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekClient {

    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;

    private HttpClient httpClient;

    /** 一条对话消息 */
    public record Message(String role, String content) {
        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 是否可进行真实调用（未开启 mock 且配置了 key）
     */
    public boolean available() {
        return !properties.isMockEnabled()
                && properties.getApiKey() != null
                && !properties.getApiKey().isBlank();
    }

    /**
     * 发起一次多轮对话，强制 JSON 对象输出。
     *
     * @return 助手回复文本（调用方按 JSON 解析）
     * @throws AiCallException 网络/HTTP/解析失败时抛出
     */
    public String chatJson(List<Message> messages) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", properties.getModel());
            body.put("temperature", properties.getTemperature());
            body.put("stream", false);
            body.put("response_format", Map.of("type", "json_object"));

            List<Map<String, String>> msgList = new ArrayList<>();
            for (Message m : messages) {
                msgList.add(Map.of("role", m.role(), "content", m.content()));
            }
            body.put("messages", msgList);

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("DeepSeek 返回非 2xx: status={}, body={}", response.statusCode(),
                        response.body() == null ? "" : response.body().length() > 500
                                ? response.body().substring(0, 500) : response.body());
                throw new AiCallException("模型服务暂时不可用（HTTP " + response.statusCode() + "）");
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                throw new AiCallException("模型返回内容为空");
            }
            return contentNode.asText();
        } catch (AiCallException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiCallException("模型调用被中断", e);
        } catch (Exception e) {
            log.warn("DeepSeek 调用失败: {}", e.getMessage());
            throw new AiCallException("模型服务调用失败，请稍后重试", e);
        }
    }
}
