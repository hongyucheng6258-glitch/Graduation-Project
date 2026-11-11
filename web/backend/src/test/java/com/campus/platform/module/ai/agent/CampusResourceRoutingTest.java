package com.campus.platform.module.ai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CampusResourceRoutingTest {
    @Test
    void publicGuidesAreNotTreatedAsLivePostSearches() {
        assertNull(CampusResourceTools.explicitLookupKind("请查公共资料：失物招领中何时确认已找回？"));
        assertNull(CampusResourceTools.explicitLookupKind("帮我查资料：互助问答中谁可以采纳回答？"));
        assertEquals("lost", CampusResourceTools.explicitLookupKind("帮我查失物招领里的黑色折叠伞"));
        assertEquals("qa", CampusResourceTools.explicitLookupKind("查互助问答里的选课帖子"));
    }
}
