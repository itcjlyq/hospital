-- =====================================================================
-- 医院微信小程序 - 完整数据库初始化脚本（本地重建）
-- MySQL 8.0 / 引擎 InnoDB / 字符集 utf8mb4
-- 说明：原远程库(8.148.155.226:3307)失效，本脚本依据后端实体类与
--       JdbcTemplate 原生 SQL 反向整理，可一次性重建全部表结构。
-- 用法：mysql -u root -p < sql/schema.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS hospital_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE hospital_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 1. 微信用户表（小程序微信登录主体）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS wx_user;
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
-- 2. 患者账号表（手机号+密码登录主体；与 wx_user 相互独立）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS patient;
CREATE TABLE patient (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    phone             VARCHAR(20)  DEFAULT NULL COMMENT '手机号(登录账号)',
    nick_name         VARCHAR(128) DEFAULT NULL COMMENT '昵称',
    avatar_url        VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    status            TINYINT      NOT NULL DEFAULT 0 COMMENT '状态:0正常,1禁用',
    password_hash     CHAR(64)     DEFAULT NULL COMMENT 'SHA-256(盐+密码)',
    primary_doctor_id BIGINT       DEFAULT NULL COMMENT '主治医生ID',
    last_login_time   DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_patient_phone (phone),
    KEY idx_patient_primary_doctor_id (primary_doctor_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '患者账号';

-- ---------------------------------------------------------------------
-- 3. 患者档案表（一个登录用户可维护多条就诊人档案）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS patient_record;
CREATE TABLE patient_record (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    uploader_user_id    BIGINT       DEFAULT NULL COMMENT '建档人 wx_user.id',
    uploader_patient_id BIGINT       DEFAULT NULL COMMENT '建档人 patient.id（如有）',
    patient_name        VARCHAR(128) DEFAULT NULL COMMENT '患者姓名',
    gender              VARCHAR(16)  DEFAULT NULL COMMENT '性别',
    age                 INT          DEFAULT NULL COMMENT '年龄',
    phone               VARCHAR(20)  DEFAULT NULL COMMENT '患者手机号',
    disease_type        VARCHAR(128) DEFAULT NULL COMMENT '疾病类型',
    id_card             VARCHAR(32)  DEFAULT NULL COMMENT '身份证号',
    remark              VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_pr_uploader_user_id (uploader_user_id),
    KEY idx_pr_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '患者档案';

-- ---------------------------------------------------------------------
-- 4. 医生表（验证码登录；后台可维护 password_hash）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS doctor;
CREATE TABLE doctor (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    phone           VARCHAR(20)  DEFAULT NULL COMMENT '手机号(登录账号)',
    doctor_name     VARCHAR(128) DEFAULT NULL COMMENT '医生姓名',
    doctor_level    VARCHAR(32)  DEFAULT NULL COMMENT '医生级别(专家/普通等)',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '状态:0正常,1禁用',
    password_hash   CHAR(64)     DEFAULT NULL COMMENT 'SHA-256(盐+密码),可空',
    last_login_time DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_doctor_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '医生';

-- ---------------------------------------------------------------------
-- 5. 线下看诊记录表（在线问诊仅取 doctor_level='专家' 的记录）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS visit_record;
CREATE TABLE visit_record (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    patient_record_id BIGINT       DEFAULT NULL COMMENT '患者档案 patient_record.id',
    patient_user_id   BIGINT       DEFAULT NULL COMMENT '归属微信用户 wx_user.id',
    patient_id        BIGINT       DEFAULT NULL COMMENT '归属患者账号 patient.id',
    doctor_name       VARCHAR(128) DEFAULT NULL COMMENT '医生姓名',
    doctor_level      VARCHAR(32)  DEFAULT NULL COMMENT '医生级别(专家/普通等)',
    doctor_id         BIGINT       DEFAULT NULL COMMENT '医生 doctor.id',
    diagnosis_report  TEXT         COMMENT '诊断报告',
    visit_time        DATETIME     DEFAULT NULL COMMENT '看诊时间',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_vr_patient_record_id (patient_record_id),
    KEY idx_vr_patient_user_id (patient_user_id),
    KEY idx_vr_doctor_level (doctor_level),
    KEY idx_vr_doctor_id (doctor_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '线下看诊记录';

-- ---------------------------------------------------------------------
-- 6. 在线问诊表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS online_consultation;
CREATE TABLE online_consultation (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    visit_record_id BIGINT       DEFAULT NULL COMMENT '关联看诊记录 visit_record.id',
    patient_user_id BIGINT       DEFAULT NULL COMMENT '发起微信用户 wx_user.id',
    patient_id      BIGINT       DEFAULT NULL COMMENT '患者账号 patient.id',
    doctor_id       BIGINT       DEFAULT NULL COMMENT '回复医生 doctor.id',
    scar_image_url  VARCHAR(512) DEFAULT NULL COMMENT '疤痕图片URL',
    patient_message TEXT         COMMENT '患者留言',
    doctor_reply    TEXT         COMMENT '医生回复',
    status          VARCHAR(32)  NOT NULL DEFAULT '待诊断' COMMENT '状态:待诊断/已回复',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_oc_visit_record_id (visit_record_id),
    KEY idx_oc_patient_user_id (patient_user_id),
    KEY idx_oc_doctor_id (doctor_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '在线问诊';

-- ---------------------------------------------------------------------
-- 7. 医院公告表（小程序首页 + 后台公告管理）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS app_notice;
CREATE TABLE app_notice (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title       VARCHAR(255) NOT NULL COMMENT '标题',
    content     TEXT         NOT NULL COMMENT '正文',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1展示,0隐藏',
    sort_no     INT          NOT NULL DEFAULT 0 COMMENT '排序权重(越大越靠前)',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_app_notice_status_sort (status, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '医院公告';

-- ---------------------------------------------------------------------
-- 8. 后台管理员表（可选；不存在时代码回退 application.yml 中 admin/123456）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS admin_user;
CREATE TABLE admin_user (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64) NOT NULL COMMENT '登录账号',
    password_hash CHAR(64)    NOT NULL COMMENT 'SHA-256(admin盐+密码)',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '后台管理员';

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 初始演示数据：医院公告（首页立即可见）
-- =====================================================================
INSERT INTO app_notice (title, content, status, sort_no) VALUES
('欢迎使用本院互联网医院小程序', '您可在小程序内维护患者档案、查看专家诊断报告并发起在线问诊。', 1, 100),
('门诊就诊温馨提示', '请携带有效身份证件就诊；在线问诊前请先查看对应专家看诊的诊断报告。', 1, 90);

-- =====================================================================
-- 完成。管理员后台登录：admin / 123456（由 application.yml 配置回退）。
-- 患者/医生账号在首次登录时由后端自动创建（患者默认密码为手机号后4位）。
-- =====================================================================
