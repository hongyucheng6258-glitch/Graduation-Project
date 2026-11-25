package com.campus.platform.module.auth.controller;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Result;
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
    public Result<Map<String, Boolean>> options() {
        return Result.ok(Map.of("enabled", phones.available()));
    }

    @PostMapping("/api/auth/phone/send")
    public Result<PhoneAuthService.Delivery> send(@RequestBody Input input) {
        if (!"login".equals(input.purpose()) && !"reset".equals(input.purpose())) {
            throw new BizException(ResultCode.BAD_REQUEST, "验证码用途无效");
        }
        return Result.ok(phones.send(input.purpose(), input.phone(), null));
    }

    @PostMapping("/api/auth/phone/login")
    public Result<LoginVO> login(@RequestBody Input input) {
        return Result.ok(phones.login(input.phone(), input.code()));
    }

    @PostMapping("/api/auth/phone/reset")
    public Result<Void> reset(@RequestBody Input input) {
        phones.reset(input.phone(), input.code(), input.password());
        return Result.ok();
    }

    @GetMapping("/api/user/verified-phone")
    public Result<PhoneAuthService.Binding> binding() {
        return Result.ok(phones.binding(UserContext.getUid()));
    }

    @PostMapping("/api/user/verified-phone/send")
    public Result<PhoneAuthService.Delivery> sendBinding(@RequestBody Input input) {
        return Result.ok(phones.send("bind", input.phone(), UserContext.getUid()));
    }

    @PutMapping("/api/user/verified-phone")
    public Result<PhoneAuthService.Binding> bind(@RequestBody Input input) {
        return Result.ok(phones.bind(UserContext.getUid(), input.phone(), input.code()));
    }
}
