package com.campus.platform.module.idle;

import com.campus.platform.module.idle.vo.PendingAppointmentVO;
import com.campus.platform.module.idle.vo.IdleDetailVO;
import com.campus.platform.module.idle.service.IdleService;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：卖家点击"闲置有新预约"通知进入详情页后，
 * 详情接口必须返回待处理预约信息，前端才能显示"接受/拒绝"操作。
 */
class IdleDetailPendingAppointmentTest {

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void detailVoCarriesPendingAppointmentForSeller() throws Exception {
        String vo = read("src/main/java/com/campus/platform/module/idle/vo/IdleDetailVO.java");
        assertTrue(vo.contains("pendingAppointment"), "IdleDetailVO 应包含待处理预约字段（pendingAppointment）");
        String pv = read("src/main/java/com/campus/platform/module/idle/vo/PendingAppointmentVO.java");
        assertTrue(pv.contains("private Integer status"),
                "PendingAppointmentVO 应包含预约状态字段，用于区分接受/确认完成");
    }

    @Test
    void detailServiceFillsPendingAppointmentWhenOwner() throws Exception {
        String svc = read("src/main/java/com/campus/platform/module/idle/service/IdleService.java");
        assertTrue(svc.contains("isOwner"), "详情查询需判断是否本人（卖家）");
        assertTrue(svc.contains("setPendingAppointment"), "详情查询应填充待处理预约（setPendingAppointment）");
        assertTrue(svc.contains("APPOINT_PENDING, Constants.APPOINT_ACCEPTED"),
                "详情查询应覆盖待确认+已接受两种状态，已接受时前端显示确认完成");
        assertTrue(svc.contains("pv.setStatus"), "应把预约状态回传给前端");
    }
}
