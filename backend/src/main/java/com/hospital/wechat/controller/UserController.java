package com.hospital.wechat.controller;

import com.hospital.wechat.dto.Result;
import com.hospital.wechat.dto.UpdateUserProfileRequest;
import com.hospital.wechat.dto.UserProfileResponse;
import com.hospital.wechat.entity.WxUser;
import com.hospital.wechat.entity.WxUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 当前微信用户信息
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final WxUserMapper wxUserMapper;

    @GetMapping("/me")
    public Result<UserProfileResponse> getCurrentUser(@NonNull HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }
        WxUser user = wxUserMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        return Result.success(toResponse(user, request));
    }

    @PutMapping("/me")
    public Result<UserProfileResponse> updateCurrentUser(@RequestBody UpdateUserProfileRequest updateRequest,
                                                         @NonNull HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }
        WxUser user = wxUserMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        if (updateRequest.getNickName() != null && !updateRequest.getNickName().isBlank()) {
            user.setNickName(updateRequest.getNickName().trim());
        }
        if (updateRequest.getPhone() != null) {
            user.setPhone(updateRequest.getPhone().trim());
        }
        if (updateRequest.getAvatarUrl() != null) {
            user.setAvatarUrl(updateRequest.getAvatarUrl().trim());
        }

        wxUserMapper.updateById(user);
        return Result.success(toResponse(user, request));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UserProfileResponse> uploadAvatar(MultipartFile file, @NonNull HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }
        if (file == null || file.isEmpty()) {
            return Result.fail("请先选择图片");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.fail("仅支持图片文件");
        }

        WxUser user = wxUserMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        try {
            Path uploadDir = resolveUploadDir();
            Files.createDirectories(uploadDir);

            String ext = getExtension(file.getOriginalFilename());
            String fileName = "avatar_" + userId + "_" + UUID.randomUUID().toString().replace("-", "") + ext;
            Path target = uploadDir.resolve(fileName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            user.setAvatarUrl("/upload/" + fileName);
            wxUserMapper.updateById(user);
            return Result.success(toResponse(user, request));
        } catch (IOException e) {
            return Result.fail("头像上传失败，请稍后重试");
        }
    }

    private UserProfileResponse toResponse(WxUser user, HttpServletRequest request) {
        String avatarUrl = buildAvatarUrl(user.getAvatarUrl(), request);
        return UserProfileResponse.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .nickName(user.getNickName())
                .avatarUrl(avatarUrl)
                .phone(user.getPhone())
                .build();
    }

    private Path resolveUploadDir() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path root = current.getFileName() != null && "backend".equalsIgnoreCase(current.getFileName().toString())
                ? current.getParent()
                : current;
        return root.resolve("upload");
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        String ext = filename.substring(filename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        if (ext.length() > 6) {
            return ".jpg";
        }
        return ext;
    }

    private String buildAvatarUrl(String avatarPath, HttpServletRequest request) {
        if (avatarPath == null || avatarPath.isBlank()) {
            return avatarPath;
        }
        if (avatarPath.startsWith("http://") || avatarPath.startsWith("https://")) {
            return avatarPath;
        }
        HttpServletRequest safeRequest = Objects.requireNonNull(request);
        return ServletUriComponentsBuilder.fromRequestUri(safeRequest)
                .replacePath(avatarPath)
                .replaceQuery(null)
                .build()
                .toUriString();
    }
}
