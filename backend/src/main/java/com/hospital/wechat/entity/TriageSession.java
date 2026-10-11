package com.hospital.wechat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 初筛会话
 */
@Data
@TableName("triage_session")
public class TriageSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起用户 */
    private Long userId;

    /** 使用的健康档案 */
    private Long profileId;

    /** 主诉 */
    private String chiefComplaint;

    /** ONGOING/FINISHED/EMERGENCY */
    private String status;

    /** LOW/MEDIUM/HIGH/EMERGENCY */
    private String riskLevel;

    /** 可能方向（||分隔） */
    private String possibleDirections;

    /** 建议科室（||分隔） */
    private String suggestedDepartments;

    /** 建议检查（||分隔） */
    private String suggestedExams;

    /** 生活建议（||分隔） */
    private String lifestyleAdvice;

    /** 总结与就医提示 */
    private String conclusion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
