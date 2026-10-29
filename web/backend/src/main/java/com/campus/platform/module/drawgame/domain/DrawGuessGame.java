package com.campus.platform.module.drawgame.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Server-authoritative rules for one draw-and-guess room. */
public final class DrawGuessGame {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 6;
    public static final int TURN_SECONDS = 60;
    public static final int EARLY_ADVANCE_SECONDS = 5;

    public enum Status { WAITING, PLAYING, FINISHED }

    public record PlayerView(long userId, String nickname, String avatar, int score) { }

    public record GuessResult(boolean accepted, boolean correct, boolean earlyAdvanceScheduled,
                              int pointsAwarded) { }

    public record TurnTransition(int finishedTurnNumber, long finishedDrawerUserId,
                                 String revealedAnswer, boolean roomFinished,
                                 Long nextDrawerUserId, Instant nextDeadline) { }

    public record TurnView(int turnNumber, long drawerUserId, Instant deadline) { }

    private static final class PlayerState {
        private final long userId;
        private final String nickname;
        private final String avatar;
        private int score;

        private PlayerState(long userId, String nickname, String avatar) {
            this.userId = userId;
            this.nickname = nickname;
            this.avatar = avatar;
        }

        private PlayerView view() {
            return new PlayerView(userId, nickname, avatar, score);
        }
    }

    private final long roomId;
    private long ownerUserId;
    private final int maxPlayers;
    private final int roundsPerPlayer;
    private final LinkedHashMap<Long, PlayerState> players = new LinkedHashMap<>();
    private final LinkedHashSet<Long> guessedUserIds = new LinkedHashSet<>();

    private Status status = Status.WAITING;
    private int completedTurns;
    private Long drawerUserId;
    private String answer;
    private Instant deadline;
    private Instant earlyAdvanceAt;

    public DrawGuessGame(long roomId, long ownerUserId, int maxPlayers, int roundsPerPlayer) {
        if (roomId <= 0 || ownerUserId <= 0) throw new IllegalArgumentException("房间和房主编号必须有效");
        if (maxPlayers < MIN_PLAYERS || maxPlayers > MAX_PLAYERS) {
            throw new IllegalArgumentException("房间人数必须在2到6人之间");
        }
        if (roundsPerPlayer < 1 || roundsPerPlayer > 8) {
            throw new IllegalArgumentException("每人轮数必须在1到8轮之间");
        }
        this.roomId = roomId;
        this.ownerUserId = ownerUserId;
        this.maxPlayers = maxPlayers;
        this.roundsPerPlayer = roundsPerPlayer;
    }

    public void addPlayer(long userId, String nickname, String avatar) {
        if (status != Status.WAITING) throw new IllegalStateException("游戏开始后不能加入");
        if (userId <= 0) throw new IllegalArgumentException("用户编号必须有效");
        if (players.containsKey(userId)) return;
        if (players.size() >= maxPlayers) throw new IllegalStateException("房间人数已满");
        String safeNickname = nickname == null ? "校园同学" : nickname.trim();
        if (safeNickname.isEmpty()) safeNickname = "校园同学";
        if (safeNickname.length() > 40) safeNickname = safeNickname.substring(0, 40);
        players.put(userId, new PlayerState(userId, safeNickname, avatar));
    }

    public void transferOwner(long newOwnerUserId) {
        if (status != Status.WAITING || !players.containsKey(newOwnerUserId)) {
            throw new IllegalStateException("房主只能转给等待中的房间成员");
        }
        ownerUserId = newOwnerUserId;
    }

    public void removeWaitingPlayer(long userId) {
        if (status != Status.WAITING) throw new IllegalStateException("游戏开始后不能移除座位");
        if (userId == ownerUserId && players.size() > 1) throw new IllegalStateException("移交房主后才能离开");
        if (!players.containsKey(userId)) throw new IllegalStateException("玩家不在房间中");
        players.remove(userId);
        if (userId == ownerUserId) status = Status.FINISHED;
    }

    public TurnView start(long actorUserId, String firstAnswer, Instant now) {
        if (actorUserId != ownerUserId) throw new IllegalStateException("只有房主可以开始游戏");
        if (status != Status.WAITING) throw new IllegalStateException("房间当前不能开始");
        if (players.size() < MIN_PLAYERS) throw new IllegalStateException("至少需要2名玩家");
        this.answer = requireAnswer(firstAnswer);
        this.drawerUserId = ownerUserId;
        this.completedTurns = 0;
        this.guessedUserIds.clear();
        this.deadline = now.plusSeconds(TURN_SECONDS);
        this.status = Status.PLAYING;
        return new TurnView(1, drawerUserId, deadline);
    }

    public boolean canDraw(long userId) {
        return status == Status.PLAYING && drawerUserId != null && drawerUserId == userId;
    }

