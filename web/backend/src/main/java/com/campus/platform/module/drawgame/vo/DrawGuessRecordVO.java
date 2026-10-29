package com.campus.platform.module.drawgame.vo;

import java.time.Instant;

public record DrawGuessRecordVO(Long roundId, Long roomId, String roomCode, String title,
                                int turnNumber, String word, Long drawerUserId,
                                String drawerNickname, String snapshotUrl, Instant endedAt) { }
