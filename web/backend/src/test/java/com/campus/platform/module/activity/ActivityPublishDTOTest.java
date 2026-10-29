package com.campus.platform.module.activity;

import com.campus.platform.module.activity.dto.ActivityPublishDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("活动发布参数时间格式")
class ActivityPublishDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("接受前端日期选择器提交的空格分隔时间")
    void acceptsSpaceSeparatedDateTime() throws Exception {
        String json = """
                {
                  "title": "学习交流会",
                  "startTime": "2026-08-07 10:00:00",
                  "endTime": "2026-08-07 12:00:00",
                  "signupDeadline": "2026-08-06 10:00:00",
                  "maxMembers": 10,
                  "images": []
                }
                """;

        ActivityPublishDTO dto = objectMapper.readValue(json, ActivityPublishDTO.class);

        assertThat(dto.getStartTime()).isEqualTo(LocalDateTime.of(2026, 8, 7, 10, 0));
        assertThat(dto.getEndTime()).isEqualTo(LocalDateTime.of(2026, 8, 7, 12, 0));
        assertThat(dto.getSignupDeadline()).isEqualTo(LocalDateTime.of(2026, 8, 6, 10, 0));
    }
}
