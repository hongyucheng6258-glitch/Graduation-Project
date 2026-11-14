package com.campus.platform.module.auth.controller;

import com.campus.platform.common.R;
import com.campus.platform.module.admin.audit.AdminOperation;
import com.campus.platform.module.admin.audit.AdminPermission;
import com.campus.platform.module.admin.service.AdminPermissionService;
import com.campus.platform.module.auth.service.CaptchaService;
import com.campus.platform.module.auth.service.PasswordRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;
    private final AdminPermissionService permissions;
    private final CaptchaService captcha;

    public record Request(String studentNo, String name, String contact, String explanation, String captchaId, String captchaCode) {}
    public record Receipt(String receipt) {}
    public record Decision(boolean approve, String reason) {}

    @PostMapping("/api/auth/recovery/apply")
    public R<Receipt> apply(@RequestBody Request request) {
        captcha.validateAndConsume(request.captchaId(), request.captchaCode());
        return R.ok(new Receipt(recovery.submit(new PasswordRecoveryService.Application(
                request.studentNo(), request.name(), request.contact(), request.explanation()))));
    }

    @PostMapping("/api/auth/recovery/status")
    public R<PasswordRecoveryService.Status> status(@RequestBody Receipt receipt) {
        return R.ok(recovery.status(receipt.receipt()));
    }

    @PostMapping("/api/auth/recovery/claim")
    public R<Map<String, String>> claim(@RequestBody Receipt receipt, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return R.ok(Map.of("temporaryPassword", recovery.claim(receipt.receipt())));
    }

    @GetMapping("/api/admin/password-recovery")
    public R<List<Map<String, Object>>> list(@RequestParam(defaultValue = "9223372036854775807") long before) {
        permissions.require(AdminPermission.USER_PASSWORD_RESET);
        return R.ok(recovery.list(before));
    }

    @PutMapping("/api/admin/password-recovery/{id}")
    @AdminOperation(permission = AdminPermission.USER_PASSWORD_RESET, module = "password-recovery", action = "review", idArgument = 0)
    public R<Void> decide(@PathVariable Long id, @RequestBody Decision decision) {
        recovery.decide(id, decision.approve(), decision.reason());
        return R.ok();
    }
}
