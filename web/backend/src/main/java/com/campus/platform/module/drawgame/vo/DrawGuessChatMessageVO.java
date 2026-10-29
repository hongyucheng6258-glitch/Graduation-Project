package com.campus.platform.module.drawgame.vo;

import java.time.Instant;

public record DrawGuessChatMessageVO(String id, Long userId, String nickname,
                                     String content, boolean correct, Instant createdAt) { }
