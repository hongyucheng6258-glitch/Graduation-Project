package com.campus.platform.module.admin.service;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.admin.entity.Admin;
import com.campus.platform.module.admin.mapper.AdminMapper;
import com.campus.platform.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管理端权限统一校验组件（R2 复查修复：按「撤销时间 vs 令牌签发时间」失效旧令牌）。
 *
 * <p>所有 /api/admin/** 入口先由 {@code AdminInterceptor} 校验令牌并核对管理员有效性，
 * 再通过本组件按业务角色（super/audit）做细粒度限制。
 *
 * <p>令牌撤销模型（替代原「无过期黑名单写死账号」导致重登也无法恢复的缺陷）：
 * 删除/禁用/降权时，把「撤销时间」{@code nowMillis} 写入 Redis
 * {@code auth:revoked-at:admin:<id>}；拦截器取令牌 iat 与之比较——
 * <b>iat &le; 撤销时间 → 旧令牌拒绝；iat &gt; 撤销时间 → 重登后的新令牌放行</b>。
 * 重登录<b>不</b>清除该 key（也不清空黑名单），因此旧令牌在重登后依然被拒绝。
 */
@Service
@RequiredArgsConstructor
public class AdminPermissionService {

    /** 管理员令牌撤销时间 key：auth:revoked-at:admin:&lt;id&gt;，value=epoch millis */
    public static final String REVOKED_AT_KEY_PREFIX = "auth:revoked-at:admin:";

    private final AdminMapper adminMapper;
    private final RedisUtils redisUtils;

    /**
     * 校验令牌对应的管理员当前仍存在且可用（未被禁用、存量令牌未被撤销）。
     *
     * @param adminId           令牌中的管理员 id
     * @param tokenIssuedAtMillis 令牌 iat（epoch millis）；为 null 时仅做 DB 状态校验
     */
    public Admin requireActive(Long adminId, Long tokenIssuedAtMillis) {
        if (adminId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "管理员账号不存在，请重新登录");
        }
        if (admin.getStatus() != null && admin.getStatus() == 1) {
            throw new BizException(ResultCode.UNAUTHORIZED, "管理员账号已被禁用");
        }
        // 旧令牌失效判断：撤销时间存在且不早于本令牌签发时间 → 拒绝
        Object revokedAt = redisUtils.get(revokedAtKey(adminId));
        if (revokedAt != null && tokenIssuedAtMillis != null) {
            try {
                long revoked = Long.parseLong(revokedAt.toString());
                if (tokenIssuedAtMillis <= revoked) {
                    throw new BizException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
                }
            } catch (NumberFormatException ignore) {
                // key 损坏时按未撤销处理，避免把所有管理员锁死
            }
        }
        return admin;
    }

    /** 供已在拦截器完成令牌校验后的内部调用使用（无 iat 比较） */
    public Admin requireActive(Long adminId) {
        return requireActive(adminId, null);
    }

    /** 仅超级管理员可操作（当前登录管理员必须是 super） */
    public Admin requireSuper() {
        Admin admin = requireActive(UserContext.getUid());
        if (!"super".equals(admin.getRole())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权限：仅超级管理员可操作");
        }
        return admin;
    }

    /**
     * 撤销管理员存量令牌（删除 / 禁用 / 降权后调用）：
     * 记录当前时间为「撤销时间」，此后所有 iat ≤ 该时间的令牌被拒绝；
     * 重登后新令牌 iat 更晚，自动可用，且不影响旧令牌继续被拒绝。
     */
    public void revokeToken(Long adminId) {
        if (adminId != null) {
            redisUtils.set(revokedAtKey(adminId), String.valueOf(System.currentTimeMillis()));
        }
    }

    /** 解除撤销（管理员恢复可用时调用，清空撤销时间记录） */
    public void restoreToken(Long adminId) {
        if (adminId != null) {
            redisUtils.delete(revokedAtKey(adminId));
        }
    }

    private String revokedAtKey(Long adminId) {
        return REVOKED_AT_KEY_PREFIX + adminId;
    }
}
