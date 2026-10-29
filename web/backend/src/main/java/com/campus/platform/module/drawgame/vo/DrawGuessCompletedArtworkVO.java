package com.campus.platform.module.drawgame.vo;

import java.util.List;
import java.util.Map;

public record DrawGuessCompletedArtworkVO(Long roundId, int turnNumber, List<Map<String, Object>> strokes) { }
