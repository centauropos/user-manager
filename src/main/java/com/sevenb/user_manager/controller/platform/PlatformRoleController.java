package com.sevenb.user_manager.controller.platform;

import com.sevenb.user_manager.dto.RoleResponseDto;
import com.sevenb.user_manager.service.PlatformUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform/tenants/{tenantId}/roles")
@PreAuthorize("@perm.isPlatformAdmin()")
public class PlatformRoleController {

    private final PlatformUserService platformUserService;

    public PlatformRoleController(PlatformUserService platformUserService) {
        this.platformUserService = platformUserService;
    }

    @GetMapping
    public List<RoleResponseDto> listRoles(@PathVariable String tenantId) {
        return platformUserService.listRoles(tenantId);
    }
}
