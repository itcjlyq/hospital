package com.hospital.wechat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.wechat.dto.Result;
import com.hospital.wechat.entity.HealthProfile;
import com.hospital.wechat.entity.HealthProfileMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 健康档案（本人/家属）
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class HealthProfileController {

    private final HealthProfileMapper profileMapper;

    @GetMapping
    public Result<List<HealthProfile>> list(@NonNull HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<HealthProfile> profiles = profileMapper.selectList(new LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getUserId, userId)
                .orderByAsc(HealthProfile::getId));
        return Result.success(profiles);
    }

    @GetMapping("/{id}")
    public Result<HealthProfile> detail(@PathVariable Long id, @NonNull HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        HealthProfile profile = getOwned(id, userId);
        return Result.success(profile);
    }

    @PostMapping
    public Result<HealthProfile> create(@RequestBody HealthProfile body, @NonNull HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        validate(body);

        body.setId(null);
        body.setUserId(userId);
        body.setConsentFlag(1);
        profileMapper.insert(body);
        return Result.success(body);
    }

    @PutMapping("/{id}")
    public Result<HealthProfile> update(@PathVariable Long id,
                                        @RequestBody HealthProfile body,
                                        @NonNull HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        HealthProfile existing = getOwned(id, userId);
        validate(body);

        body.setId(existing.getId());
        body.setUserId(userId);
        body.setConsentFlag(existing.getConsentFlag() == null ? 1 : existing.getConsentFlag());
        profileMapper.updateById(body);
        return Result.success(profileMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @NonNull HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        getOwned(id, userId);
        profileMapper.deleteById(id);
        return Result.success();
    }

    private HealthProfile getOwned(Long id, Long userId) {
        HealthProfile profile = profileMapper.selectById(id);
        if (profile == null || !userId.equals(profile.getUserId())) {
            throw new IllegalArgumentException("档案不存在");
        }
        return profile;
    }

    private void validate(HealthProfile body) {
        if (body.getRealName() == null || body.getRealName().isBlank()) {
            throw new IllegalArgumentException("请填写姓名或称呼");
        }
        if (body.getAge() != null && (body.getAge() < 0 || body.getAge() > 120)) {
            throw new IllegalArgumentException("年龄不合法");
        }
    }
}
