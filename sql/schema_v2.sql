-- =====================================================================
-- 肝病 AI 初筛与病例管理小程序 - 数据库初始化脚本 v2（业务重构）
-- MySQL 8.0 / InnoDB / utf8mb4
-- 说明：旧业务（patient/doctor/visit_record/online_consultation 等）已废弃；
--       本脚本重建为「健康档案 + AI 初筛会话 + 检查报告」模型。
-- 用法：mysql -u root -p < sql/schema_v2.sql
-- =====================================================================

DROP DATABASE IF EXISTS hospital_db;
CREATE DATABASE hospital_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE hospital_db;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 微信用户（唯一登录身份；保留原结构）
-- ---------------------------------------------------------------------
CREATE TABLE wx_user (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    openid          VARCHAR(64)  DEFAULT NULL COMMENT '微信openid',
    unionid         VARCHAR(64)  DEFAULT NULL COMMENT '微信unionid',
    nick_name       VARCHAR(128) DEFAULT NULL COMMENT '昵称',
    avatar_url      VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    phone           VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '状态:0正常,1禁用',
    last_login_time DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0未删,1已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wx_user_openid (openid),
    KEY idx_wx_user_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '微信用户';

-- ---------------------------------------------------------------------
-- 2. 健康档案（本人/家属，一人可维护多份；含肝病高危因素）
-- ---------------------------------------------------------------------
CREATE TABLE health_profile (
    id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id                BIGINT       NOT NULL COMMENT '所属微信用户 wx_user.id',
    real_name              VARCHAR(64)  DEFAULT NULL COMMENT '真实姓名/称呼',
    gender                 VARCHAR(8)   NOT NULL DEFAULT '男' COMMENT '性别:男/女',
    age                    INT          DEFAULT NULL COMMENT '年龄',
    relation               VARCHAR(16)  NOT NULL DEFAULT '本人' COMMENT '与账号关系:本人/父母/子女/配偶/其他',
    hbv_carrier            TINYINT      NOT NULL DEFAULT 0 COMMENT '乙肝病毒携带/感染:0否,1是',
    hcv_carrier            TINYINT      NOT NULL DEFAULT 0 COMMENT '丙肝病毒携带/感染:0否,1是',
    cirrhosis              TINYINT      NOT NULL DEFAULT 0 COMMENT '肝硬化:0否,1是',
    fatty_liver            TINYINT      NOT NULL DEFAULT 0 COMMENT '脂肪肝:0无,1轻度,2中重度',
    diabetes               TINYINT      NOT NULL DEFAULT 0 COMMENT '糖尿病:0否,1是',
    alcohol_history        VARCHAR(128) DEFAULT NULL COMMENT '饮酒史描述(频率/年限)',
    smoking                TINYINT      NOT NULL DEFAULT 0 COMMENT '吸烟:0否,1是',
    family_liver_cancer    TINYINT      NOT NULL DEFAULT 0 COMMENT '肝癌家族史:0否,1是',
    allergy                VARCHAR(255) DEFAULT NULL COMMENT '过敏史/药物过敏',
    current_medication     VARCHAR(512) DEFAULT NULL COMMENT '当前长期用药',
    consent_flag           TINYINT      NOT NULL DEFAULT 0 COMMENT '健康信息授权:0未授权,1已授权',
    remark                 VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_hp_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '健康档案';

-- ---------------------------------------------------------------------
-- 3. AI 初筛会话
-- ---------------------------------------------------------------------
CREATE TABLE triage_session (
    id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id                BIGINT       NOT NULL COMMENT '发起用户 wx_user.id',
    profile_id             BIGINT       NOT NULL COMMENT '使用的健康档案 health_profile.id',
    chief_complaint        VARCHAR(512) NOT NULL COMMENT '主诉/初始症状描述',
    status                 VARCHAR(16)  NOT NULL DEFAULT 'ONGOING' COMMENT '状态:ONGOING进行中,FINISHED已完成,EMERGENCY急症中断',
    risk_level             VARCHAR(16)  DEFAULT NULL COMMENT '风险等级:LOW/MEDIUM/HIGH/EMERGENCY',
    possible_directions    VARCHAR(1024) DEFAULT NULL COMMENT '可能方向(谨慎措辞,多条以||分隔)',
    suggested_departments  VARCHAR(512) DEFAULT NULL COMMENT '建议就诊科室(||分隔)',
    suggested_exams        VARCHAR(1024) DEFAULT NULL COMMENT '建议检查项目(||分隔)',
    lifestyle_advice       VARCHAR(2048) DEFAULT NULL COMMENT '生活建议(||分隔)',
    conclusion             TEXT         COMMENT '总结与就医提示',
    create_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_ts_user_id (user_id),
    KEY idx_ts_profile_id (profile_id),
    KEY idx_ts_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI初筛会话';

-- ---------------------------------------------------------------------
-- 4. 初筛会话消息（多轮对话留痕）
-- ---------------------------------------------------------------------
CREATE TABLE triage_message (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id   BIGINT       NOT NULL COMMENT '会话 triage_session.id',
    role         VARCHAR(16)  NOT NULL COMMENT '角色:USER用户,ASSISTANT助手,SYSTEM系统(急症等)',
    content      TEXT         NOT NULL COMMENT '消息内容',
    options_json VARCHAR(1024) DEFAULT NULL COMMENT '快捷选项JSON数组',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_tm_session_id (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '初筛会话消息';

-- ---------------------------------------------------------------------
-- 5. 检查报告/指标记录（一期手工录入指标 + AI 解读）
-- ---------------------------------------------------------------------
CREATE TABLE medical_report (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           BIGINT       NOT NULL COMMENT '所属用户 wx_user.id',
    profile_id        BIGINT       NOT NULL COMMENT '关联档案 health_profile.id',
    report_type       VARCHAR(32)  NOT NULL DEFAULT 'OTHER' COMMENT '类型:LIVER_FUNCTION肝功能,AFP甲胎蛋白,ULTRASOUND超声,OTHER其他',
    report_date       DATE         DEFAULT NULL COMMENT '检查日期',
    indicators_json   TEXT         COMMENT '结构化指标JSON(如ALT/AST/总胆红素/AFP数值)',
    image_url         VARCHAR(512) DEFAULT NULL COMMENT '报告图片URL(二期)',
    ai_interpretation TEXT         COMMENT 'AI通俗解读',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_mr_user_id (user_id),
    KEY idx_mr_profile_id (profile_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '检查报告与指标记录';

-- ---------------------------------------------------------------------
-- 6. 急症红旗规则（代码内置硬拦截，本表供后台维护/审阅）
-- ---------------------------------------------------------------------
CREATE TABLE sys_red_flag (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    keyword     VARCHAR(64)  NOT NULL COMMENT '触发关键词',
    category    VARCHAR(32)  NOT NULL COMMENT '急症类别:消化道出血/肝性脑病/急腹症/心理危机等',
    advice      VARCHAR(512) NOT NULL COMMENT '急诊提示文案',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用:0否,1是',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_red_flag_keyword (keyword)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '急症红旗规则';

-- ---------------------------------------------------------------------
-- 7. 医院/健康公告（保留原结构）
-- ---------------------------------------------------------------------
CREATE TABLE app_notice (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title       VARCHAR(255) NOT NULL COMMENT '标题',
    content     TEXT         NOT NULL COMMENT '正文',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1展示,0隐藏',
    sort_no     INT          NOT NULL DEFAULT 0 COMMENT '排序权重(越大越靠前)',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_app_notice_status_sort (status, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '公告';

-- ---------------------------------------------------------------------
-- 8. 后台管理员（可选；不存在时代码回退 application.yml 的 admin/123456）
-- ---------------------------------------------------------------------
CREATE TABLE admin_user (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64) NOT NULL COMMENT '登录账号',
    password_hash CHAR(64)    NOT NULL COMMENT 'SHA-256(admin盐+密码)',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '后台管理员';

-- =====================================================================
-- 初始数据
-- =====================================================================
INSERT INTO sys_red_flag (keyword, category, advice) VALUES
('呕血',       '消化道出血', '呕血可能提示食管胃底静脉曲张破裂等上消化道大出血，请立即前往急诊或拨打120，保持静卧、不要进食饮水。'),
('吐血',       '消化道出血', '呕血可能提示上消化道大出血，请立即前往急诊或拨打120，保持静卧、不要进食饮水。'),
('黑便',       '消化道出血', '柏油样黑便可能提示消化道出血，请立即前往急诊或拨打120。'),
('大量便血',   '消化道出血', '大量便血属于急症，请立即前往急诊或拨打120。'),
('意识模糊',   '肝性脑病',   '意识模糊/嗜睡/胡言乱语可能是肝性脑病表现，请立即前往急诊或拨打120。'),
('昏迷',       '肝性脑病',   '意识丧失属于危急情况，请立即拨打120。'),
('胡言乱语',   '肝性脑病',   '精神异常、胡言乱语可能提示肝性脑病，请立即前往急诊或拨打120。'),
('剧烈腹痛',   '急腹症',     '剧烈且持续加重的腹痛可能是急腹症，请立即前往急诊或拨打120。'),
('自杀',       '心理危机',   '如果你有伤害自己的想法，请立即拨打全国心理援助热线400-161-9995或120/110，并联系身边亲友陪同。'),
('不想活',     '心理危机',   '如果你有伤害自己的想法，请立即拨打全国心理援助热线400-161-9995或120/110，并联系身边亲友陪同。');

INSERT INTO app_notice (title, content, status, sort_no) VALUES
('肝病早筛科普：哪些人属于肝癌高危人群', '乙肝/丙肝病毒感染者、肝硬化患者、长期饮酒者、非酒精性脂肪肝、有肝癌家族史等人群建议定期筛查。本工具仅提供健康初筛与就医建议，不能替代医生诊断。', 1, 100),
('高危人群复查提示', '肝癌高危人群一般建议每6个月复查一次血清甲胎蛋白(AFP)与肝脏超声；具体复查频率请遵医嘱。', 1, 90),
('免责声明', '本小程序提供的所有健康信息与AI初筛结果仅供参考，不构成诊断或治疗方案。若出现呕血、黑便、意识障碍等紧急情况，请立即就医或拨打120。', 1, 80);

-- =====================================================================
-- 完成。
-- =====================================================================
