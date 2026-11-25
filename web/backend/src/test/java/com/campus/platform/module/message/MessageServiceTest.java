package com.campus.platform.module.message;

import com.campus.platform.module.message.service.impl.MessageServiceImpl;
import com.campus.platform.module.message.mapper.MessageMapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageServiceTest {
    @Test
    void unreadBadgeExcludesPrivateMessageAggregationNotifications() {
        MessageMapper mapper = mock(MessageMapper.class);
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(4L);

        MessageServiceImpl service = new MessageServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        assertEquals(4L, service.unreadCount(7L).getCount());
    }
}
