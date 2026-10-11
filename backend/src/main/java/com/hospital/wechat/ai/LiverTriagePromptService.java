package com.hospital.wechat.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.wechat.entity.HealthProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 肝病初筛 Prompt 编排：
 * 约束模型只做"初筛/导诊"、以固定 JSON 协议输出；负责 JSON 容错解析、
 * mock 应答（无 key 演示）与输出合规改写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiverTriagePromptService {

    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    private static final Set<String> ALLOWED_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "EMERGENCY");
    private static final String DISCLAIMER = "以上内容仅供健康科普与初筛参考，不能替代医生面诊或药师审核，具体诊断与处理请以线下就医评估为准。";

    private static final String SYSTEM_PROMPT = """
            你是一名肝病方向的【健康初筛与智能导诊助手】，服务于一款健康小程序。你必须严格遵守以下边界：

            【职责边界】
            1. 你只做：健康信息收集、症状初筛、风险分级、建议就诊科室与检查项目、肝病科普与生活方式建议。
            2. 你绝不：给出确定诊断、宣布用户"得了/确诊"某种疾病、开具或推荐处方药、指导用户停药/换药/调整剂量。
            3. 措辞必须谨慎，使用"可能与……有关""需要排除……""建议就医评估以明确原因"等表述。
            4. 若信息提示急症（呕血、黑便/大量便血、意识模糊或嗜睡胡言乱语、昏迷、剧烈持续腹痛、自伤倾向等），
               立即结束追问，risk_level=EMERGENCY，并在回复中明确建议立即急诊或拨打120。

            【问诊要求】
            1. 通过多轮简短提问收集：症状起病时间与持续时长、性质与程度、伴随症状（乏力、食欲减退、腹胀、黄疸、
               肝区不适、恶心、体重下降、尿色加深等）、既往乙肝/丙肝/肝硬化/脂肪肝、饮酒史、家族史、已做检查。
            2. 每次只问 1 个最关键的问题，给出 2-4 个可快速选择的中文选项；总追问不超过 4 轮，避免开放式长问题。
            3. 信息充分（通常 3-4 轮）后结束问诊并给出结构化结论。
            4. 高危人群（乙肝/丙肝感染、肝硬化、肝癌家族史、长期大量饮酒等）即使症状轻微，也应建议尽早就医并做
               肝功能、甲胎蛋白(AFP)、腹部超声等基础筛查；并提示高危人群一般每 6 个月复查 AFP 与肝脏超声。

            【输出格式 —— 必须严格遵守】
            每一轮都只输出一个 JSON 对象，不要输出 JSON 以外的任何文字或 markdown 代码块。
            追问中（未结束）：
            {"reply":"本轮回复与问题","options":["选项1","选项2","选项3"],"finished":false,"assessment":null}
            结束时：
            {"reply":"简短总结","options":[],"finished":true,"assessment":{
              "risk_level":"LOW|MEDIUM|HIGH|EMERGENCY",
              "possible_directions":["谨慎措辞的可能方向1","方向2"],
              "suggested_departments":["建议科室，如消化内科/肝病科/感染科/肝胆外科"],
              "suggested_exams":["建议检查，如肝功能、甲胎蛋白AFP、腹部超声、乙肝五项、HBV-DNA等"],
              "lifestyle_advice":["戒酒","避免自行服用偏方/不明成分药物","规律作息"],
              "conclusion":"总体判断与就医提示，必须明确建议线下就医评估"
            }}
            """;

    /**
     * 执行一轮初筛。
     *
     * @param profile      健康档案（可为空）
     * @param conversation 完整对话（system 之外），最后一条必须是本轮用户消息
     */
    public TriageAiResult nextTurn(HealthProfile profile, List<DeepSeekClient.Message> conversation) {
        if (!deepSeekClient.available()) {
            return mockTurn(profile, conversation);
        }

        List<DeepSeekClient.Message> messages = new ArrayList<>();
        messages.add(DeepSeekClient.Message.system(SYSTEM_PROMPT));
        messages.add(DeepSeekClient.Message.user("【就诊人背景信息】\n" + buildProfileContext(profile)));
        messages.addAll(conversation);

        String raw = deepSeekClient.chatJson(messages);
        TriageAiResult parsed = tryParse(raw);
        if (parsed == null) {
            // 一次修复重试
            log.info("模型输出 JSON 解析失败，执行一次修复重试");
            List<DeepSeekClient.Message> retryMessages = new ArrayList<>(messages);
            retryMessages.add(DeepSeekClient.Message.user(
                    "你上一条回复不是合法 JSON。请严格按照约定，只输出一个 JSON 对象，不要输出任何其他内容。"));
            String retryRaw = deepSeekClient.chatJson(retryMessages);
            parsed = tryParse(retryRaw);
            if (parsed == null) {
                throw new AiCallException("模型返回格式异常，请稍后重试");
            }
        }
        return sanitize(parsed);
    }

    // ------------------------------------------------------------------
    // JSON 解析与合规改写
    // ------------------------------------------------------------------

    private TriageAiResult tryParse(String raw) {
        try {
            String json = raw.trim();
            // 容错：去掉可能的 ```json 包裹
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            }
            JsonNode root = objectMapper.readTree(json);
            String reply = textOrEmpty(root.path("reply"));
            List<String> options = readStringList(root.path("options"));
            boolean finished = root.path("finished").asBoolean(false);

            TriageAiResult.Assessment assessment = null;
            if (finished && root.hasNonNull("assessment")) {
                JsonNode a = root.path("assessment");
                String level = textOrEmpty(a.path("risk_level")).toUpperCase();
                if (!ALLOWED_LEVELS.contains(level)) {
                    level = "MEDIUM";
                }
                String conclusion = textOrEmpty(a.path("conclusion"));
                if (!conclusion.isBlank() && !conclusion.contains("仅供")) {
                    conclusion = conclusion + " " + DISCLAIMER;
                }
                assessment = new TriageAiResult.Assessment(
                        level,
                        readStringList(a.path("possible_directions")),
                        readStringList(a.path("suggested_departments")),
                        readStringList(a.path("suggested_exams")),
                        readStringList(a.path("lifestyle_advice")),
                        conclusion
                );
            }
            if (reply.isBlank()) {
                return null;
            }
            return new TriageAiResult(complyWords(reply), options, finished, assessment);
        } catch (Exception e) {
            log.warn("解析模型 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    /** 越界措辞的保守替换（双保险，主要靠 system prompt 约束） */
    private String complyWords(String text) {
        return text
                .replace("确诊为", "需要警惕")
                .replace("诊断为", "可能与……有关，需要排除")
                .replace("你得了", "你的情况可能与")
                .replace("建议服用", "不建议自行用药，用药请咨询医生。相关情况");
    }

    private List<String> readStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> {
                String s = n.asText("").trim();
                if (!s.isEmpty()) {
                    list.add(s);
                }
            });
        }
        return list;
    }

    private String textOrEmpty(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? "" : node.asText("").trim();
    }

    private TriageAiResult sanitize(TriageAiResult r) {
        return r;
    }

    // ------------------------------------------------------------------
    // 档案摘要
    // ------------------------------------------------------------------

    private String buildProfileContext(HealthProfile p) {
        if (p == null) {
            return "未提供就诊人背景信息。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("性别").append(nullToDash(p.getGender()))
                .append("，年龄").append(p.getAge() == null ? "未知" : p.getAge()).append("岁")
                .append("，关系：").append(nullToDash(p.getRelation())).append("；");
        sb.append("乙肝携带/感染：").append(yesNo(p.getHbvCarrier())).append("；");
        sb.append("丙肝携带/感染：").append(yesNo(p.getHcvCarrier())).append("；");
        sb.append("肝硬化：").append(yesNo(p.getCirrhosis())).append("；");
        sb.append("脂肪肝：").append(switch (p.getFattyLiver() == null ? 0 : p.getFattyLiver()) {
            case 1 -> "轻度";
            case 2 -> "中重度";
            default -> "无";
        }).append("；");
        sb.append("糖尿病：").append(yesNo(p.getDiabetes())).append("；");
        sb.append("饮酒史：").append(nullToDash(p.getAlcoholHistory())).append("；");
        sb.append("吸烟：").append(yesNo(p.getSmoking())).append("；");
        sb.append("肝癌家族史：").append(yesNo(p.getFamilyLiverCancer())).append("；");
        sb.append("过敏史：").append(nullToDash(p.getAllergy())).append("；");
        sb.append("长期用药：").append(nullToDash(p.getCurrentMedication())).append("。");
        if (p.getRemark() != null && !p.getRemark().isBlank()) {
            sb.append("备注：").append(p.getRemark());
        }
        return sb.toString();
    }

    private String yesNo(Integer v) {
        return v != null && v == 1 ? "是" : "否";
    }

    private String nullToDash(String s) {
        return s == null || s.isBlank() ? "无" : s;
    }

    // ------------------------------------------------------------------
    // 检查指标解读
    // ------------------------------------------------------------------

    private static final String REPORT_SYSTEM_PROMPT = """
            你是肝病方向的检查报告科普解读助手。请用通俗易懂的中文解释用户提交的检查指标：
            1. 概括这些指标大致反映什么、是否有明显异常倾向（不得据此下确定诊断）；
            2. 对每个用户提供的指标用一句话解释含义（如 ALT/AST 反映肝细胞损伤、总胆红素反映胆红素代谢、
               AFP 是肝癌筛查标志物之一、腹部超声看肝脏形态与占位等）；
            3. 给出温和的复查/就医建议（建议挂消化内科或肝病科、结合其他检查综合判断）；
            4. 不做诊断、不推荐具体药物、不指导停药换药。
            只输出 JSON 对象：{"interpretation":"给用户看的完整解读文本（可用\\n分段）"}，不要输出其他内容。
            """;

    /**
     * 解读一份手工录入的检查指标
     *
     * @return 通俗解读文本
     */
    public String interpretIndicators(HealthProfile profile, String reportType, Map<String, String> indicators) {
        if (!deepSeekClient.available()) {
            return mockInterpret(reportType, indicators);
        }

        String typeName = switch (reportType == null ? "OTHER" : reportType) {
            case "LIVER_FUNCTION" -> "肝功能";
            case "AFP" -> "甲胎蛋白(AFP)";
            case "ULTRASOUND" -> "腹部超声";
            default -> "其他检查";
        };

        Map<String, String> valid = new LinkedHashMap<>();
        if (indicators != null) {
            indicators.forEach((k, v) -> {
                if (k != null && !k.isBlank() && v != null && !v.isBlank()) {
                    valid.put(k.trim(), v.trim());
                }
            });
        }

        String userContent = "【就诊人背景】\n" + buildProfileContext(profile)
                + "\n【检查类型】" + typeName
                + "\n【检查指标（名称=数值/描述）】\n" + valid.entrySet().stream()
                .map(e -> "- " + e.getKey() + " = " + e.getValue())
                .reduce((a, b) -> a + "\n" + b).orElse("（未填写）");

        List<DeepSeekClient.Message> messages = List.of(
                DeepSeekClient.Message.system(REPORT_SYSTEM_PROMPT),
                DeepSeekClient.Message.user(userContent)
        );

        String raw = deepSeekClient.chatJson(messages);
        String text = extractInterpretation(raw);
        if (text == null) {
            String retryRaw = deepSeekClient.chatJson(List.of(
                    DeepSeekClient.Message.system(REPORT_SYSTEM_PROMPT),
                    DeepSeekClient.Message.user(userContent),
                    DeepSeekClient.Message.user("请只输出 {\"interpretation\":\"...\"} 这样的 JSON 对象。")
            ));
            text = extractInterpretation(retryRaw);
        }
        if (text == null) {
            throw new AiCallException("解读结果格式异常，请稍后重试");
        }
        if (!text.contains("仅供")) {
            text = text + "\n\n" + DISCLAIMER;
        }
        return text;
    }

    private String extractInterpretation(String raw) {
        try {
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            }
            JsonNode root = objectMapper.readTree(json);
            String text = root.path("interpretation").asText("").trim();
            return text.isEmpty() ? null : complyWords(text);
        } catch (Exception e) {
            log.warn("解析指标解读 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    private String mockInterpret(String reportType, Map<String, String> indicators) {
        StringBuilder sb = new StringBuilder();
        int count = indicators == null ? 0 : indicators.size();
        sb.append("你提交了 ").append(count).append(" 项指标。以下为科普性解读（演示数据，非真实分析）：\n");
        if (indicators != null) {
            indicators.forEach((k, v) -> {
                String meaning = indicatorMeaning(k);
                sb.append("• ").append(k).append(" = ").append(v).append("：").append(meaning).append("\n");
            });
        }
        sb.append("\n单次指标轻度波动可能与熬夜、饮酒、劳累、感冒服药等有关，需要结合复查趋势和腹部超声等综合判断。")
                .append("建议携带报告到消化内科或肝病科门诊咨询；乙肝/丙肝感染、肝硬化或有肝癌家族史者，")
                .append("建议每 6 个月复查 AFP 与肝脏超声。\n\n")
                .append(DISCLAIMER);
        return sb.toString();
    }

    private String indicatorMeaning(String name) {
        if (name == null) {
            return "请结合医院参考范围判断";
        }
        String n = name.toUpperCase();
        if (n.contains("ALT") || n.contains("谷丙")) {
            return "主要反映肝细胞损伤，升高常见于肝细胞炎症";
        }
        if (n.contains("AST") || n.contains("谷草")) {
            return "反映肝细胞及线粒体损伤，需结合 ALT 一起看";
        }
        if (n.contains("胆红素")) {
            return "反映胆红素代谢，明显升高时可出现黄疸";
        }
        if (n.contains("AFP") || n.contains("甲胎")) {
            return "肝癌筛查标志物之一，升高不等于癌症，需结合超声/影像判断";
        }
        if (n.contains("白蛋白")) {
            return "反映肝脏合成功能与营养状态";
        }
        if (n.contains("超声") || n.contains("B超")) {
            return "用于观察肝脏形态、脂肪肝、肝硬化及占位性病变";
        }
        if (n.contains("HBV") || n.contains("乙肝")) {
            return "反映乙肝病毒感染/复制水平，需专科医生评估是否抗病毒治疗";
        }
        return "请对照报告给出的参考范围，异常时咨询专科医生";
    }

    // ------------------------------------------------------------------
    // Mock 应答（无 DeepSeek key 时保证演示链路可用；非急症分支）
    // ------------------------------------------------------------------

    private TriageAiResult mockTurn(HealthProfile profile, List<DeepSeekClient.Message> conversation) {
        long userTurns = conversation.stream().filter(m -> "user".equals(m.role())).count();
        String lastUser = conversation.isEmpty() ? ""
                : conversation.get(conversation.size() - 1).content();

        if (userTurns <= 1) {
            return new TriageAiResult(
                    "了解到你的情况：「" + clip(lastUser, 30) + "」。为了更好地初步判断，这些不适大概持续多久了？",
                    List.of("1周以内", "1-4周", "1-3个月", "超过3个月"),
                    false, null);
        }
        if (userTurns == 2) {
            return new TriageAiResult(
                    "谢谢。除了这些，是否还伴随以下表现？（可选择，也可直接文字补充）",
                    List.of("乏力/食欲减退", "腹胀", "皮肤或眼白发黄", "肝区隐痛或胀痛", "尿色加深", "没有明显伴随表现"),
                    false, null);
        }

        boolean highRisk = profile != null && (eq(profile.getHbvCarrier())
                || eq(profile.getHcvCarrier()) || eq(profile.getCirrhosis())
                || eq(profile.getFamilyLiverCancer()));
        String level = highRisk ? "HIGH" : "MEDIUM";
        String reply = highRisk
                ? "根据你提供的信息和高危背景，建议尽快到消化内科或肝病科门诊评估，并完善肝病相关基础检查。"
                : "信息已基本足够，下面给出初步评估与就医建议。";
        var assessment = new TriageAiResult.Assessment(
                level,
                List.of("症状可能与肝脏或胆道系统问题有关，也可能是疲劳、消化问题等其他原因，需要检查排除",
                        "需结合肝功能与影像学检查判断，不能仅凭症状下结论"),
                List.of("消化内科", "肝病科"),
                List.of("肝功能（ALT/AST/总胆红素/白蛋白）", "甲胎蛋白 AFP", "腹部超声", "乙肝五项 + HBV-DNA"),
                List.of("严格戒酒，避免含酒精饮品", "不要自行服用偏方、保健品或不明成分药物",
                        "规律作息、清淡饮食，避免高脂暴饮暴食", "若为高危人群，建议每6个月复查 AFP 与肝脏超声"),
                "你的情况需要结合线下检查才能明确原因，建议预约消化内科或肝病科门诊评估。"
                        + "若出现呕血、黑便、意识改变、剧烈腹痛等情况请立即急诊。 " + DISCLAIMER
        );
        return new TriageAiResult(reply, List.of(), true, assessment);
    }

    private boolean eq(Integer v) {
        return v != null && v == 1;
    }

    private String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
