package com.campus.platform.common;

/**
 * 当前登录用户上下文：基于 ThreadLocal 在一次请求内传递 {uid, role}。
 * <p>由拦截器在 preHandle 写入，afterCompletion 清理。
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long uid, String role) {
        HOLDER.set(new CurrentUser(uid, role));
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long getUid() {
        CurrentUser u = get();
        return u == null ? null : u.uid();
    }

    public static String getRole() {
        CurrentUser u = get();
        return u == null ? null : u.role();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 当前登录用户信息（不可变 Record）。
     */
    public record CurrentUser(Long uid, String role) {
        public boolean isAdmin() {
            return "admin".equals(role);
        }
    }
}
