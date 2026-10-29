package com.campus.platform.module.drawgame.vo;

public record DrawGuessPlayerVO(Long userId, String nickname, String avatar, int score,
                               boolean online, boolean owner) { }
