package com.campus.platform.module.drawgame.service;

import com.campus.platform.common.mapper.MapperQueries;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.drawgame.dto.DrawGuessCreateRoomDTO;
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
import com.campus.platform.module.upload.service.IUploadService;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * DrawGuessRoomService 服务接口。
 */
public interface IDrawGuessRoomService {

    void closeStaleRooms();

    void stopTimers();

    DrawGuessRoomVO createRoom(Long userId, DrawGuessCreateRoomDTO dto);

    List<DrawGuessRoomVO> listPublicRooms(Long viewerUserId);

    DrawGuessRoomVO joinRoom(Long userId, String roomCode, String password);

    DrawGuessRoomVO getRoom(Long roomId, Long viewerUserId);

    List<DrawGuessRoomVO> recentRooms(Long userId);

    DrawGuessRoomVO startRoom(Long roomId, Long userId);

    DrawGuessRoomVO leaveRoom(Long roomId, Long userId);

    UploadVO uploadSnapshot(Long roomId, Long roundId, Long userId, MultipartFile file);

    List<DrawGuessRecordVO> listRecords();

    boolean isActiveMember(Long roomId, Long userId);

    void onConnected(Long roomId, Long userId);

    void onDisconnected(Long roomId);

    void handleSocketMessage(Long roomId, Long userId, JsonNode message);
}