    public boolean canDraw(long userId, Instant now) {
        return canDraw(userId) && deadline != null && now != null && now.isBefore(deadline);
    }

    /** The secret is deliberately returned only for the active drawer. */
    public String answerFor(long userId) {
        return canDraw(userId) ? answer : null;
    }

    public GuessResult guess(long userId, String submittedAnswer, Instant now) {
        requirePlaying();
        if (deadline == null || now == null || !now.isBefore(deadline)) {
            return new GuessResult(false, false, false, 0);
        }
        if (!players.containsKey(userId) || canDraw(userId) || guessedUserIds.contains(userId)) {
            return new GuessResult(false, false, false, 0);
        }
        if (submittedAnswer == null || submittedAnswer.isBlank() || submittedAnswer.length() > 60) {
            throw new IllegalArgumentException("猜词不能为空且不能超过60个字符");
        }
        if (!normalize(submittedAnswer).equals(normalize(answer))) {
            return new GuessResult(true, false, false, 0);
        }

        guessedUserIds.add(userId);
        players.get(userId).score++;
        players.get(drawerUserId).score++;
        boolean allGuessersCorrect = guessedUserIds.size() == players.size() - 1;
        if (allGuessersCorrect) earlyAdvanceAt = now.plusSeconds(EARLY_ADVANCE_SECONDS);
        return new GuessResult(true, true, allGuessersCorrect, 1);
    }

    public TurnTransition skip(long actorUserId, Instant now, String nextAnswer) {
        requirePlaying();
        if (!canDraw(actorUserId)) throw new IllegalStateException("只有当前画手可以跳过");
        if (deadline == null || now == null || !now.isBefore(deadline)) {
            throw new IllegalStateException("作画时间已结束");
        }
        return finishTurn(now, nextAnswer);
    }

    /** Advances on timeout or after the five-second all-guessed grace period. */
    public TurnTransition advanceIfDue(Instant now, String nextAnswer) {
        if (status != Status.PLAYING) return null;
        boolean timerExpired = deadline != null && !now.isBefore(deadline);
        boolean earlyAdvanceDue = earlyAdvanceAt != null && !now.isBefore(earlyAdvanceAt);
        return timerExpired || earlyAdvanceDue ? finishTurn(now, nextAnswer) : null;
    }

    private TurnTransition finishTurn(Instant now, String nextAnswer) {
        int finishedTurnNumber = completedTurns + 1;
        long finishedDrawer = drawerUserId;
        String revealedAnswer = answer;
        completedTurns++;
        earlyAdvanceAt = null;
        guessedUserIds.clear();

        if (completedTurns >= players.size() * roundsPerPlayer) {
            status = Status.FINISHED;
            drawerUserId = null;
            answer = null;
            deadline = null;
            return new TurnTransition(finishedTurnNumber, finishedDrawer, revealedAnswer,
                    true, null, null);
        }

        List<Long> userIds = List.copyOf(players.keySet());
        drawerUserId = userIds.get(completedTurns % userIds.size());
        answer = requireAnswer(nextAnswer);
        deadline = now.plusSeconds(TURN_SECONDS);
        return new TurnTransition(finishedTurnNumber, finishedDrawer, revealedAnswer,
                false, drawerUserId, deadline);
    }

    private String requireAnswer(String value) {
        if (value == null || value.isBlank() || value.length() > 40) {
            throw new IllegalArgumentException("答案长度必须为1到40个字符");
        }
        return value.trim();
    }

    private String normalize(String value) {
        return value.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private void requirePlaying() {
        if (status != Status.PLAYING) throw new IllegalStateException("游戏当前未进行");
    }

    public List<PlayerView> players() {
        return players.values().stream().map(PlayerState::view).toList();
    }

    public Map<Long, Integer> scores() {
        LinkedHashMap<Long, Integer> result = new LinkedHashMap<>();
        players.forEach((userId, player) -> result.put(userId, player.score));
        return Collections.unmodifiableMap(result);
    }

    public Set<Long> guessedUserIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(guessedUserIds));
    }

    public long roomId() { return roomId; }
    public long ownerUserId() { return ownerUserId; }
    public int maxPlayers() { return maxPlayers; }
    public int roundsPerPlayer() { return roundsPerPlayer; }
    public Status status() { return status; }
    public int turnNumber() { return status == Status.PLAYING ? completedTurns + 1 : completedTurns; }
    public int totalTurns() { return players.size() * roundsPerPlayer; }
    public Long drawerUserId() { return drawerUserId; }
    public Instant deadline() { return deadline; }
    public Instant advanceAt() { return earlyAdvanceAt; }
    public int playerCount() { return players.size(); }
    public boolean containsPlayer(long userId) { return players.containsKey(userId); }
}
