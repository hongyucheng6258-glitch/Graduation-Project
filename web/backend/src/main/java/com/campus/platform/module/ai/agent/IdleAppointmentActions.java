package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.idle.dto.AppointDTO;
import com.campus.platform.module.idle.service.IdleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.campus.platform.module.ai.mapper.AgentIdleAppointmentMapper;
import com.campus.platform.module.ai.mapper.AgentIdleAppointmentMapper.Offer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdleAppointmentActions implements AgentToolSource {
    static final String PREPARE = "prepare_idle_appointment";
    private final AgentIdleAppointmentMapper appointments;
    private final ObjectMapper json;
    private final IdleService idle;

    @Override
    public List<AgentTool> tools() {
        return List.of(new AgentTool(PREPARE,
                "学生明确要预约互换时生成确认卡，不提交预约。传 itemId 或最近闲置搜索中的 resultNumber，从1开始。"
                        + "remark 可选，原样转述用户提供的预约留言，最多200字。",
                Map.of("itemId", "integer", "resultNumber", "integer", "remark", "string"), List.of(), this::prepare));
    }

    private JsonNode prepare(Long uid, JsonNode args) {
        long itemId = args.path("itemId").asLong();
        String remark = args.path("remark").asText("").strip();
        if (itemId <= 0 || remark.length() > 200) {
            throw new BizException(ResultCode.BAD_REQUEST, "闲置编号无效或预约留言超过200字");
        }
        var offer = offer(uid, itemId, false);
        if (offer == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "该闲置当前无法预约");
        }
        String id = UUID.randomUUID().toString();
        appointments.insertCard(id, uid, itemId, offer.title(), remark, offer.version());
        return json.valueToTree(card(uid, id));
    }

    @Transactional
    public Card confirm(Long uid, String id) {
        return confirm(uid, id, null);
    }

    @Transactional
    public Card confirm(Long uid, String id, String remark) {
        if (remark != null && remark.length() > 200) {
            throw new BizException(ResultCode.BAD_REQUEST, "留言最多 200 字");
        }
        var row = query(uid, id, true);
        if (row == null) throw new BizException(ResultCode.NOT_FOUND, "未找到闲置预约确认卡");
        var current = row.card();
        if (current.appointmentId() != null) return current;
        if ("EXPIRED".equals(current.state())) {
            throw new BizException(ResultCode.BAD_REQUEST, "确认卡已过期，请重新发起预约");
        }
        if (!matches(row, offer(uid, current.itemId(), true))) {
            throw new BizException(ResultCode.BAD_REQUEST, "闲置条件已更新或物品已不可预约，请重新查询");
        }
        var form = new AppointDTO();
        form.setMessage(remark == null ? current.remark() : remark.strip());
        var appointment = idle.appoint(uid, current.itemId(), form);
        appointments.markSubmitted(id, appointment.getId(), form.getMessage());
        return card(uid, id);
    }

    Card card(Long uid, String id) {
        var row = query(uid, id, false);
        if (row == null) return null;
        var card = row.card();
        if ("PENDING".equals(card.state()) && !matches(row, offer(uid, card.itemId(), false))) {
            return new Card(card.id(), card.itemId(), card.itemTitle(), card.remark(), card.expiresAt(), null, "CHANGED", null);
        }
        return card;
    }

    private boolean matches(Stored row, Offer offer) {
        return offer != null && offer.version().equals(row.offerVersion());
    }

    private Offer offer(Long uid, long id, boolean lock) {
        return lock ? appointments.lockOffer(id, uid, Constants.AUDIT_PASS, Constants.IDLE_ON_SHELF)
                : appointments.findOffer(id, uid, Constants.AUDIT_PASS, Constants.IDLE_ON_SHELF);
    }

    private Stored query(Long uid, String id, boolean lock) {
        var row = lock ? appointments.lockCard(id, uid) : appointments.findCard(id, uid);
        if (row == null) {
            return null;
        }
        return new Stored(new Card(row.id(), row.itemId(), row.itemTitle(), row.remark(), row.expiresAt(),
                row.appointmentId(), row.state(), row.appointmentStatus()), row.offerVersion());
    }

    private record Stored(Card card, String offerVersion) {}
    public record Card(String id, long itemId, String itemTitle, String remark, LocalDateTime expiresAt,
                       Long appointmentId, String state, Integer appointmentStatus) {}
}
