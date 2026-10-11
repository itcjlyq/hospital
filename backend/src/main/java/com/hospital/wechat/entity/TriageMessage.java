package com.hospital.wechat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 初筛会话消息
 */
@Data
@TableName("triage_message")
public class TriageMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private Long sessionId;

    /** USER/ASSISTANT/SYSTEM */
    private String role;

    /** 内容 */
    private String content;

    /** 快捷选项 JSON 数组 */
    private String optionsJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
