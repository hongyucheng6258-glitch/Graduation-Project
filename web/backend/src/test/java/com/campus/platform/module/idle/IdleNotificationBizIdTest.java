package com.campus.platform.module.idle;

import com.campus.platform.module.idle.service.IdleService;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：闲置预约通知的 bizId 必须指向闲置物品 ID（itemId），
 * 而不是预约记录 ID（id），否则消息页点击后会把预约 ID 当物品 ID，
 * 跳转到错误的闲置详情（ID 碰撞场景）。
 */
class IdleNotificationBizIdTest {

    private String idleServiceSource() throws Exception {
        return Files.readString(
                Path.of("src/main/java/com/campus/platform/module/idle/service/IdleService.java")
        );
    }

    @Test
    void appointmentNotificationsMustUseItemIdNotAppointmentId() throws Exception {
        String src = idleServiceSource();

        // 三处闲置相关通知（新预约 / 接受拒绝 / 完成）都不得把预约记录 ID 当作业务 ID
        assertFalse(src.contains("Constants.BIZ_IDLE, appointment.getId())"),
                "闲置通知不应使用预约记录 ID 作为 bizId，否则点击消息会跳错闲置详情");
    }

    @Test
    void appointmentNotificationUsesItemIdForNewAppointment() throws Exception {
        String src = idleServiceSource();

        // 新预约通知（发送给卖家）必须携带物品 ID
        assertTrue(src.contains("Constants.BIZ_IDLE, appointment.getItemId())"),
                "新预约通知的 bizId 应为 appointment.getItemId()");
    }
}
