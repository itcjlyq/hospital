package com.hospital.wechat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 检查报告/指标记录
 */
@Data
@TableName("medical_report")
public class MedicalReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 关联档案 */
    private Long profileId;

    /** LIVER_FUNCTION/AFP/ULTRASOUND/OTHER */
    private String reportType;

    /** 检查日期 */
    private LocalDate reportDate;

    /** 结构化指标 JSON */
    private String indicatorsJson;

    /** 报告图片URL（二期） */
    private String imageUrl;

    /** AI 通俗解读 */
    private String aiInterpretation;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
