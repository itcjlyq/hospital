package com.hospital.wechat.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 大模型配置。
 * api-key 仅从环境变量 DEEPSEEK_API_KEY 注入，不得在仓库中写死。
 */
@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    /** API Key（环境变量 DEEPSEEK_API_KEY） */
    private String apiKey = "";

    /** OpenAI 兼容基础地址，不带末尾斜杠 */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** 单次请求超时（秒） */
    private int timeoutSeconds = 30;

    /** 采样温度，医疗场景取低值以降低发散 */
    private double temperature = 0.3;

    /** 是否使用本地 mock 应答（无 key 时用于演示联调） */
    private boolean mockEnabled = false;
}
