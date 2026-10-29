package com.campus.platform.module.drawgame.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.drawgame.domain.DrawGuessGame;
import com.campus.platform.module.drawgame.domain.DrawGuessRoomAccessPolicy;
import com.campus.platform.module.drawgame.domain.DrawGuessStrokePolicy;
import com.campus.platform.module.drawgame.entity.DrawGuessMember;
import com.campus.platform.module.drawgame.entity.DrawGuessRoom;
import com.campus.platform.module.drawgame.entity.DrawGuessRound;
import com.campus.platform.module.drawgame.entity.DrawGuessWord;
import com.campus.platform.module.drawgame.mapper.DrawGuessMemberMapper;
import com.campus.platform.module.drawgame.mapper.DrawGuessRoomMapper;
import com.campus.platform.module.drawgame.mapper.DrawGuessRoundMapper;
import com.campus.platform.module.drawgame.mapper.DrawGuessWordMapper;
import com.campus.platform.module.drawgame.vo.DrawGuessChatMessageVO;
import com.campus.platform.module.drawgame.vo.DrawGuessCompletedArtworkVO;
import com.campus.platform.module.drawgame.vo.DrawGuessPlayerVO;
import com.campus.platform.module.drawgame.vo.DrawGuessRecordVO;
import com.campus.platform.module.drawgame.vo.DrawGuessRoomVO;
import com.campus.platform.module.upload.entity.UploadResource;
import com.campus.platform.module.upload.mapper.UploadResourceMapper;
import com.campus.platform.module.upload.service.UploadService;
import com.campus.platform.module.upload.vo.UploadVO;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DrawGuessRoomService {
    private static final Pattern ROOM_CODE_PATTERN = Pattern.compile("[A-HJ-NP-Z2-9]{6}");
    private static final String[] FALLBACK_WORDS = {"梧桐树", "图书馆", "校园卡", "咖啡", "篮球", "雨伞"};
    private static final int MAX_STROKES_PER_ROOM = 2400;
    private static final int MAX_CHAT_MESSAGES = 100;
    private static final int MAX_SOCKET_MESSAGES_PER_10_SECONDS = 40;
    private static final ObjectMapper STROKE_JSON = new ObjectMapper();

    private final DrawGuessRoomMapper roomMapper;
    private final DrawGuessMemberMapper memberMapper;
    private final DrawGuessRoundMapper roundMapper;
    private final DrawGuessWordMapper wordMapper;
    private final UserMapper userMapper;
    private final UploadResourceMapper uploadResourceMapper;
    private final UploadService uploadService;
    private final com.campus.platform.module.drawgame.websocket.DrawGuessSessionRegistry sessionRegistry;

    private final ConcurrentMap<Long, RuntimeRoom> liveRooms = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timerExecutor = Executors.newScheduledThreadPool(2,
            new DrawGuessTimerThreadFactory());
    private final Pattern stripControls = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");

    private static final class RuntimeRoom {
        private final DrawGuessRoom entity;
        private final DrawGuessGame game;
        private final List<Map<String, Object>> strokes = new ArrayList<>();
        private final Deque<DrawGuessChatMessageVO> messages = new ArrayDeque<>();
        private final Map<String, Deque<Instant>> socketRate = new HashMap<>();
        private DrawGuessRound currentRound;
        private ScheduledFuture<?> timer;

        private RuntimeRoom(DrawGuessRoom entity, DrawGuessGame game) {
            this.entity = entity;
            this.game = game;
        }
    }

    private static final class DrawGuessTimerThreadFactory implements ThreadFactory {
        private int sequence;

        @Override
        public synchronized Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "draw-guess-timer-" + (++sequence));
            thread.setDaemon(true);
            return thread;
        }
    }

    /** A restart closes transient rooms cleanly while completed rounds remain reviewable. */
    @PostConstruct
    public void closeStaleRooms() {
        roomMapper.update(null, new UpdateWrapper<DrawGuessRoom>()
                .set("status", DrawGuessGame.Status.FINISHED.name())
                .in("status", DrawGuessGame.Status.WAITING.name(), DrawGuessGame.Status.PLAYING.name()));
        memberMapper.update(null, new UpdateWrapper<DrawGuessMember>()
                .set("active", false).eq("active", true));
    }

    @PreDestroy
    public void stopTimers() {
        liveRooms.values().forEach(this::cancelTimer);
        timerExecutor.shutdownNow();
    }

    @Transactional
    public DrawGuessRoomVO createRoom(Long userId, com.campus.platform.module.drawgame.dto.DrawGuessCreateRoomDTO dto) {
        User user = requireUser(userId);
        boolean privateRoom = Boolean.TRUE.equals(dto.getPrivateRoom());
        String password = dto.getPassword();
        if (privateRoom && (password == null || password.length() < 4 || password.length() > 32)) {
            throw badRequest("私密房间密码长度需为4到32位");
        }
        String passwordHash = privateRoom ? DrawGuessRoomAccessPolicy.hashPassword(password) : null;
        int maxPlayers = dto.getMaxPlayers() == null ? DrawGuessGame.MAX_PLAYERS : dto.getMaxPlayers();
        int roundsPerPlayer = dto.getRoundsPerPlayer() == null ? 1 : dto.getRoundsPerPlayer();
        if (maxPlayers < DrawGuessGame.MIN_PLAYERS || maxPlayers > DrawGuessGame.MAX_PLAYERS) {
            throw badRequest("房间人数必须在2到6人之间");
        }
        if (roundsPerPlayer < 1 || roundsPerPlayer > 8) throw badRequest("每人轮数必须在1到8轮之间");

        DrawGuessRoom entity = new DrawGuessRoom();
        entity.setRoomCode(generateRoomCode());
        entity.setOwnerUserId(userId);
        entity.setTitle(safeTitle(dto.getTitle()));
        entity.setPrivateRoom(privateRoom);
        entity.setPasswordHash(passwordHash);
        entity.setMaxPlayers(maxPlayers);
        entity.setRoundsPerPlayer(roundsPerPlayer);
        entity.setStatus(DrawGuessGame.Status.WAITING.name());
        entity.setCurrentTurn(0);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        roomMapper.insert(entity);

        DrawGuessMember owner = newMember(entity.getId(), userId, 1, user);
        memberMapper.insert(owner);
        DrawGuessGame game = new DrawGuessGame(entity.getId(), userId, maxPlayers, roundsPerPlayer);
        game.addPlayer(userId, user.getNickname(), safeAvatar(user.getAvatar()));
        RuntimeRoom runtime = new RuntimeRoom(entity, game);
        liveRooms.put(entity.getId(), runtime);
        return roomView(runtime, userId);
    }

    public List<DrawGuessRoomVO> listPublicRooms(Long viewerUserId) {
        List<DrawGuessRoom> candidates = roomMapper.selectList(new QueryWrapper<DrawGuessRoom>()
                .eq("private_room", false)
                .eq("status", DrawGuessGame.Status.WAITING.name())
                .orderByDesc("updated_at")
                .last("LIMIT 50"));
        List<DrawGuessRoomVO> result = new ArrayList<>();
        for (DrawGuessRoom room : candidates) {
            RuntimeRoom runtime = liveRooms.get(room.getId());
            if (runtime == null) continue;
            synchronized (runtime) {
                if (runtime.game.status() == DrawGuessGame.Status.WAITING
                        && runtime.game.playerCount() < runtime.game.maxPlayers()) {
                    result.add(roomView(runtime, viewerUserId));
                }
            }
        }
        return List.copyOf(result);
    }

    @Transactional
    public DrawGuessRoomVO joinRoom(Long userId, String roomCode, String password) {
        String code = roomCode == null ? "" : roomCode.trim().toUpperCase();
        if (!ROOM_CODE_PATTERN.matcher(code).matches()) throw notFound("房间码无效或已过期");
        DrawGuessRoom room = roomMapper.selectOne(new QueryWrapper<DrawGuessRoom>().eq("room_code", code));
        if (room == null) throw notFound("没有找到这个房间");
        if (Boolean.TRUE.equals(room.getPrivateRoom())
                && !DrawGuessRoomAccessPolicy.canJoin(true, room.getPasswordHash(), password)) {
            throw forbidden("房间密码不正确");
        }
        RuntimeRoom runtime = requireRuntime(room.getId());
        User user = requireUser(userId);
        synchronized (runtime) {
            DrawGuessMember existing = member(room.getId(), userId);
            if (existing != null && Boolean.TRUE.equals(existing.getActive())) {
                updateLastVisited(existing);
                return roomView(runtime, userId);
            }
            if (runtime.game.status() != DrawGuessGame.Status.WAITING) {
                throw badRequest("游戏已经开始，不能加入新玩家");
            }
            if (runtime.game.playerCount() >= runtime.game.maxPlayers()) throw badRequest("房间人数已满");
            int seat = nextAvailableSeat(room.getId(), runtime);
            if (existing == null) {
                memberMapper.insert(newMember(room.getId(), userId, seat, user));
            } else {
                existing.setActive(true);
                existing.setSeatNo(seat);
                existing.setScore(0);
                existing.setNickname(safeNickname(user.getNickname()));
                existing.setAvatar(safeAvatar(user.getAvatar()));
                existing.setLastVisitedAt(LocalDateTime.now());
                memberMapper.updateById(existing);
            }
            runtime.game.addPlayer(userId, user.getNickname(), safeAvatar(user.getAvatar()));
            runtime.entity.setUpdatedAt(LocalDateTime.now());
            roomMapper.updateById(runtime.entity);
            broadcastRoomState(runtime);
            return roomView(runtime, userId);
        }
    }

    public DrawGuessRoomVO getRoom(Long roomId, Long viewerUserId) {
        RuntimeRoom runtime = requireRuntime(roomId);
        synchronized (runtime) {
            requireActiveMember(roomId, viewerUserId);
            DrawGuessMember member = member(roomId, viewerUserId);
            updateLastVisited(member);
            return roomView(runtime, viewerUserId);
        }
    }

    public List<DrawGuessRoomVO> recentRooms(Long userId) {
        List<DrawGuessMember> recent = memberMapper.selectList(new QueryWrapper<DrawGuessMember>()
                .eq("user_id", userId)
                .eq("active", true)
                .orderByDesc("last_visited_at")
                .last("LIMIT 10"));
        List<DrawGuessRoomVO> result = new ArrayList<>();
        Set<Long> seen = ConcurrentHashMap.newKeySet();
        for (DrawGuessMember entry : recent) {
            if (!Boolean.TRUE.equals(entry.getActive())) continue;
            if (!seen.add(entry.getRoomId())) continue;
            RuntimeRoom runtime = liveRooms.get(entry.getRoomId());
            if (runtime == null) continue;
            synchronized (runtime) {
                result.add(roomView(runtime, userId));
            }
        }
        return List.copyOf(result);
    }

    @Transactional
    public DrawGuessRoomVO startRoom(Long roomId, Long userId) {
        RuntimeRoom runtime = requireRuntime(roomId);
        synchronized (runtime) {
            requireActiveMember(roomId, userId);
            String firstWord = pickWord();
            runtime.game.start(userId, firstWord, Instant.now());
            runtime.entity.setStatus(DrawGuessGame.Status.PLAYING.name());
            runtime.entity.setCurrentTurn(runtime.game.turnNumber());
            runtime.entity.setUpdatedAt(LocalDateTime.now());
            roomMapper.updateById(runtime.entity);
            openRound(runtime);
            scheduleTimer(runtime);
            broadcastRoundStarted(runtime);
            broadcastRoomState(runtime);
            sendPrivateTurn(runtime);
            return roomView(runtime, userId);
        }
    }

    public DrawGuessRoomVO leaveRoom(Long roomId, Long userId) {
        RuntimeRoom runtime = requireRuntime(roomId);
        synchronized (runtime) {
            DrawGuessMember member = requireActiveMember(roomId, userId);
            if (runtime.game.status() == DrawGuessGame.Status.PLAYING) {
                // Keep the seat in the active round so its order and score stay deterministic.
                sessionRegistry.closeUserSessions(roomId, userId);
                return roomView(runtime, userId);
            }

            if (runtime.game.status() == DrawGuessGame.Status.FINISHED) {
                // A completed game keeps its roster and owner for the result view; leaving only
                // marks this membership inactive and must not run waiting-room seat operations.
                sessionRegistry.closeUserSessions(roomId, userId);
                member.setActive(false);
                member.setLastVisitedAt(LocalDateTime.now());
                memberMapper.updateById(member);
                broadcastRoomState(runtime);
                return roomView(runtime, userId);
            }

            if (userId == runtime.game.ownerUserId() && runtime.game.playerCount() > 1) {
                Long nextOwner = runtime.game.players().stream().map(DrawGuessGame.PlayerView::userId)
                        .filter(id -> !id.equals(userId)).findFirst().orElseThrow();
                runtime.game.transferOwner(nextOwner);
                runtime.entity.setOwnerUserId(nextOwner);
                roomMapper.updateById(runtime.entity);
            }
            runtime.game.removeWaitingPlayer(userId);
            member.setActive(false);
            member.setLastVisitedAt(LocalDateTime.now());
            memberMapper.updateById(member);
            if (runtime.game.playerCount() == 0) {
                runtime.entity.setStatus(DrawGuessGame.Status.FINISHED.name());
                roomMapper.updateById(runtime.entity);
                liveRooms.remove(roomId, runtime);
                cancelTimer(runtime);
            } else {
                broadcastRoomState(runtime);
            }
            return roomView(runtime, userId);
        }
    }

    @Transactional
    public UploadVO uploadSnapshot(Long roomId, Long roundId, Long userId, MultipartFile file) {
        RuntimeRoom runtime = requireRuntime(roomId);
        synchronized (runtime) {
            requireActiveMember(roomId, userId);
            DrawGuessRound round = runtime.currentRound != null && runtime.currentRound.getId().equals(roundId)
                    ? runtime.currentRound : roundMapper.selectById(roundId);
            boolean activeRound = round != null && round == runtime.currentRound
                    && "PLAYING".equals(round.getStatus());
            boolean completedRound = round != null && "FINISHED".equals(round.getStatus());
            if (round == null || !roomId.equals(round.getRoomId())
                    || !round.getDrawerUserId().equals(userId) || (!activeRound && !completedRound)) {
                throw forbidden("只有这回合的画手可以保存作品");
            }
            if (round.getSnapshotResourceId() != null) throw badRequest("这回合的作品已经保存");
            UploadVO upload = uploadService.uploadImage(userId, file);
            round.setSnapshotResourceId(upload.getResourceId());
            roundMapper.updateById(round);
            return upload;
        }
    }

    public List<DrawGuessRecordVO> listRecords() {
        List<DrawGuessRound> rounds = roundMapper.selectPublicGalleryRounds(8);
        if (rounds == null || rounds.isEmpty()) return List.of();
        List<DrawGuessRecordVO> records = new ArrayList<>();
        for (DrawGuessRound round : rounds) {
            DrawGuessRoom room = roomMapper.selectById(round.getRoomId());
            UploadResource upload = uploadResourceMapper.selectById(round.getSnapshotResourceId());
            User drawer = userMapper.selectById(round.getDrawerUserId());
            if (room == null || upload == null) continue;
            Instant endedAt = round.getEndedAt() == null ? null : toInstant(round.getEndedAt());
            records.add(new DrawGuessRecordVO(round.getId(), room.getId(), room.getRoomCode(),
                    room.getTitle(), round.getTurnNumber(), round.getWord(), round.getDrawerUserId(),
                    drawer == null ? "校园同学" : safeNickname(drawer.getNickname()),
                    upload.getResourceUrl(), endedAt));
        }
        return List.copyOf(records);
    }

    public boolean isActiveMember(Long roomId, Long userId) {
        if (roomId == null || userId == null || !liveRooms.containsKey(roomId)) return false;
        return memberMapper.selectCount(new QueryWrapper<DrawGuessMember>()
                .eq("room_id", roomId).eq("user_id", userId).eq("active", true)) > 0;
    }

    public void onConnected(Long roomId, Long userId) {
        RuntimeRoom runtime = requireRuntime(roomId);
        synchronized (runtime) {
            DrawGuessMember member = requireActiveMember(roomId, userId);
            updateLastVisited(member);
            broadcastRoomState(runtime);
            sendPrivateTurn(runtime);
        }
    }

    public void onDisconnected(Long roomId) {
        RuntimeRoom runtime = liveRooms.get(roomId);
        if (runtime == null) return;
        synchronized (runtime) {
            broadcastRoomState(runtime);
        }
    }

    public void handleSocketMessage(Long roomId, Long userId, JsonNode message) {
        RuntimeRoom runtime = requireRuntime(roomId);
        if (message == null || !message.isObject()) {
            sessionRegistry.sendToUser(roomId, userId, event("error", "message", "消息格式错误"));
            return;
        }
        String type = message.path("type").asText("");
        if ("ping".equals(type)) {
            sessionRegistry.sendToUser(roomId, userId, event("pong", "at", Instant.now()));
            return;
        }
        synchronized (runtime) {
            if (!runtime.game.containsPlayer(userId)) throw forbidden("你不是这个房间的成员");
            if (!allowSocketEvent(runtime, userId, type)) {
                sessionRegistry.sendToUser(roomId, userId, event("error", "message", "操作太频繁，请稍后再试"));
                return;
            }
            switch (type) {
                case "draw" -> draw(runtime, userId, message);
                case "clear" -> clearCanvas(runtime, userId);
                case "guess" -> submitGuess(runtime, userId, message.path("text").asText(null));
                case "chat" -> sendChat(runtime, userId, message.path("text").asText(null));
                case "skip" -> skipTurn(runtime, userId);
                case "start" -> startRoom(roomId, userId);
                default -> sessionRegistry.sendToUser(roomId, userId,
                        event("error", "message", "不支持这类房间消息"));
            }
        }
    }

    private void draw(RuntimeRoom runtime, Long userId, JsonNode message) {
        if (!runtime.game.canDraw(userId, Instant.now())) {
            sessionRegistry.sendToUser(runtime.entity.getId(), userId, event("error", "message", "现在不是你的作画回合"));
            return;
        }
        Map<String, Object> stroke = DrawGuessStrokePolicy.validate(message);
        if (runtime.strokes.size() >= MAX_STROKES_PER_ROOM) {
            sessionRegistry.sendToUser(runtime.entity.getId(), userId, event("error", "message", "这一回合笔画太多，请清空画布后继续"));
            return;
        }
        Map<String, Object> stored = Map.of("userId", userId, "stroke", stroke);
        runtime.strokes.add(stored);
        sessionRegistry.broadcast(runtime.entity.getId(), event("stroke", "userId", userId, "stroke", stroke));
    }

    private void clearCanvas(RuntimeRoom runtime, Long userId) {
        if (!runtime.game.canDraw(userId, Instant.now())) {
            sessionRegistry.sendToUser(runtime.entity.getId(), userId, event("error", "message", "只有当前画手可以清空画布"));
            return;
        }
        runtime.strokes.clear();
        sessionRegistry.broadcast(runtime.entity.getId(), event("canvas_cleared", "userId", userId));
    }

    private void submitGuess(RuntimeRoom runtime, Long userId, String rawText) {
        String text = safeChatText(rawText);
        Instant now = Instant.now();
        DrawGuessGame.GuessResult result = runtime.game.guess(userId, text, now);
        if (!result.accepted()) {
            String reason = runtime.game.canDraw(userId) ? "画手不能猜词" : "你已经猜对了，等下一回合吧";
            sessionRegistry.sendToUser(runtime.entity.getId(), userId, event("error", "message", reason));
            return;
        }
        if (result.correct()) {
            persistScores(runtime);
            DrawGuessMember member = member(runtime.entity.getId(), userId);
            String nickname = member == null ? "校园同学" : member.getNickname();
            DrawGuessChatMessageVO chatMessage = addChatMessage(runtime, userId, nickname,
                    nickname + " 猜对了", true, now);
            sessionRegistry.broadcast(runtime.entity.getId(), event("guess_result",
                    "userId", userId, "nickname", nickname, "correct", true,
                    "pointsAwarded", result.pointsAwarded(), "message", chatMessage,
                    "advanceAt", runtime.game.advanceAt()));
            if (result.earlyAdvanceScheduled()) {
                sessionRegistry.broadcast(runtime.entity.getId(), event("advance_scheduled",
                        "at", runtime.game.advanceAt(), "seconds", DrawGuessGame.EARLY_ADVANCE_SECONDS));
            }
        } else {
            DrawGuessMember member = member(runtime.entity.getId(), userId);
            DrawGuessChatMessageVO chatMessage = addChatMessage(runtime, userId,
                    member == null ? "校园同学" : member.getNickname(), text, false, now);
            sessionRegistry.broadcast(runtime.entity.getId(), event("chat_message", "message", chatMessage));
        }
        broadcastRoomState(runtime);
    }

    private void sendChat(RuntimeRoom runtime, Long userId, String rawText) {
        String text = safeChatText(rawText);
        DrawGuessMember member = member(runtime.entity.getId(), userId);
        DrawGuessChatMessageVO chatMessage = addChatMessage(runtime, userId,
                member == null ? "校园同学" : member.getNickname(), text, false, Instant.now());
        sessionRegistry.broadcast(runtime.entity.getId(), event("chat_message", "message", chatMessage));
    }

    private void skipTurn(RuntimeRoom runtime, Long userId) {
        DrawGuessGame.TurnTransition transition = runtime.game.skip(userId, Instant.now(), pickWord());
        finishTurn(runtime, transition);
    }

    private DrawGuessChatMessageVO addChatMessage(RuntimeRoom runtime, Long userId, String nickname,
                                                   String content, boolean correct, Instant createdAt) {
        DrawGuessChatMessageVO message = new DrawGuessChatMessageVO(
                java.util.UUID.randomUUID().toString(), userId, nickname, content, correct, createdAt);
        runtime.messages.addLast(message);
        while (runtime.messages.size() > MAX_CHAT_MESSAGES) runtime.messages.removeFirst();
        return message;
    }

    private String safeChatText(String rawText) {
        if (rawText == null) throw badRequest("消息不能为空");
        String value = stripControls.matcher(rawText.trim()).replaceAll("");
        if (value.isBlank() || value.codePointCount(0, value.length()) > 240) {
            throw badRequest("消息不能为空且不能超过240个字符");
        }
        return value;
    }

    private boolean allowSocketEvent(RuntimeRoom runtime, Long userId, String type) {
        Instant now = Instant.now();
        boolean drawEvent = "draw".equals(type);
        String key = userId + (drawEvent ? ":draw" : ":message");
        Deque<Instant> events = runtime.socketRate.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        Instant cutoff = now.minusSeconds(drawEvent ? 1 : 10);
        while (!events.isEmpty() && events.peekFirst().isBefore(cutoff)) events.removeFirst();
        int limit = drawEvent ? 60 : MAX_SOCKET_MESSAGES_PER_10_SECONDS;
        if (events.size() >= limit) return false;
        events.addLast(now);
        return true;
    }

    private void persistScores(RuntimeRoom runtime) {
        for (Map.Entry<Long, Integer> score : runtime.game.scores().entrySet()) {
            DrawGuessMember member = member(runtime.entity.getId(), score.getKey());
            if (member == null) continue;
            member.setScore(score.getValue());
            memberMapper.updateById(member);
        }
    }

    private User requireUser(Long userId) {
        if (userId == null) throw new BizException(ResultCode.UNAUTHORIZED);
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != null && user.getStatus() != 0) {
            throw new BizException(ResultCode.UNAUTHORIZED, "账号当前不可用");
        }
        return user;
    }

    private DrawGuessMember newMember(Long roomId, Long userId, int seat, User user) {
        DrawGuessMember member = new DrawGuessMember();
        member.setRoomId(roomId);
        member.setUserId(userId);
        member.setSeatNo(seat);
        member.setNickname(safeNickname(user.getNickname()));
        member.setAvatar(safeAvatar(user.getAvatar()));
        member.setScore(0);
        member.setActive(true);
        member.setJoinedAt(LocalDateTime.now());
        member.setLastVisitedAt(LocalDateTime.now());
        return member;
    }

    private int nextAvailableSeat(Long roomId, RuntimeRoom runtime) {
        List<DrawGuessMember> activeMembers = memberMapper.selectList(new QueryWrapper<DrawGuessMember>()
                .eq("room_id", roomId).eq("active", true));
        Set<Integer> occupied = activeMembers == null ? Set.of() : activeMembers.stream()
                .map(DrawGuessMember::getSeatNo).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        for (int seat = 1; seat <= runtime.game.maxPlayers(); seat++) {
            if (!occupied.contains(seat)) return seat;
        }
        throw badRequest("房间人数已满");
    }

    private String generateRoomCode() {
        for (int attempt = 0; attempt < 12; attempt++) {
            StringBuilder code = new StringBuilder(6);
            for (int index = 0; index < 6; index++) {
                code.append("ABCDEFGHJKLMNPQRSTUVWXYZ23456789".charAt(
                        ThreadLocalRandom.current().nextInt(32)));
            }
            if (roomMapper.selectCount(new QueryWrapper<DrawGuessRoom>().eq("room_code", code.toString())) == 0) {
                return code.toString();
            }
        }
        throw new BizException(ResultCode.SYSTEM_ERROR, "暂时无法分配房间码，请重试");
    }

    private String safeTitle(String title) {
        String value = title == null ? "校园画画房" : stripControls.matcher(title.trim()).replaceAll("");
        if (value.isBlank()) value = "校园画画房";
        return value.length() > 40 ? value.substring(0, 40) : value;
    }

    private String safeNickname(String nickname) {
        String value = nickname == null ? "校园同学" : nickname.trim();
        if (value.isBlank()) value = "校园同学";
        return value.length() > 40 ? value.substring(0, 40) : value;
    }

    private String safeAvatar(String avatar) {
        if (avatar == null || avatar.isBlank() || avatar.startsWith("data:") || avatar.length() > 4096) return null;
        return avatar;
    }

    private String pickWord() {
        List<DrawGuessWord> words = wordMapper.selectList(new QueryWrapper<DrawGuessWord>().eq("active", true));
        if (words == null || words.isEmpty()) {
            return FALLBACK_WORDS[ThreadLocalRandom.current().nextInt(FALLBACK_WORDS.length)];
        }
        return words.get(ThreadLocalRandom.current().nextInt(words.size())).getWord();
    }

    private DrawGuessRoomVO roomView(RuntimeRoom runtime, Long viewerUserId) {
        DrawGuessGame game = runtime.game;
        Set<Long> onlineUsers = sessionRegistry.onlineUserIds(runtime.entity.getId());
        List<DrawGuessPlayerVO> players = game.players().stream()
                .map(player -> new DrawGuessPlayerVO(player.userId(), player.nickname(), player.avatar(),
                        player.score(), onlineUsers.contains(player.userId()), player.userId() == game.ownerUserId()))
                .toList();
        Long drawerUserId = game.drawerUserId();
        String privateAnswer = drawerUserId == null ? null : game.answerFor(drawerUserId);
        int answerLength = privateAnswer == null ? 0 : privateAnswer.codePointCount(0, privateAnswer.length());
        Instant deadline = game.deadline();
        int remaining = deadline == null ? 0 : (int) Math.max(0, Duration.between(Instant.now(), deadline).toSeconds());
        List<DrawGuessCompletedArtworkVO> pendingArtworks = pendingCompletedArtworks(runtime, viewerUserId);
        return new DrawGuessRoomVO(runtime.entity.getId(), runtime.entity.getRoomCode(), runtime.entity.getTitle(),
                Boolean.TRUE.equals(runtime.entity.getPrivateRoom()), game.status().name(), game.ownerUserId(),
                drawerUserId, game.playerCount(), onlineUsers.size(), game.maxPlayers(), game.roundsPerPlayer(),
                game.turnNumber(), game.totalTurns(), remaining, answerLength,
                viewerUserId != null && viewerUserId == game.ownerUserId(),
                viewerUserId != null && drawerUserId != null && viewerUserId.equals(drawerUserId),
                players, List.copyOf(runtime.strokes), List.copyOf(runtime.messages),
                runtime.currentRound == null ? null : runtime.currentRound.getId(), pendingArtworks);
    }

    private List<DrawGuessCompletedArtworkVO> pendingCompletedArtworks(RuntimeRoom runtime, Long viewerUserId) {
        if (viewerUserId == null || (runtime.game.status() != DrawGuessGame.Status.FINISHED
                && runtime.game.turnNumber() <= 1)) return List.of();
        Long roomId = runtime.entity.getId();
        DrawGuessMember viewer = member(roomId, viewerUserId);
        if (viewer == null || !Boolean.TRUE.equals(viewer.getActive())
                || !viewerUserId.equals(viewer.getUserId())) return List.of();

        List<DrawGuessRound> rounds = roundMapper.selectList(new QueryWrapper<DrawGuessRound>()
                .eq("room_id", roomId)
                .eq("drawer_user_id", viewerUserId)
                .eq("status", "FINISHED")
                .isNull("snapshot_resource_id")
                .isNotNull("drawing_data")
                .orderByAsc("turn_number"));
        if (rounds == null || rounds.isEmpty()) return List.of();

        List<DrawGuessCompletedArtworkVO> pending = new ArrayList<>();
        for (DrawGuessRound round : rounds) {
            if (!roomId.equals(round.getRoomId()) || !viewerUserId.equals(round.getDrawerUserId())
                    || !"FINISHED".equals(round.getStatus()) || round.getSnapshotResourceId() != null
                    || round.getDrawingData() == null) continue;
            try {
                List<Map<String, Object>> strokes = STROKE_JSON.readValue(round.getDrawingData(),
                        new TypeReference<List<Map<String, Object>>>() { });
                if (strokes != null) {
                    pending.add(new DrawGuessCompletedArtworkVO(round.getId(), round.getTurnNumber(), strokes));
                }
            } catch (JsonProcessingException ignored) {
                // One invalid payload must not prevent a member from loading the room.
            }
        }
        return List.copyOf(pending);
    }

    private RuntimeRoom requireRuntime(Long roomId) {
        RuntimeRoom runtime = roomId == null ? null : liveRooms.get(roomId);
        if (runtime == null) throw notFound("房间不存在或已经结束");
        return runtime;
    }

    private DrawGuessMember requireActiveMember(Long roomId, Long userId) {
        DrawGuessMember member = member(roomId, userId);
        if (member == null || !Boolean.TRUE.equals(member.getActive())) throw forbidden("你不是这个房间的成员");
        return member;
    }

    private DrawGuessMember member(Long roomId, Long userId) {
        if (roomId == null || userId == null) return null;
        return memberMapper.selectOne(new QueryWrapper<DrawGuessMember>()
                .eq("room_id", roomId).eq("user_id", userId));
    }

    private void updateLastVisited(DrawGuessMember member) {
        if (member == null) return;
        member.setLastVisitedAt(LocalDateTime.now());
        memberMapper.updateById(member);
    }

    private void openRound(RuntimeRoom runtime) {
        DrawGuessRound round = new DrawGuessRound();
        round.setRoomId(runtime.entity.getId());
        round.setTurnNumber(runtime.game.turnNumber());
        round.setDrawerUserId(runtime.game.drawerUserId());
        round.setWord(runtime.game.answerFor(runtime.game.drawerUserId()));
        round.setStatus("PLAYING");
        round.setStartedAt(LocalDateTime.now());
        roundMapper.insert(round);
        runtime.currentRound = round;
        runtime.strokes.clear();
    }

    private void scheduleTimer(RuntimeRoom runtime) {
        cancelTimer(runtime);
        Long roomId = runtime.entity.getId();
        runtime.timer = timerExecutor.scheduleAtFixedRate(() -> tick(roomId), 1, 1, TimeUnit.SECONDS);
    }

    private void cancelTimer(RuntimeRoom runtime) {
        if (runtime.timer != null) {
            runtime.timer.cancel(false);
            runtime.timer = null;
        }
    }

    private void tick(Long roomId) {
        RuntimeRoom runtime = liveRooms.get(roomId);
        if (runtime == null) return;
        synchronized (runtime) {
            if (runtime.game.status() != DrawGuessGame.Status.PLAYING) {
                cancelTimer(runtime);
                return;
            }
            Instant now = Instant.now();
            boolean due = runtime.game.deadline() != null && !now.isBefore(runtime.game.deadline())
                    || runtime.game.advanceAt() != null && !now.isBefore(runtime.game.advanceAt());
            DrawGuessGame.TurnTransition transition = runtime.game.advanceIfDue(now, due ? pickWord() : null);
            if (transition != null) finishTurn(runtime, transition);
            else sessionRegistry.broadcast(roomId, event("timer", "remainingSeconds", roomView(runtime, null).remainingSeconds()));
        }
    }

    private void finishCurrentRound(RuntimeRoom runtime) {
        if (runtime.currentRound == null) return;
        try {
            runtime.currentRound.setDrawingData(STROKE_JSON.writeValueAsString(runtime.strokes));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存本回合画作数据", exception);
        }
        runtime.currentRound.setStatus("FINISHED");
        runtime.currentRound.setEndedAt(LocalDateTime.now());
        roundMapper.updateById(runtime.currentRound);
    }

    private void finishTurn(RuntimeRoom runtime, DrawGuessGame.TurnTransition transition) {
        finishCurrentRound(runtime);
        runtime.strokes.clear();
        runtime.entity.setCurrentTurn(runtime.game.turnNumber());
        runtime.entity.setUpdatedAt(LocalDateTime.now());
        sessionRegistry.broadcast(runtime.entity.getId(), event("round_ended",
                "turnNumber", transition.finishedTurnNumber(),
                "roundId", runtime.currentRound == null ? null : runtime.currentRound.getId(),
                "drawerUserId", transition.finishedDrawerUserId(),
                "answer", transition.revealedAnswer(),
                "scores", runtime.game.scores(),
                "roomFinished", transition.roomFinished()));
        if (transition.roomFinished()) {
            runtime.entity.setStatus(DrawGuessGame.Status.FINISHED.name());
            runtime.currentRound = null;
            roomMapper.updateById(runtime.entity);
            cancelTimer(runtime);
            sessionRegistry.broadcast(runtime.entity.getId(), event("room_ended", "room", roomView(runtime, null)));
            return;
        }
        roomMapper.updateById(runtime.entity);
        openRound(runtime);
        broadcastRoundStarted(runtime);
        broadcastRoomState(runtime);
        sendPrivateTurn(runtime);
    }

    private void broadcastRoundStarted(RuntimeRoom runtime) {
        sessionRegistry.broadcast(runtime.entity.getId(), event("round_started",
                "turnNumber", runtime.game.turnNumber(),
                "totalTurns", runtime.game.totalTurns(),
                "drawerUserId", runtime.game.drawerUserId(),
                "deadline", runtime.game.deadline()));
    }

    private void sendPrivateTurn(RuntimeRoom runtime) {
        Long drawer = runtime.game.drawerUserId();
        if (drawer == null) return;
        sessionRegistry.sendToUser(runtime.entity.getId(), drawer, event("private_turn",
                "turnNumber", runtime.game.turnNumber(),
                "word", runtime.game.answerFor(drawer),
                "deadline", runtime.game.deadline()));
    }

    private void broadcastRoomState(RuntimeRoom runtime) {
        for (org.springframework.web.socket.WebSocketSession session : sessionRegistry.sessions(runtime.entity.getId())) {
            Object user = session.getAttributes().get(
                    com.campus.platform.module.drawgame.websocket.DrawGuessSessionRegistry.USER_ID_ATTRIBUTE);
            if (!(user instanceof Number number)) continue;
            sessionRegistry.sendToSession(session, event("room_state", "room", roomView(runtime, number.longValue())));
        }
    }

    private static Map<String, Object> event(String type, Object... fields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        for (int index = 0; index + 1 < fields.length; index += 2) payload.put((String) fields[index], fields[index + 1]);
        return payload;
    }

    private static Instant toInstant(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static BizException badRequest(String message) {
        return new BizException(ResultCode.BAD_REQUEST, message);
    }

    private static BizException forbidden(String message) {
        return new BizException(ResultCode.FORBIDDEN, message);
    }

    private static BizException notFound(String message) {
        return new BizException(ResultCode.NOT_FOUND, message);
    }
}
