package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.idle.dto.AppointDTO;
import com.campus.platform.module.idle.service.IdleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private static final String OFFER = """
            SELECT title,SHA2(JSON_ARRAY(user_id,title,description,expect_item,category,images),256)
            FROM idle_item WHERE id=? AND user_id<>? AND audit_status=? AND status=?
            """;

    private final JdbcTemplate jdbc;
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
        jdbc.update("""
                INSERT INTO agent_idle_appointment(id,user_id,item_id,item_title,remark,expires_at,offer_version)
                VALUES(?,?,?,?,?,DATE_ADD(NOW(), INTERVAL 10 MINUTE),?)
                """, id, uid, itemId, offer.title(), remark, offer.version());
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
        jdbc.update("UPDATE agent_idle_appointment SET appointment_id=?,remark=? WHERE id=?", appointment.getId(), form.getMessage(), id);
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
        return jdbc.query(OFFER + (lock ? " FOR UPDATE" : ""), (rs, row) -> new Offer(rs.getString(1), rs.getString(2)),
                id, uid, Constants.AUDIT_PASS, Constants.IDLE_ON_SHELF).stream().findFirst().orElse(null);
    }

    private Stored query(Long uid, String id, boolean lock) {
        return jdbc.query("""
                SELECT a.id,a.item_id,a.item_title,a.remark,a.expires_at,a.appointment_id,
                       CASE WHEN a.appointment_id IS NOT NULL THEN 'SUBMITTED'
                            WHEN a.expires_at<=NOW() THEN 'EXPIRED' ELSE 'PENDING' END,
                       (SELECT status FROM idle_appointment WHERE id=a.appointment_id AND buyer_id=a.user_id),a.offer_version
                FROM agent_idle_appointment a WHERE a.id=? AND a.user_id=?
                """ + (lock ? " FOR UPDATE" : ""), (rs, row) -> new Stored(new Card(rs.getString(1), rs.getLong(2), rs.getString(3),
                rs.getString(4), rs.getObject(5, LocalDateTime.class), rs.getObject(6, Long.class), rs.getString(7),
                rs.getObject(8, Integer.class)), rs.getString(9)), id, uid).stream().findFirst().orElse(null);
    }

    private record Offer(String title, String version) {}
    private record Stored(Card card, String offerVersion) {}
    public record Card(String id, long itemId, String itemTitle, String remark, LocalDateTime expiresAt,
                       Long appointmentId, String state, Integer appointmentStatus) {}
}
