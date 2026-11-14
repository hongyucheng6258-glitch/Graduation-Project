package com.campus.platform.module.auth.service;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.admin.service.AdminUserService;
import com.campus.platform.module.auth.mapper.PasswordRecoveryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {
    private final PasswordRecoveryMapper recoveries;
    private final StringRedisTemplate redis;
    private final AdminUserService accounts;

    public record Application(String studentNo, String name, String contact, String explanation) {}
    public record Status(String status, String reason) {}

    public String submit(Application application) {
        String studentNo = required(application.studentNo(), 32);
        String name = required(application.name(), 64);
        String contact = required(application.contact(), 80);
        String explanation = required(application.explanation(), 500);
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                "recovery:submit:" + hash(studentNo.toLowerCase(java.util.Locale.ROOT)), "1", Duration.ofMinutes(1)))) {
            throw new BizException(ResultCode.RATE_LIMITED, "请稍后再提交申请");
        }
        String receipt = UUID.randomUUID().toString() + UUID.randomUUID();
        recoveries.insertApplication(hash(receipt), studentNo, name, contact, explanation);
        return receipt;
    }

    public Status status(String receipt) {
        Status status = recoveries.findStatus(receiptHash(receipt));
        if (status == null) throw missing();
        return status;
    }

    public List<Map<String, Object>> list(long before) {
        return recoveries.listBefore(before).stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", row.id());
            result.put("studentNo", row.studentNo());
            result.put("name", row.name());
            result.put("contact", row.contact());
            result.put("explanation", row.explanation());
            result.put("status", row.status());
            result.put("reason", row.reason());
            result.put("createdAt", row.createdAt());
            return result;
        }).toList();
    }

    @Transactional
    public void decide(long id, boolean approve, String reason) {
        if (approve) {
            String studentNo = recoveries.findStudentNo(id);
            if (studentNo == null) throw missing();
            if (recoveries.findActiveUserId(studentNo) == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "没有找到可用的学生账号，请核实学号");
            }
        }
        String note = approve ? null : required(reason, 500);
        int changed = recoveries.decide(id, approve ? "APPROVED" : "REJECTED", note);
        if (changed != 1) throw new BizException(ResultCode.BAD_REQUEST, "申请不存在或已审核");
    }

    @Transactional
    public String claim(String receipt) {
        var row = recoveries.lockClaimable(receiptHash(receipt));
        if (row == null) throw new BizException(ResultCode.BAD_REQUEST, "申请尚不可领取、已领取或领取期限已过");
        Long userId = recoveries.findActiveUserId(row.studentNo());
        if (userId == null) throw missing();
        String password = accounts.resetPassword(userId);
        recoveries.markClaimed(row.id());
        return password;
    }

    private static String required(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new BizException(ResultCode.BAD_REQUEST, "请完整填写信息，并检查字数限制");
        }
        return value.strip();
    }

    private static String receiptHash(String value) {
        if (value == null || value.length() != 72) throw missing();
        return hash(value);
    }

    private static String hash(String value) {
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(value);
    }

    private static BizException missing() {
        return new BizException(ResultCode.NOT_FOUND, "未找到可用的找回记录");
    }
}
