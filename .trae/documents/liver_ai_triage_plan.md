# 肝病 AI 初筛与病例管理小程序 — 重设计实施计划

## 一、Repository Research（现状结论）

### 已核实的可复用底座
- 后端：Spring Boot 3.2 + Java 17 + MyBatis-Plus 3.5 + MySQL 8，工程结构完整，本机已可运行（端口 8080，profile=local）。
- 鉴权：JWT（`JwtUtil` / `JwtAuthFilter`），白名单机制可直接复用。
- 微信登录：`WxLoginController` + mock 登录开关（`__DEVTOOLS_MOCK_PHONE__`），演示期够用。
- 文件上传：本地上传机制（`upload/` + `/upload/**` 静态映射）可复用为报告图片存储。
- 公告：`app_notice` 表 + 小程序/后台双端接口，保留用于肝病科普公告。
- 管理后台：内嵌静态 SPA + `Admin*Controller`（公告/用户只读能力），一期不强改。
- 小程序：`utils/request.js`（统一 Token/401）、tabBar、登录页可复用。

### 需要废弃的旧业务（本次推翻重做）
- 业务表：`patient`、`patient_record`、`doctor`、`visit_record`、`online_consultation`。
- 后端：`AuthController`/`AuthServiceImpl`（患者密码登录、医生验证码登录）、`PatientController`、`OnlineConsultationController`、`admin` 包中的患者/医生/诊断管理控制器及其 DTO，相关 entity/mapper。
- 小程序页面：`pages/patient/*`、`pages/upload/*`、`pages/consult/*`、`pages/doctor/*`、`pages/chat/*` 及对应 tabBar 配置。

### 已识别且本次一并消除的旧设计缺陷
- `JwtAuthFilter` 用 patientId/doctorId 顶替 `userId` 造成跨表 ID 碰撞——新模型只有一套登录身份（`wx_user`），问题自然消失。
- 数据库结构无权威来源（旧脚本只剩增量）——本次产出新的权威 `schema.sql`。

## 二、产品设计（一期范围）

**定位（合规口径）**：肝病方向的"健康初筛与智能导诊 + 个人病例管理"工具。输出为风险分级、就诊建议、检查建议，**不输出确诊结论、不推荐处方、不指导停药换药**。

### 一期用户闭环（演示主链路）
1. 微信登录（mock 可演示）→ 首启免责声明与健康信息授权确认。
2. 建立/选择健康档案（本人或家属，含肝病高危因素）。
3. 发起 AI 初筛：主诉（可文字描述）→ DeepSeek 多轮追问（快捷选项 + 自由输入）→ 3~6 轮收敛。
4. 输出结构化初筛报告：风险等级（低/中/高/急症）、可能方向（谨慎措辞）、建议科室、建议检查（肝功能/AFP/腹部超声等）、生活建议、复查提示。
5. 急症红旗硬拦截（呕血黑便、意识障碍、剧烈腹痛等）→ 立即中止并展示急诊提示（拨打 120）。
6. 初筛历史与报告回看；检查指标手工录入与 AI 解读（简版，一期文字指标）。

### 二期（仅规划，不实施）
报告照片 OCR/多模态解读、医学知识库 RAG、复查到期提醒、科普内容库；医生端与人工转介（需医疗机构资质后再做）。

## 三、数据模型（新 5 张业务表 + 保留 3 张）

保留：`wx_user`（唯一登录身份）、`app_notice`、`admin_user`。

新增：
1. **health_profile 健康档案**：`id, user_id, real_name, gender, age, relation(本人/父母/子女/其他), hbv_carrier, hcv_carrier, cirrhosis, fatty_liver, diabetes, alcohol_history(饮酒史文本), smoking, family_liver_cancer, allergy, current_medication, consent_flag, remark, create_time, update_time`
2. **triage_session 初筛会话**：`id, user_id, profile_id, chief_complaint(主诉), status(进行中/已完成/急症中断), risk_level(LOW/MEDIUM/HIGH/EMERGENCY), possible_directions, suggested_departments, suggested_exams, lifestyle_advice, conclusion, create_time, update_time`
3. **triage_message 会话消息**：`id, session_id, role(USER/ASSISTANT/SYSTEM), content, options_json(快捷选项), create_time`
4. **medical_report 检查报告/指标记录**：`id, user_id, profile_id, report_type(LIVER_FUNCTION/AFP/ULTRASOUND/OTHER), report_date, indicators_json(ALT/AST/胆红素/AFP 等), image_url, ai_interpretation, create_time`
5. **sys_red_flag 红旗规则表**（可选，规则也在代码内置；表用于后台维护关键词，一期可只建表内置数据）。

