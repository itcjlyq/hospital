package com.hospital.wechat.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AdminApiController {

    private final JdbcTemplate jdbcTemplate;
    private final AdminAuthorizationService authorizationService;

    public AdminApiController(JdbcTemplate jdbcTemplate, AdminAuthorizationService authorizationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
    }

    private boolean authorized(HttpServletRequest request) {
        return authorizationService.authorized(request);
    }

    @GetMapping("/admin/api/tables")
    public ResponseEntity<?> tables(HttpServletRequest request) {
        if (!authorized(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未授权"));
        }

        // 白名单：避免任意表名导致的越权查询
        List<Map<String, String>> tables = new ArrayList<>();
        tables.add(label("wx_user", "微信用户"));
        tables.add(label("health_profile", "健康档案"));
        tables.add(label("triage_session", "AI初筛会话"));
        tables.add(label("triage_message", "初筛对话消息"));
        tables.add(label("medical_report", "检查报告记录"));

        return ResponseEntity.ok(Map.of("tables", tables));
    }

    @GetMapping("/admin/api/table/{table}")
    public ResponseEntity<?> tableRows(
            @PathVariable String table,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request
    ) {
        if (!authorized(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未授权"));
        }
        if (!isAllowedTable(table)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "不允许访问的表"));
        }

        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safePageSize;

        String countSql = "SELECT COUNT(*) FROM `" + table + "`";
        Long totalObj = jdbcTemplate.queryForObject(countSql, Long.class);
        long total = totalObj == null ? 0L : totalObj;

        // 绝大多数表都带 id 字段；用 id 倒序，便于看最新数据
        String sql = "SELECT * FROM `" + table + "` ORDER BY id DESC LIMIT ? OFFSET ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, safePageSize, offset);

        return ResponseEntity.ok(Map.of(
                "table", table,
                "page", safePage,
                "pageSize", safePageSize,
                "total", total,
                "rows", rows
        ));
    }

    @GetMapping("/admin/api/table/{table}/{id}")
    public ResponseEntity<?> tableRow(
            @PathVariable String table,
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        if (!authorized(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未授权"));
        }
        if (!isAllowedTable(table)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "不允许访问的表"));
        }

        String sql = "SELECT * FROM `" + table + "` WHERE id = ? LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);
        if (rows.isEmpty()) {
            return ResponseEntity.ok(Map.of("table", table, "id", id, "row", null));
        }
        return ResponseEntity.ok(Map.of("table", table, "id", id, "row", rows.get(0)));
    }

    private boolean isAllowedTable(String table) {
        return table.equals("wx_user")
                || table.equals("health_profile")
                || table.equals("triage_session")
                || table.equals("triage_message")
                || table.equals("medical_report");
    }

    private Map<String, String> label(String name, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("label", label);
        return m;
    }
}

