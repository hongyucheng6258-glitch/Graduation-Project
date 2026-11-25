package com.campus.platform.common;

import cn.dev33.satoken.stp.StpLogic;

/**
 * Sa-Token 双账号体系。
 *
 * <p>学生端与管理员端各自使用一个独立的 {@link StpLogic}：
 * <ul>
 *   <li>{@link #STUDENT}：会话 key 前缀 {@code satoken:student:}，只存学生登录态</li>
 *   <li>{@link #ADMIN}：会话 key 前缀 {@code satoken:admin:}，只存管理员登录态</li>
 * </ul>
 * 两套会话互不相通，学生 token 无法访问管理端接口，管理员 token 也无法访问学生端接口；
 * 即使 user 表和 admin 表出现相同 id，也不会串号。
 */
public final class StpKit {

    public static final StpLogic STUDENT = new StpLogic("student");

    public static final StpLogic ADMIN = new StpLogic("admin");

    private StpKit() {
    }
}
