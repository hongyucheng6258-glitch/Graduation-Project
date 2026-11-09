package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.module.ai.entity.AiMessage;
import com.campus.platform.module.ai.rag.KnowledgeService;
import com.campus.platform.module.idle.service.IdleService;
import com.campus.platform.module.idle.vo.IdleGuideCard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentMessageAttachments {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ObjectProvider<KnowledgeService> knowledge;
    private final ActivitySignupActions actions;
    private final IdleService idle;
    private final IdlePublishDrafts idleDrafts;
    private final IdleAppointmentActions idleAppointments;

    public void save(long messageId, List<AgentService.Citation> citations, List<String> actionIds) {
        save(messageId, citations, actionIds, List.of(), false, List.of(), List.of());
    }

    public void save(long messageId, List<AgentService.Citation> citations, List<String> actionIds, List<Long> idleIds,
                     boolean idleSearch, List<String> idleDraftIds, List<String> idleAppointmentIds) {
        if (citations.isEmpty() && actionIds.isEmpty() && idleIds.isEmpty() && idleDraftIds.isEmpty() && idleAppointmentIds.isEmpty()) {
            return;
        }
        try {
            jdbc.update("INSERT INTO agent_message_attachment(message_id,body) VALUES(?,?)", messageId,
                    json.writeValueAsString(new Stored(citations, actionIds, idleIds, idleSearch, idleDraftIds, idleAppointmentIds)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法保存向导消息附件", e);
        }
    }

    public void restore(Long uid, List<AiMessage> messages) {
        for (AiMessage message : messages) {
            if (!"assistant".equals(message.getRole())) {
                continue;
            }
            var stored = jdbc.queryForList("SELECT body FROM agent_message_attachment WHERE message_id=?",
                    String.class, message.getId());
            if (stored.isEmpty()) {
                continue;
            }
            try {
                var values = json.readValue(stored.get(0), Stored.class);
                var visible = values.citations().stream().filter(source -> canRead(uid, source.documentId())).toList();
                var cards = values.actionIds().stream().map(id -> actions.card(uid, id)).filter(Objects::nonNull).toList();
                var idleItems = values.idleIds() == null ? List.<IdleGuideCard>of() : idle.guideCards(uid, values.idleIds());
                var drafts = values.idleDraftIds() == null ? List.<IdlePublishDrafts.Draft>of()
                        : values.idleDraftIds().stream().map(id -> idleDrafts.find(uid, id)).filter(Objects::nonNull).toList();
                var appointments = values.idleAppointmentIds() == null ? List.<IdleAppointmentActions.Card>of()
                        : values.idleAppointmentIds().stream().map(id -> idleAppointments.card(uid, id)).filter(Objects::nonNull).toList();
                message.setAttachments(new View(visible, cards, idleItems, values.idleSearch(), drafts, appointments));
            } catch (JsonProcessingException e) {
                log.warn("无法读取向导消息 {} 的附件", message.getId());
            }
        }
    }

    private boolean canRead(Long uid, long id) {
        var service = knowledge.getIfAvailable();
        if (service == null) {
            return false;
        }
        try {
            return service.status(id, uid).activeVersion() != null;
        } catch (BizException e) {
            return false;
        }
    }

    private record Stored(List<AgentService.Citation> citations, List<String> actionIds, List<Long> idleIds,
                          boolean idleSearch, List<String> idleDraftIds, List<String> idleAppointmentIds) {}
    public record View(List<AgentService.Citation> citations, List<ActivitySignupActions.Card> actions,
                       List<IdleGuideCard> idleItems, boolean idleSearch, List<IdlePublishDrafts.Draft> idleDrafts,
                       List<IdleAppointmentActions.Card> idleAppointments) {}
}
