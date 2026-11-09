package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.module.idle.service.IdleService;
import com.campus.platform.module.idle.vo.IdleGuideCard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class IdleAgentToolsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final IdleService idle = mock(IdleService.class);
    private final IdleAgentTools tools = new IdleAgentTools(idle, json);

    @Test
    void searchesSwapConditionAndReturnsOnlyPublicCardFieldsToModel() throws Exception {
        var card = new IdleGuideCard(8L, "机械键盘", "数码", "换编程书", "按键正常", 0, false, 92L);
        when(idle.guideSearch(5L, "键盘", "", "编程书", 6)).thenReturn(List.of(card));
        var result = tools.tools().get(0).run(5L, json.readTree("""
                {"keyword":"键盘","exchange":"编程书"}
                """));
        assertThat(result.at("/items/0/itemId").asLong()).isEqualTo(8L);
        assertThat(result.at("/items/0/expectItem").asText()).isEqualTo("换编程书");
        assertThat(result.toString()).doesNotContain("publisherId", "auditReason", "imageList");
    }

    @Test
    void rejectsAnotherUserIdentityAndInvalidDetailId() throws Exception {
        assertThatThrownBy(() -> tools.tools().get(0).run(5L, json.readTree("{\"userId\":99}")))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> tools.tools().get(1).run(5L, json.readTree("{\"itemId\":0}")))
                .isInstanceOf(BizException.class);
        verifyNoInteractions(idle);
    }
}
