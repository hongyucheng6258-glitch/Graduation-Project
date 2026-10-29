package com.campus.platform.module.drawgame.vo;

import java.util.List;
import java.util.Map;

public record DrawGuessRoomVO(Long id, String roomCode, String title, boolean privateRoom,
                              String status, Long ownerUserId, Long drawerUserId,
                              int playerCount, int onlineCount, int maxPlayers,
                              int roundsPerPlayer, int currentTurn, int totalTurns,
                              int remainingSeconds, int answerLength, boolean isOwner,
                              boolean isDrawer, List<DrawGuessPlayerVO> players,
                              List<Map<String, Object>> strokes,
                              List<DrawGuessChatMessageVO> messages, Long currentRoundId,
                              List<DrawGuessCompletedArtworkVO> pendingArtworks) { }