产出新的权威脚本 `sql/schema_v2.sql`（重建库；旧 `schema.sql` 保留作历史，或在同目录归档说明）。

## 四、Files and Modules

### 后端（backend/）
- `resources/application-local.yml`：**修改**——增加 `deepseek.api-key`（从环境变量 `DEEPSEEK_API_KEY` 读取，不写死、不提交）、`deepseek.base-url=https://api.deepseek.com`、`deepseek.model=deepseek-chat`。
- 新增 `config/DeepSeekProperties.java`：配置绑定。
- 新增 `ai/DeepSeekClient.java`：HTTP 调用（用 JDK HttpClient 或项目已有的 httpclient5）、超时、错误降级。
- 新增 `ai/RedFlagGuard.java`：红旗规则前置/后置拦截（硬编码肝病急症关键词 + risk_level 兜底）。
- 新增 `ai/LiverTriagePromptService.java`：system prompt 组装、DeepSeek JSON 输出协议、档案上下文注入、输出校验与改写（拦截"确诊/处方"等措辞）。
- 新增 entity/mapper：`HealthProfile(+Mapper)`、`TriageSession(+Mapper)`、`TriageMessage(+Mapper)`、`MedicalReport(+Mapper)`。
- 新增 `controller/HealthProfileController.java`：`/api/profile` CRUD（按 userId 隔离）。
- 新增 `controller/TriageController.java`：
  - `POST /api/triage/sessions`（选档案+主诉，建会话）
  - `POST /api/triage/sessions/{id}/messages`（发消息→红旗检查→调模型→落库→返回回复/选项/是否完成/结论）
  - `GET /api/triage/sessions`、`GET /api/triage/sessions/{id}`
- 新增 `controller/MedicalReportController.java`：`POST /api/report/interpret`（指标→解读）、`GET /api/report`。
- 删除：`PatientController`、`OnlineConsultationController`、`AuthController` + `service/AuthService` + `service/impl/AuthServiceImpl`、`admin/Admin{Patient,Doctor,Diagnosis}Controller.java` 及关联 DTO、废弃 entity（`Patient/PatientRecord/Doctor/VisitRecord/OnlineConsultation` 及 Mapper）。
- 修改 `config/JwtAuthFilter.java`：白名单更新（移除 `/api/auth/`）；移除 patient/doctor ID 兼容逻辑。
- 管理后台前端静态包一期保持现状（旧入口点击可能报错，列入二期清理），公告功能正常。

### 小程序（miniprogram/）
- 删除：`pages/patient`、`pages/upload`、`pages/consult`、`pages/doctor`、`pages/chat` 及无用图片。
- 新增页面：
  - `pages/home/index`：初筛入口、档案快捷选择、科普公告、历史入口。
  - `pages/profile/list`、`pages/profile/edit`：健康档案列表/编辑（肝病高危因素表单）。
  - `pages/triage/chat`：多轮问诊对话页（气泡 + 快捷选项 + 输入框 + loading）。
  - `pages/triage/report`：结构化初筛报告页（风险等级色标、科室/检查建议、急症强提示、免责声明）。
  - `pages/report/index`：检查指标录入与解读结果（ALT/AST/总胆红素/AFP 等表单）。
  - `pages/history/list`、`pages/history/detail`：初筛历史列表/详情。
  - `pages/agreement/notice`：免责声明与授权页。
- 修改：`app.json`（页面注册、tabBar 改为：首页/历史/我的）、`custom-tab-bar`（移除医生角色分支）、登录成功后的落地页。
- `utils/` 新增 `ai-api.js`（初筛/档案/报告接口封装）。

