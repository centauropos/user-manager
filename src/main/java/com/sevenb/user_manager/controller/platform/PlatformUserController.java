package com.sevenb.user_manager.controller.platform;

import com.sevenb.user_manager.dto.EnabledRequest;
import com.sevenb.user_manager.dto.PlatformUserUpdateRequest;
import com.sevenb.user_manager.dto.ResetPasswordRequest;
import com.sevenb.user_manager.dto.UserCreateRequest;
import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.dto.UserRoleDto;
import com.sevenb.user_manager.service.PlatformUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform/tenants/{tenantId}/users")
@PreAuthorize("@perm.isPlatformAdmin()")
public class PlatformUserController {

    private final PlatformUserService platformUserService;

    public PlatformUserController(PlatformUserService platformUserService) {
        this.platformUserService = platformUserService;
    }

    @GetMapping
    public List<UserResponseDto> listUsers(@PathVariable String tenantId) {
        return platformUserService.listUsers(tenantId);
    }

    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(
            @PathVariable String tenantId,
            @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(platformUserService.createUser(tenantId, request));
    }

    @GetMapping("/{userId}")
    public UserResponseDto getUser(@PathVariable String tenantId, @PathVariable Long userId) {
        return platformUserService.getUser(tenantId, userId);
    }

    @PutMapping("/{userId}")
    public UserResponseDto updateUser(
            @PathVariable String tenantId,
            @PathVariable Long userId,
            @RequestBody PlatformUserUpdateRequest request) {
        return platformUserService.updateUser(tenantId, userId, request);
    }

    @PostMapping("/{userId}/reset-password")
    public UserResponseDto resetPassword(
            @PathVariable String tenantId,
            @PathVariable Long userId,
            @RequestBody ResetPasswordRequest request) {
        return platformUserService.resetPassword(tenantId, userId, request.getPassword());
    }

    @PutMapping("/{userId}/enabled")
    public UserResponseDto setEnabled(
            @PathVariable String tenantId,
            @PathVariable Long userId,
            @RequestBody EnabledRequest request) {
        return platformUserService.setEnabled(tenantId, userId, request.isEnabled());
    }

    @PutMapping("/{userId}/role")
    public UserResponseDto assignRole(
            @PathVariable String tenantId,
            @PathVariable Long userId,
            @RequestBody UserRoleDto dto) {
        return platformUserService.assignRole(tenantId, userId, dto.getRoleId());
    }
}
