package com.campus.platform.module.auth.controller;

import com.campus.platform.common.BizException;
import com.campus.platform.common.R;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.auth.service.PhoneAuthService;
import com.campus.platform.module.auth.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PhoneAuthController {
    private final PhoneAuthService phones;

    public record Input(String phone, String purpose, String code, String password) {}

    @GetMapping("/api/auth/phone/options")
    public R<Map<String, Boolean>> options() {
        return R.ok(Map.of("enabled", phones.available()));
    }

    @PostMapping("/api/auth/phone/send")
    public R<PhoneAuthService.Delivery> send(@RequestBody Input input) {
        if (!"login".equals(input.purpose()) && !"reset".equals(input.purpose())) {
            throw new BizException(ResultCode.BAD_REQUEST, "验证码用途无效");
        }
        return R.ok(phones.send(input.purpose(), input.phone(), null));
    }

    @PostMapping("/api/auth/phone/login")
    public R<LoginVO> login(@RequestBody Input input) {
        return R.ok(phones.login(input.phone(), input.code()));
    }

    @PostMapping("/api/auth/phone/reset")
    public R<Void> reset(@RequestBody Input input) {
        phones.reset(input.phone(), input.code(), input.password());
        return R.ok();
    }

    @GetMapping("/api/user/verified-phone")
    public R<PhoneAuthService.Binding> binding() {
        return R.ok(phones.binding(UserContext.getUid()));
    }

    @PostMapping("/api/user/verified-phone/send")
    public R<PhoneAuthService.Delivery> sendBinding(@RequestBody Input input) {
        return R.ok(phones.send("bind", input.phone(), UserContext.getUid()));
    }

    @PutMapping("/api/user/verified-phone")
    public R<PhoneAuthService.Binding> bind(@RequestBody Input input) {
        return R.ok(phones.bind(UserContext.getUid(), input.phone(), input.code()));
    }
}