### 数据库
- 新建 `sql/schema_v2.sql`：建库 + 8 张表（保留 3 + 新增 5）+ 红旗规则初始数据 + 2 条肝病科普公告。
- 本地 MySQL 已就绪（root/123456），直接导入执行。

## 五、Implementation Steps（依赖顺序）

1. **数据库重建**：编写并导入 `sql/schema_v2.sql`，验证表结构。
2. **后端清理**：删除旧业务 controller/service/entity/dto，收窄过滤器白名单，确保工程可编译。
3. **DeepSeek 接入**：配置类 + `DeepSeekClient`（key 走环境变量）；先用一个最小问答接口在本地联调真实 key。
4. **初筛内核**：prompt 协议（固定 JSON schema）+ `RedFlagGuard` + 会话/消息落库；完成 `TriageController` 三个接口，用 curl/接口工具验证多轮与急症拦截。
5. **健康档案 + 报告解读接口**：CRUD 与指标解读接口。
6. **小程序改造**：删旧页面 → 调整 app.json/tabBar → 依次实现 档案→初筛对话→报告页→历史→指标录入→免责页。
7. **端到端联调**：mock 登录走完演示主链路；再接真实 DeepSeek 验证问答质量与 JSON 稳定性。
8. **管理后台**：仅保留公告；旧菜单不可用部分在文档中标注（二期重做后台）。

## 六、Dependencies and Considerations

- **DeepSeek 调用协议**：`POST https://api.deepseek.com/chat/completions`（OpenAI 兼容），`model=deepseek-chat`；用 `response_format=json_object` 强制 JSON；超时 30s；后端统一捕获异常并降级为"服务繁忙/信息不足，请线下就诊"。
- **密钥安全**：key 仅存于本机环境变量 `DEEPSEEK_API_KEY` 与未入库的本地配置；仓库配置用占位符 `${DEEPSEEK_API_KEY:}`。
- **医疗合规（演示也照做，为上线留口）**：
  - 首启授权弹窗 + 每页免责口径；所有结论使用"可能与……有关，建议就医评估"，禁止确诊措辞；
  - 急症关键词硬拦截 + 模型 risk_level=EMERGENCY 双重触发急诊模板；
  - 小程序正式上线前需接 `security.msgSecCheck` 文本审核（一期预留调用点，演示可开关）。
- **JSON 稳定性**：DeepSeek 偶发不返回合法 JSON 时做一次"修复重试"（在 user message 中要求严格按 schema），仍失败则降级提示。
- **对话为同步请求**：一期不用流式（小程序对 SSE 支持差），每轮显示 loading；二期再评估流式。
- **档案归属**：所有业务表以 `user_id`（wx_user）隔离；一个用户可维护多份家属档案。
- 历史数据：本地为演示库，直接重建，无需迁移。

## 七、Validation

- 编译与启动：`mvn spring-boot:run "-Dspring-boot.run.profiles=local"` 无报错，8080 健康检查通过。
- 接口层：建档 → 建会话 → 连续追问 → 完成报告（risk_level/departments/exams 字段完整）；构造"呕血/意识模糊"输入验证急症拦截；构造模型异常验证降级。
- 数据层：会话与消息完整落库、历史列表按用户隔离、越权访问他人档案返回无权限。
- 小程序端：微信开发者工具走通登录→建档→初筛→看报告→历史的完整链路；红旗急症展示急诊页。
- 合规检查：全产品文案无"诊断/确诊/处方"越界表述，免责声明可见。

## 八、Risks

- **模型输出越界或失准**：prompt 约束 + JSON 协议 + 后置措辞拦截 + 免责声明；演示数据中保留典型好/坏案例用于回归。
- **DeepSeek 服务波动**：超时/限流降级文案，避免把错误抛给用户。
- **医疗误导**：红旗规则前置是最高优先级，宁可误判为急症也不漏；产品始终给出线下就医出口。
- **范围蔓延**：医生端、报告图片 OCR、RAG、后台重做一律二期；一期以演示主链路完整为验收标准。
- **后台旧入口报错**：静态包中患者/医生/诊断菜单仍调旧接口；一期文档标注"已停用"，不阻塞小程序演示。
