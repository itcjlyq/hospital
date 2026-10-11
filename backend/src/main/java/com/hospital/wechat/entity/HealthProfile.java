package com.hospital.wechat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 健康档案（本人/家属，含肝病高危因素）
 */
@Data
@TableName("health_profile")
public class HealthProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属微信用户 */
    private Long userId;

    /** 姓名/称呼 */
    private String realName;

    /** 性别:男/女 */
    private String gender;

    /** 年龄 */
    private Integer age;

    /** 关系:本人/父母/子女/配偶/其他 */
    private String relation;

    /** 乙肝病毒携带/感染:0否,1是 */
    private Integer hbvCarrier;

    /** 丙肝病毒携带/感染:0否,1是 */
    private Integer hcvCarrier;

    /** 肝硬化:0否,1是 */
    private Integer cirrhosis;

    /** 脂肪肝:0无,1轻度,2中重度 */
    private Integer fattyLiver;

    /** 糖尿病:0否,1是 */
    private Integer diabetes;

    /** 饮酒史描述 */
    private String alcoholHistory;

    /** 吸烟:0否,1是 */
    private Integer smoking;

    /** 肝癌家族史:0否,1是 */
    private Integer familyLiverCancer;

    /** 过敏史/药物过敏 */
    private String allergy;

    /** 当前长期用药 */
    private String currentMedication;

    /** 健康信息授权:0未授权,1已授权 */
    private Integer consentFlag;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
