package com.campus.platform.module.auth.service;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.admin.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {
    private final JdbcTemplate jdbc;
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
        jdbc.update("""
                INSERT INTO student_password_recovery(ticket_hash,student_no,claimed_name,contact,explanation)
                VALUES (?,?,?,?,?)
                """, hash(receipt), studentNo, name, contact, explanation);
        return receipt;
    }

    public Status status(String receipt) {
        return jdbc.query("""
                SELECT status,decision_reason FROM student_password_recovery WHERE ticket_hash=?
                """, (rs, i) -> new Status(rs.getString(1), rs.getString(2)), receiptHash(receipt))
                .stream().findFirst().orElseThrow(PasswordRecoveryService::missing);
    }

    public List<Map<String, Object>> list(long before) {
        return jdbc.queryForList("""
                SELECT id,student_no AS studentNo,claimed_name AS name,contact,explanation,
                       status,decision_reason AS reason,created_at AS createdAt
                FROM student_password_recovery WHERE id<? ORDER BY id DESC LIMIT 30
                """, before);
    }

    @Transactional
    public void decide(long id, boolean approve, String reason) {
        if (approve) {
            String studentNo = jdbc.query("SELECT student_no FROM student_password_recovery WHERE id=?",
                    (rs, index) -> rs.getString(1), id).stream().findFirst().orElseThrow(PasswordRecoveryService::missing);
            if (findActiveUser(studentNo) == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "没有找到可用的学生账号，请核实学号");
            }
        }
        String note = approve ? null : required(reason, 500);
        int changed = jdbc.update("""
                UPDATE student_password_recovery SET status=?,decision_reason=?,decided_at=NOW()
                WHERE id=? AND status='PENDING'
                """, approve ? "APPROVED" : "REJECTED", note, id);
        if (changed != 1) throw new BizException(ResultCode.BAD_REQUEST, "申请不存在或已审核");
    }

    @Transactional
    public String claim(String receipt) {
        var rows = jdbc.queryForList("""
                SELECT id,student_no FROM student_password_recovery
                WHERE ticket_hash=? AND status='APPROVED' AND decided_at>NOW()-INTERVAL 7 DAY FOR UPDATE
                """, receiptHash(receipt));
        if (rows.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST, "申请尚不可领取、已领取或领取期限已过");
        var row = rows.get(0);
        Long userId = findActiveUser((String) row.get("student_no"));
        if (userId == null) throw missing();
        String password = accounts.resetPassword(userId);
        jdbc.update("UPDATE student_password_recovery SET status='CLAIMED' WHERE id=?", row.get("id"));
        return password;
    }

    private Long findActiveUser(String studentNo) {
        return jdbc.query("SELECT id FROM `user` WHERE student_no=? AND status=0",
                (rs, index) -> rs.getLong(1), studentNo).stream().findFirst().orElse(null);
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
