package com.campus.platform.module.ai;

import com.campus.platform.module.ai.service.ContentAiAuditService;
import com.campus.platform.module.ai.entity.AiAuditResult;

import com.campus.platform.common.Constants;
import com.campus.platform.module.ai.gateway.AiConfigHolder;
import com.campus.platform.module.ai.gateway.AiGatewayService;
import com.campus.platform.config.SystemConfigHolder;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentAiAuditServiceTest {

    @Mock
    private LostFoundMapper lostFoundMapper;

    private SystemConfigHolder mockConfig(boolean aiEnabled) {
        SystemConfigHolder config = mock(SystemConfigHolder.class);
        lenient().when(config.isAiAuditEnabled()).thenReturn(aiEnabled);
        lenient().when(config.getAuditHighRiskWords()).thenReturn(List.of("转账", "押金", "银行卡", "刷单", "兼职返利", "加微信", "二维码", "代充", "账号交易"));
        lenient().when(config.getAuditMediumRiskWords()).thenReturn(List.of("悬赏", "收费", "校外", "联系我", "手机号", "群聊", "购买"));
        return config;
    }

    @Test
    void lowRiskContentShouldAutoPassAndRecordAiDecision() {
        ContentAiAuditService service = new ContentAiAuditService(lostFoundMapper, null, null, null, null, null, null, mockConfig(false));

        LostFound content = new LostFound();
        content.setId(1L);
        content.setAuditStatus(Constants.AUDIT_PENDING);

        service.applyDecision(Constants.BIZ_LOSTFOUND, content, new AiAuditResult("LOW", 10, "普通校园信息", null));

        assertThat(content.getAuditStatus()).isEqualTo(Constants.AUDIT_PASS);
        assertThat(content.getAiRiskLevel()).isEqualTo(0);
        assertThat(content.getAuditSource()).isEqualTo("ai");
        verify(lostFoundMapper).updateById(content);
    }

    @Test
    void highRiskContentShouldRemainPendingForManualReview() {
        ContentAiAuditService service = new ContentAiAuditService(lostFoundMapper, null, null, null, null, null, null, mockConfig(false));

        LostFound content = new LostFound();
        content.setId(2L);
        content.setAuditStatus(Constants.AUDIT_PENDING);

        service.applyDecision(Constants.BIZ_LOSTFOUND, content, new AiAuditResult("HIGH", 85, "疑似外部交易引导", null));

        assertThat(content.getAuditStatus()).isEqualTo(Constants.AUDIT_PENDING);
        assertThat(content.getAiRiskLevel()).isEqualTo(2);
        assertThat(content.getAuditSource()).isEqualTo("ai");
        verify(lostFoundMapper).updateById(content);
    }

    @Test
    void aiFailureShouldFallbackToRuleAndAutoPassLowRisk() {
        AiGatewayService gateway = mock(AiGatewayService.class);
        AiConfigHolder holder = mock(AiConfigHolder.class);
        when(holder.getApiKey()).thenReturn("sk-real-key");
        when(gateway.internalChat(anyLong(), anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("AI down"));
        ContentAiAuditService service = new ContentAiAuditService(lostFoundMapper, null, null, null, null, gateway, holder, mockConfig(true));

        LostFound content = new LostFound();
        content.setId(3L);
        content.setAuditStatus(Constants.AUDIT_PENDING);

        service.audit(Constants.BIZ_LOSTFOUND, content, 1L, "捡到校园卡", "在图书馆二楼捡到一张校园卡，失主请到一楼服务台认领");

        assertThat(content.getAuditStatus()).isEqualTo(Constants.AUDIT_PASS);
        verify(lostFoundMapper).updateById(content);
    }

    @Test
    void aiFailureShouldKeepRuleInterceptionForHighRisk() {
        AiGatewayService gateway = mock(AiGatewayService.class);
        AiConfigHolder holder = mock(AiConfigHolder.class);
        when(holder.getApiKey()).thenReturn("sk-real-key");
        when(gateway.internalChat(anyLong(), anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("AI down"));
        ContentAiAuditService service = new ContentAiAuditService(lostFoundMapper, null, null, null, null, gateway, holder, mockConfig(true));

        LostFound content = new LostFound();
        content.setId(4L);
        content.setAuditStatus(Constants.AUDIT_PENDING);

        service.audit(Constants.BIZ_LOSTFOUND, content, 1L, "兼职", "加微信刷单返利，日结 300");

        assertThat(content.getAuditStatus()).isEqualTo(Constants.AUDIT_PENDING);
        verify(lostFoundMapper).updateById(content);
    }
}
