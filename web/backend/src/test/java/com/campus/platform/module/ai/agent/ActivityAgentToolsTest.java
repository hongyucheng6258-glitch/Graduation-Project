package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.PageResult;
import com.campus.platform.module.activity.service.ActivityService;
import com.campus.platform.module.activity.vo.MemberVO;
import com.campus.platform.module.ai.service.CampusGuideService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ActivityAgentToolsTest {
    @Test
    void signupToolUsesOnlyAuthenticatedIdentityAndRedactsPrivateFields() throws Exception {
        ActivityService activity = mock(ActivityService.class);
        ObjectMapper json = new ObjectMapper();
        AgentTool tool = new ActivityAgentTools(activity, json, mock(CampusGuideService.class)).tools().stream()
                .filter(candidate -> candidate.name().equals("get_my_signups")).findFirst().orElseThrow();
        assertThatThrownBy(() -> tool.run(17L, json.readTree("{\"userId\":19}")))
                .isInstanceOf(BizException.class);
        verifyNoInteractions(activity);

        MemberVO signup = new MemberVO();
        signup.setActivityId(5L);
        signup.setActivityTitle("Campus Agent Test");
        signup.setStatus(0);
        signup.setUserId(17L);
        signup.setRemark("Private remark");
        PageResult<MemberVO> page = new PageResult<>();
        page.setTotal(1L);
        page.setList(List.of(signup));
        when(activity.mySignups(17L, 1, 8)).thenReturn(page);

        String result = tool.run(17L, json.createObjectNode()).toString();
        assertThat(result).contains("Campus Agent Test", "待审核")
                .doesNotContain("userId", "remark", "Private remark");
        verify(activity).mySignups(17L, 1, 8);
    }
}
