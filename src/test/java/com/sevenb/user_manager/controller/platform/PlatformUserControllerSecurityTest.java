package com.sevenb.user_manager.controller.platform;

import com.sevenb.user_manager.dto.UserCreateRequest;
import com.sevenb.user_manager.repository.UserRepository;
import com.sevenb.user_manager.security.JwtService;
import com.sevenb.user_manager.security.PermissionGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PlatformUserControllerSecurityTest {

    private final PermissionGuard guard = new PermissionGuard(true, mock(UserRepository.class));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void classLevelRequiresPlatformAdmin() {
        PreAuthorize annotation = PlatformUserController.class.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("@perm.isPlatformAdmin()");
    }

    @Test
    void platformAdminAllowed() {
        authenticate("CAJERO", List.of("PLATFORM_ADMIN"));
        assertThat(allows()).isTrue();
    }

    @Test
    void tenantAdminWithoutPlatformDenied() {
        authenticate("ADMIN", List.of("USERS_MANAGE"));
        assertThat(allows()).isFalse();
    }

    @Test
    void createUserMethodInheritsClassGuard() throws Exception {
        authenticate("ADMIN", List.of());
        String expression = PlatformUserController.class
                .getMethod("createUser", String.class, UserCreateRequest.class)
                .getDeclaringClass()
                .getAnnotation(PreAuthorize.class)
                .value();
        assertThat(allows(expression)).isFalse();
    }

    private void authenticate(String role, List<String> permissions) {
        JwtService.JwtClaims claims = new JwtService.JwtClaims(
                "tenant-admin", 1L, 1L, "ops", role, "RI", permissions);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(claims, null, List.of()));
    }

    private boolean allows() {
        return allows(PlatformUserController.class.getAnnotation(PreAuthorize.class).value());
    }

    private boolean allows(String expression) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setBeanResolver((ctx, name) -> guard);
        return Boolean.TRUE.equals(
                new SpelExpressionParser().parseExpression(expression).getValue(context, Boolean.class));
    }
}
