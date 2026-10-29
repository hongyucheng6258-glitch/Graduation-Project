package com.campus.platform.module.drawgame.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.module.drawgame.dto.DrawGuessCreateRoomDTO;
import com.campus.platform.module.drawgame.entity.DrawGuessMember;
import com.campus.platform.module.drawgame.entity.DrawGuessRoom;
import com.campus.platform.module.drawgame.entity.DrawGuessRound;
import com.campus.platform.module.drawgame.mapper.DrawGuessMemberMapper;
import com.campus.platform.module.drawgame.mapper.DrawGuessRoomMapper;
import com.campus.platform.module.drawgame.mapper.DrawGuessRoundMapper;
import com.campus.platform.module.drawgame.mapper.DrawGuessWordMapper;
import com.campus.platform.module.drawgame.vo.DrawGuessRoomVO;
import com.campus.platform.module.drawgame.websocket.DrawGuessSessionRegistry;
import com.campus.platform.module.upload.mapper.UploadResourceMapper;
import com.campus.platform.module.upload.service.UploadService;
import com.campus.platform.module.upload.vo.UploadVO;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DrawGuessRoomServiceTest {
    private final DrawGuessRoomMapper roomMapper = mock(DrawGuessRoomMapper.class);
    private final DrawGuessMemberMapper memberMapper = mock(DrawGuessMemberMapper.class);
    private final DrawGuessRoundMapper roundMapper = mock(DrawGuessRoundMapper.class);
    private final DrawGuessWordMapper wordMapper = mock(DrawGuessWordMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UploadResourceMapper uploadResourceMapper = mock(UploadResourceMapper.class);
    private final UploadService uploadService = mock(UploadService.class);
    private final DrawGuessSessionRegistry sessions = new DrawGuessSessionRegistry(new ObjectMapper());
    private final List<DrawGuessMember> members = new ArrayList<>();
    private DrawGuessRoomService service;
    private DrawGuessRoom insertedRoom;

    @BeforeEach
    void setUp() {
        service = new DrawGuessRoomService(roomMapper, memberMapper, roundMapper, wordMapper,
                userMapper, uploadResourceMapper, uploadService, sessions);
        User owner = new User();
        owner.setId(7L);
        owner.setNickname("画手小林");
        owner.setAvatar("/uploads/avatar.png");
        owner.setStatus(0);
        when(userMapper.selectById(7L)).thenReturn(owner);
        User returningPlayer = new User();
        returningPlayer.setId(8L);
        returningPlayer.setNickname("小周");
        returningPlayer.setStatus(0);
        when(userMapper.selectById(8L)).thenReturn(returningPlayer);
        when(roomMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(roomMapper.selectOne(any(QueryWrapper.class))).thenAnswer(invocation -> insertedRoom);
        when(roundMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(roomMapper.insert(any(DrawGuessRoom.class))).thenAnswer(invocation -> {
            insertedRoom = invocation.getArgument(0);
            insertedRoom.setId(88L);
            return 1;
        });
        when(memberMapper.insert(any(DrawGuessMember.class))).thenAnswer(invocation -> {
            members.add(invocation.getArgument(0));
            return 1;
        });
        when(memberMapper.selectOne(any(QueryWrapper.class))).thenAnswer(invocation -> members.stream()
                .filter(member -> member.getRoomId().equals(insertedRoom.getId()))
                .findFirst().orElse(null));
        when(memberMapper.selectList(any(QueryWrapper.class))).thenAnswer(invocation -> members.stream()
                .filter(member -> Boolean.TRUE.equals(member.getActive())).toList());
        when(memberMapper.updateById(any(DrawGuessMember.class))).thenReturn(1);
        when(roomMapper.updateById(any(DrawGuessRoom.class))).thenReturn(1);
    }

    @AfterEach
    void stopTimerPool() {
        service.stopTimers();
    }

    @Test
    void privateRoomKeepsPasswordHashedAndNeverReturnsItInRoomView() throws Exception {
        DrawGuessCreateRoomDTO request = new DrawGuessCreateRoomDTO();
        request.setTitle("课间速写");
        request.setPrivateRoom(true);
        request.setPassword("secret123");

        DrawGuessRoomVO view = service.createRoom(7L, request);
        String json = new ObjectMapper().writeValueAsString(view);

        assertTrue(insertedRoom.getPasswordHash().startsWith("$2"));
        assertNotEquals("secret123", insertedRoom.getPasswordHash());
        assertFalse(json.contains("secret123"));
        assertFalse(json.contains("passwordHash"));
        assertEquals(1, view.playerCount());
        assertTrue(view.isOwner());
    }

    @Test
    void ownerCanCloseTheirEmptyWaitingRoomAndItCannotBeReopened() {
        DrawGuessRoomVO created = service.createRoom(7L, new DrawGuessCreateRoomDTO());

        DrawGuessRoomVO afterLeave = service.leaveRoom(created.id(), 7L);

        assertEquals("FINISHED", afterLeave.status());
        assertEquals(0, afterLeave.playerCount());
        assertFalse(members.get(0).getActive());
        assertThrows(BizException.class, () -> service.getRoom(created.id(), 7L));
    }

    @Test
    void ownerCanLeaveAFinishedGameWithoutTransferringOwnership() throws Exception {
        AtomicInteger membershipLookups = new AtomicInteger();
        AtomicLong roundIds = new AtomicLong(900L);
        org.mockito.Mockito.doAnswer(invocation -> membershipLookups.getAndIncrement() == 0
                ? null : members.get(0)).when(memberMapper).selectOne(any(QueryWrapper.class));
        when(wordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(roundMapper.insert(any(DrawGuessRound.class))).thenAnswer(invocation -> {
            DrawGuessRound round = invocation.getArgument(0);
            round.setId(roundIds.incrementAndGet());
            return 1;
        });
        DrawGuessCreateRoomDTO request = new DrawGuessCreateRoomDTO();
        request.setRoundsPerPlayer(1);
        DrawGuessRoomVO created = service.createRoom(7L, request);
        service.joinRoom(8L, created.roomCode(), null);
        service.startRoom(created.id(), 7L);
        ObjectMapper json = new ObjectMapper();

        service.handleSocketMessage(created.id(), 7L, json.readTree("{\"type\":\"skip\"}"));
        service.handleSocketMessage(created.id(), 8L, json.readTree("{\"type\":\"skip\"}"));
        DrawGuessRoomVO afterLeave = service.leaveRoom(created.id(), 7L);

        assertEquals("FINISHED", afterLeave.status());
        assertEquals(7L, insertedRoom.getOwnerUserId());
        assertFalse(members.get(0).getActive());
    }

    @Test
    void recentRoomsIgnoreInactiveMembershipsAndFilterThemBeforeTheLimit() {
        DrawGuessRoomVO created = service.createRoom(7L, new DrawGuessCreateRoomDTO());
        DrawGuessMember inactiveMembership = new DrawGuessMember();
        inactiveMembership.setRoomId(created.id());
        inactiveMembership.setUserId(7L);
        inactiveMembership.setActive(false);
        when(memberMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(inactiveMembership));

        List<DrawGuessRoomVO> recent = service.recentRooms(7L);

        assertTrue(recent.isEmpty());
        ArgumentCaptor<QueryWrapper<DrawGuessMember>> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(memberMapper).selectList(query.capture());
        assertTrue(query.getValue().getSqlSegment().toLowerCase().contains("active"));
    }

    @Test
    void returningPlayerGetsAnUnoccupiedSeatAfterLeavingAndRejoining() {
        AtomicInteger membershipLookups = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> membershipLookups.getAndIncrement() == 0
                ? null : members.get(0)).when(memberMapper).selectOne(any(QueryWrapper.class));
        DrawGuessRoomVO created = service.createRoom(7L, new DrawGuessCreateRoomDTO());
        service.joinRoom(8L, created.roomCode(), null);
        service.leaveRoom(created.id(), 7L);

        DrawGuessRoomVO rejoined = service.joinRoom(7L, created.roomCode(), null);
        List<Integer> occupiedSeats = members.stream().filter(member -> Boolean.TRUE.equals(member.getActive()))
                .map(DrawGuessMember::getSeatNo).toList();

        assertEquals(2, rejoined.playerCount());
        assertEquals(2, occupiedSeats.stream().distinct().count());
        assertTrue(occupiedSeats.contains(1));
        assertTrue(occupiedSeats.contains(2));
    }

    @Test
    void playingRoomSnapshotIncludesTheCurrentRoundIdForTheDrawer() {
        AtomicInteger membershipLookups = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> membershipLookups.getAndIncrement() == 0
                ? null : members.get(0)).when(memberMapper).selectOne(any(QueryWrapper.class));
        when(wordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(roundMapper.insert(any(DrawGuessRound.class))).thenAnswer(invocation -> {
            DrawGuessRound round = invocation.getArgument(0);
            round.setId(900L);
            return 1;
        });
        DrawGuessRoomVO created = service.createRoom(7L, new DrawGuessCreateRoomDTO());
        service.joinRoom(8L, created.roomCode(), null);

        DrawGuessRoomVO playing = service.startRoom(created.id(), 7L);

        assertEquals(900L, playing.currentRoundId());
        assertTrue(playing.isDrawer());
    }

    @Test
    void completedRoundPersistsItsDrawingStrokesBeforeTheNextTurnStarts() throws Exception {
        List<DrawGuessRound> insertedRounds = new ArrayList<>();
        AtomicLong roundIds = new AtomicLong(899L);
        AtomicInteger membershipLookups = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> membershipLookups.getAndIncrement() == 0
                ? null : members.get(0)).when(memberMapper).selectOne(any(QueryWrapper.class));
        when(wordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(roundMapper.insert(any(DrawGuessRound.class))).thenAnswer(invocation -> {
            DrawGuessRound round = invocation.getArgument(0);
            round.setId(roundIds.incrementAndGet());
            insertedRounds.add(round);
            return 1;
        });

        DrawGuessRoomVO created = service.createRoom(7L, new DrawGuessCreateRoomDTO());
        service.joinRoom(8L, created.roomCode(), null);
        DrawGuessRoomVO playing = service.startRoom(created.id(), 7L);
        String stroke = "{\"type\":\"draw\",\"points\":[{\"x\":0.25,\"y\":0.5}],"
                + "\"color\":\"#304d99\",\"width\":4,\"tool\":\"pen\"}";

        service.handleSocketMessage(created.id(), playing.drawerUserId(), new ObjectMapper().readTree(stroke));
        service.handleSocketMessage(created.id(), playing.drawerUserId(), new ObjectMapper().readTree("{\"type\":\"skip\"}"));

        JsonNode savedRound = new ObjectMapper().findAndRegisterModules().valueToTree(insertedRounds.get(0));
        assertTrue(savedRound.has("drawingData"));
        assertTrue(savedRound.get("drawingData").asText().contains("\"x\":0.25"));
        assertTrue(savedRound.get("drawingData").asText().contains("\"y\":0.5"));
    }

    @Test
    void reloadedRoomRestoresOnlyTheDrawersUnsavedCompletedArtwork() throws Exception {
        List<DrawGuessRound> insertedRounds = new ArrayList<>();
        AtomicLong roundIds = new AtomicLong(899L);
        AtomicInteger membershipLookups = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> membershipLookups.getAndIncrement() == 0
                ? null : members.get(0)).when(memberMapper).selectOne(any(QueryWrapper.class));
        when(wordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(roundMapper.insert(any(DrawGuessRound.class))).thenAnswer(invocation -> {
            DrawGuessRound round = invocation.getArgument(0);
            round.setId(roundIds.incrementAndGet());
            insertedRounds.add(round);
            return 1;
        });
        when(roundMapper.selectList(any(QueryWrapper.class))).thenAnswer(invocation -> insertedRounds.stream()
                .filter(round -> "FINISHED".equals(round.getStatus())).toList());

        DrawGuessRoomVO created = service.createRoom(7L, new DrawGuessCreateRoomDTO());
        service.joinRoom(8L, created.roomCode(), null);
        DrawGuessRoomVO playing = service.startRoom(created.id(), 7L);
        Long drawerUserId = playing.drawerUserId();
        Long otherUserId = drawerUserId.equals(7L) ? 8L : 7L;
        String stroke = "{\"type\":\"draw\",\"points\":[{\"x\":0.25,\"y\":0.5}],"
                + "\"color\":\"#304d99\",\"width\":4,\"tool\":\"pen\"}";
        service.handleSocketMessage(created.id(), drawerUserId, new ObjectMapper().readTree(stroke));
        service.handleSocketMessage(created.id(), drawerUserId, new ObjectMapper().readTree("{\"type\":\"skip\"}"));
        DrawGuessRound completedRound = insertedRounds.get(0);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();

        JsonNode drawerView = json.valueToTree(service.getRoom(created.id(), drawerUserId));

        JsonNode pending = drawerView.path("pendingArtworks");
        assertTrue(pending.isArray());
        assertEquals(1, pending.size());
        assertEquals(completedRound.getId().longValue(), pending.get(0).path("roundId").asLong());
        assertEquals(1, pending.get(0).path("strokes").size());
        assertEquals(0.25, pending.get(0).path("strokes").get(0).path("stroke")
                .path("points").get(0).path("x").asDouble());
        assertFalse(pending.get(0).has("word"));
        assertFalse(pending.get(0).has("answer"));

        JsonNode otherView = json.valueToTree(service.getRoom(created.id(), otherUserId));
        assertEquals(0, otherView.path("pendingArtworks").size());

        completedRound.setSnapshotResourceId(99L);
        JsonNode savedView = json.valueToTree(service.getRoom(created.id(), drawerUserId));
        assertEquals(0, savedView.path("pendingArtworks").size());
        completedRound.setSnapshotResourceId(null);
        members.get(0).setActive(false);
        assertThrows(BizException.class, () -> service.getRoom(created.id(), drawerUserId));
    }

    @Test
    void onlyTheRoundDrawerCanSaveOneSnapshotForThatRound() {
        AtomicInteger membershipLookups = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> membershipLookups.getAndIncrement() == 0
                ? null : members.get(0)).when(memberMapper).selectOne(any(QueryWrapper.class));
        when(wordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(roundMapper.insert(any(DrawGuessRound.class))).thenAnswer(invocation -> {
            DrawGuessRound round = invocation.getArgument(0);
            round.setId(900L);
            return 1;
        });
        MockMultipartFile image = new MockMultipartFile("file", "drawing.png", "image/png", new byte[]{1, 2, 3});
        when(uploadService.uploadImage(7L, image)).thenReturn(new UploadVO(51L, "/uploads/drawing.png"));
        DrawGuessRoomVO created = service.createRoom(7L, new DrawGuessCreateRoomDTO());
        service.joinRoom(8L, created.roomCode(), null);
        DrawGuessRoomVO playing = service.startRoom(created.id(), 7L);

        service.uploadSnapshot(created.id(), playing.currentRoundId(), 7L, image);

        assertThrows(BizException.class,
                () -> service.uploadSnapshot(created.id(), playing.currentRoundId(), 8L, image));
        assertThrows(BizException.class,
                () -> service.uploadSnapshot(created.id(), playing.currentRoundId(), 7L, image));
        verify(uploadService, times(1)).uploadImage(7L, image);
        verify(roundMapper).updateById(argThat((DrawGuessRound round) -> Long.valueOf(900L).equals(round.getId())
                && Long.valueOf(51L).equals(round.getSnapshotResourceId())));
    }

    @Test
    void drawerCanSaveACompletedRoundAfterTheNextTurnHasStarted() throws Exception {
        AtomicInteger membershipLookups = new AtomicInteger();
        AtomicLong roundIds = new AtomicLong(899L);
        List<DrawGuessRound> rounds = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> membershipLookups.getAndIncrement() == 0
                ? null : members.get(0)).when(memberMapper).selectOne(any(QueryWrapper.class));
        when(wordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(roundMapper.insert(any(DrawGuessRound.class))).thenAnswer(invocation -> {
            DrawGuessRound round = invocation.getArgument(0);
            round.setId(roundIds.incrementAndGet());
            rounds.add(round);
            return 1;
        });
        when(roundMapper.selectById(900L)).thenAnswer(invocation -> rounds.stream()
                .filter(round -> Long.valueOf(900L).equals(round.getId())).findFirst().orElse(null));
        MockMultipartFile image = new MockMultipartFile("file", "drawing.png", "image/png", new byte[]{1, 2, 3});
        when(uploadService.uploadImage(7L, image)).thenReturn(new UploadVO(52L, "/uploads/drawing.png"));
        DrawGuessRoomVO created = service.createRoom(7L, new DrawGuessCreateRoomDTO());
        service.joinRoom(8L, created.roomCode(), null);
        DrawGuessRoomVO playing = service.startRoom(created.id(), 7L);

        service.handleSocketMessage(created.id(), 7L, new ObjectMapper().readTree("{\"type\":\"skip\"}"));
        service.uploadSnapshot(created.id(), playing.currentRoundId(), 7L, image);

        assertEquals("FINISHED", rounds.get(0).getStatus());
        assertEquals(52L, rounds.get(0).getSnapshotResourceId());
        verify(uploadService).uploadImage(7L, image);
    }

    @Test
    void drawerCanSendStrokeWithNumericUserId() throws Exception {
        AtomicInteger membershipLookups = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> membershipLookups.getAndIncrement() == 0
                ? null : members.get(0)).when(memberMapper).selectOne(any(QueryWrapper.class));
        when(wordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(roundMapper.insert(any(DrawGuessRound.class))).thenAnswer(invocation -> {
            DrawGuessRound round = invocation.getArgument(0);
            round.setId(901L);
            return 1;
        });
        DrawGuessRoomVO created = service.createRoom(7L, new DrawGuessCreateRoomDTO());
        service.joinRoom(8L, created.roomCode(), null);
        DrawGuessRoomVO playing = service.startRoom(created.id(), 7L);
        String stroke = "{\"type\":\"draw\",\"points\":[{\"x\":0.2,\"y\":0.3}],"
                + "\"color\":\"#304d99\",\"width\":4,\"tool\":\"pen\"}";

        service.handleSocketMessage(created.id(), 7L, new ObjectMapper().readTree(stroke));

        DrawGuessRoomVO updated = service.getRoom(created.id(), 7L);
        assertTrue(playing.isDrawer());
        assertEquals(1, updated.strokes().size());
        assertEquals(7L, updated.strokes().get(0).get("userId"));
    }
}
