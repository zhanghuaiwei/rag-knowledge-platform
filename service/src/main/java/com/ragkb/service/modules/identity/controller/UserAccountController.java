package com.ragkb.service.modules.identity.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.identity.dto.CreateLocalUserRequest;
import com.ragkb.service.modules.identity.dto.ResetPasswordRequest;
import com.ragkb.service.modules.identity.dto.RoleSetRequest;
import com.ragkb.service.modules.identity.dto.UpdateUserOrgRequest;
import com.ragkb.service.modules.identity.service.UserAccountService;
import com.ragkb.service.modules.identity.vo.UserVo;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户成员账号管理接口入口（管理中心用户管理）。业务实现见 {@link UserAccountService}。
 *
 * <p>类级 {@code tenant-member:manage} 权限码门禁（唯一持有人 TENANT_ADMIN，
 * 角色→权限由 PermissionCatalog 服务端聚合）；校验失败返回 403。
 * 用户为全局身份，所有操作限定当前激活租户（服务端从 JWT 推导，不信任客户端自报）。
 */
@RestController
@PreAuthorize("hasAuthority('tenant-member:manage')")
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class UserAccountController {

    private final UserAccountService userAccountService;

    public UserAccountController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/api/v1/users")
    public ApiResponse<PageData<UserVo>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(userAccountService.listUsers(page, size));
    }

    @PostMapping("/api/v1/users")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserVo> createLocalUser(@Valid @RequestBody CreateLocalUserRequest request) {
        return ApiResponse.ok(userAccountService.createLocalUser(request));
    }

    @PostMapping("/api/v1/users/{userId}/disable")
    public ApiResponse<UserVo> disableUser(@PathVariable long userId) {
        return ApiResponse.ok(userAccountService.disableUser(userId));
    }

    @PostMapping("/api/v1/users/{userId}/enable")
    public ApiResponse<UserVo> enableUser(@PathVariable long userId) {
        return ApiResponse.ok(userAccountService.enableUser(userId));
    }

    @PatchMapping("/api/v1/users/{userId}/org")
    public ApiResponse<UserVo> updateUserOrg(@PathVariable long userId,
                                             @RequestBody UpdateUserOrgRequest request) {
        return ApiResponse.ok(userAccountService.updateUserOrg(userId, request.orgId()));
    }

    @PutMapping("/api/v1/users/{userId}/roles")
    public ApiResponse<UserVo> setRoles(@PathVariable long userId, @Valid @RequestBody RoleSetRequest request) {
        return ApiResponse.ok(userAccountService.setRoles(userId, request.roles()));
    }

    @PostMapping("/api/v1/users/{userId}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable long userId,
                                              @Valid @RequestBody ResetPasswordRequest request) {
        userAccountService.resetPassword(userId, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/users/{userId}")
    public ResponseEntity<Void> removeFromTenant(@PathVariable long userId) {
        userAccountService.removeFromTenant(userId);
        return ResponseEntity.noContent().build();
    }
}
