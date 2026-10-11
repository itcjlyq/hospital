package com.hospital.wechat.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 急症红旗守卫（代码内置、最高优先级，不依赖模型判断）。
 * 与 sys_red_flag 表初始数据保持一致；命中即中止常规初筛并提示急诊。
 */
@Slf4j
@Component
public class RedFlagGuard {

    public record RedFlagRule(String keyword, String category, String advice) {
    }

    public record RedFlagHit(String keyword, String category, String advice) {
    }

    private static final List<RedFlagRule> RULES = List.of(
            new RedFlagRule("呕血", "消化道出血",
                    "呕血可能提示食管胃底静脉曲张破裂等上消化道大出血，请立即前往急诊或拨打120，保持静卧、不要进食饮水。"),
            new RedFlagRule("吐血", "消化道出血",
                    "呕血可能提示上消化道大出血，请立即前往急诊或拨打120，保持静卧、不要进食饮水。"),
            new RedFlagRule("黑便", "消化道出血",
                    "柏油样黑便可能提示消化道出血，请立即前往急诊或拨打120。"),
            new RedFlagRule("大量便血", "消化道出血",
                    "大量便血属于急症，请立即前往急诊或拨打120。"),
            new RedFlagRule("意识模糊", "肝性脑病",
                    "意识模糊/嗜睡/胡言乱语可能是肝性脑病表现，请立即前往急诊或拨打120。"),
            new RedFlagRule("昏迷", "肝性脑病",
                    "意识丧失属于危急情况，请立即拨打120。"),
            new RedFlagRule("胡言乱语", "肝性脑病",
                    "精神异常、胡言乱语可能提示肝性脑病，请立即前往急诊或拨打120。"),
            new RedFlagRule("剧烈腹痛", "急腹症",
                    "剧烈且持续加重的腹痛可能是急腹症，请立即前往急诊或拨打120。"),
            new RedFlagRule("自杀", "心理危机",
                    "如果你有伤害自己的想法，请立即拨打全国心理援助热线400-161-9995或120/110，并联系身边亲友陪同。"),
            new RedFlagRule("不想活", "心理危机",
                    "如果你有伤害自己的想法，请立即拨打全国心理援助热线400-161-9995或120/110，并联系身边亲友陪同。")
    );

    /**
     * 检测用户输入是否包含急症信号
     */
    public Optional<RedFlagHit> detect(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String normalized = text.replaceAll("\\s+", "");
        for (RedFlagRule rule : RULES) {
            if (normalized.contains(rule.keyword())) {
                log.warn("命中急症红旗规则: keyword={}, category={}", rule.keyword(), rule.category());
                return Optional.of(new RedFlagHit(rule.keyword(), rule.category(), rule.advice()));
            }
        }
        return Optional.empty();
    }

    /**
     * 后置校验：模型给出的风险等级若为急症，也强制走急诊流程
     */
    public boolean isEmergencyLevel(String riskLevel) {
        return "EMERGENCY".equalsIgnoreCase(riskLevel);
    }
}
