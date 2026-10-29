package com.campus.platform.module.drawgame.db;

import com.campus.platform.module.drawgame.entity.DrawGuessRoom;
import com.campus.platform.module.drawgame.entity.DrawGuessRound;
import com.campus.platform.module.drawgame.mapper.DrawGuessMemberMapper;
import com.campus.platform.module.drawgame.mapper.DrawGuessRoomMapper;
import com.campus.platform.module.drawgame.mapper.DrawGuessRoundMapper;
import com.campus.platform.module.drawgame.mapper.DrawGuessWordMapper;
import com.campus.platform.module.drawgame.service.DrawGuessRoomService;
import com.campus.platform.module.upload.entity.UploadResource;
import com.campus.platform.module.upload.mapper.UploadResourceMapper;
import com.campus.platform.module.upload.service.UploadService;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.module.drawgame.websocket.DrawGuessSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DrawGuessGalleryQueryTest {
    @Test
    void publicGalleryMapperFiltersPrivateRoomsBeforeApplyingLimit() throws Exception {
        Method query = null;
        for (Method method : DrawGuessRoundMapper.class.getMethods()) {
            if (method.getName().equals("selectPublicGalleryRounds")) query = method;
        }

        assertNotNull(query, "The gallery must have a dedicated public-only query");
        Select select = query.getAnnotation(Select.class);
        assertNotNull(select, "The public gallery filter must run in SQL");
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ").toLowerCase();
        String selectedColumns = sql.substring(0, sql.indexOf(" from "));
        assertFalse(selectedColumns.contains(".*"), "Gallery queries must not load the large drawing_data column");
        assertFalse(selectedColumns.contains("drawing_data"));
        assertTrue(selectedColumns.contains("r.id"));
        assertTrue(sql.contains("join draw_game_room room on room.id = r.room_id"));
        assertTrue(sql.contains("room.private_room = 0"));
        assertTrue(sql.contains("r.status = 'finished'"));
        assertTrue(sql.contains("r.snapshot_resource_id is not null"));
        assertTrue(sql.contains("order by r.ended_at desc"));
        assertTrue(sql.contains("limit #{limit}"));
        assertTrue(sql.indexOf("room.private_room = 0") < sql.indexOf("limit #{limit}"));
        assertEquals("limit", query.getParameters()[0].getAnnotation(Param.class).value());
    }

    @Test
    void galleryServicePreservesPublicRecordFieldsAndRequestsEightRows() {
        DrawGuessRound round = new DrawGuessRound();
        round.setId(501L);
        round.setRoomId(88L);
        round.setTurnNumber(3);
        round.setDrawerUserId(7L);
        round.setWord("梧桐树");
        round.setSnapshotResourceId(901L);
        round.setStatus("FINISHED");
        round.setEndedAt(LocalDateTime.of(2026, 9, 24, 9, 0));

        AtomicInteger requestedLimit = new AtomicInteger(-1);
        DrawGuessRoundMapper roundMapper = mock(DrawGuessRoundMapper.class, invocation ->
                galleryMapperAnswer(invocation, round, requestedLimit));
        DrawGuessRoomMapper roomMapper = mock(DrawGuessRoomMapper.class);
        DrawGuessMemberMapper memberMapper = mock(DrawGuessMemberMapper.class);
        DrawGuessWordMapper wordMapper = mock(DrawGuessWordMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        UploadResourceMapper resourceMapper = mock(UploadResourceMapper.class);
        UploadService uploadService = mock(UploadService.class);

        DrawGuessRoom room = new DrawGuessRoom();
        room.setId(88L);
        room.setRoomCode("ABC234");
        room.setTitle("校园速写");
        room.setPrivateRoom(false);
        when(roomMapper.selectById(88L)).thenReturn(room);
        UploadResource resource = new UploadResource();
        resource.setId(901L);
        resource.setResourceUrl("/uploads/draw-501.png");
        when(resourceMapper.selectById(901L)).thenReturn(resource);
        User drawer = new User();
        drawer.setId(7L);
        drawer.setNickname("画手小林");
        when(userMapper.selectById(7L)).thenReturn(drawer);

        DrawGuessRoomService service = new DrawGuessRoomService(roomMapper, memberMapper, roundMapper,
                wordMapper, userMapper, resourceMapper, uploadService,
                new DrawGuessSessionRegistry(new ObjectMapper()));
        try {
            var records = service.listRecords();

            assertEquals(8, requestedLimit.get());
            assertEquals(1, records.size());
            assertEquals("校园速写", records.get(0).title());
            assertEquals("/uploads/draw-501.png", records.get(0).snapshotUrl());
            assertEquals("画手小林", records.get(0).drawerNickname());
            assertEquals(3, records.get(0).turnNumber());
        } finally {
            service.stopTimers();
        }
    }

    private static Object galleryMapperAnswer(InvocationOnMock invocation, DrawGuessRound round,
                                              AtomicInteger requestedLimit) throws Throwable {
        if (invocation.getMethod().getName().equals("selectPublicGalleryRounds")) {
            requestedLimit.set((Integer) invocation.getArgument(0));
            return List.of(round);
        }
        if (invocation.getMethod().getName().equals("selectList")) return List.<DrawGuessRound>of();
        return RETURNS_DEFAULTS.answer(invocation);
    }
}
