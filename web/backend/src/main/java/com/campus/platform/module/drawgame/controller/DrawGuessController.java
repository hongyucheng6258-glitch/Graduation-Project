package com.campus.platform.module.drawgame.controller;

import com.campus.platform.common.Result;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.drawgame.dto.DrawGuessCreateRoomDTO;
import com.campus.platform.module.drawgame.dto.DrawGuessJoinRoomDTO;
import com.campus.platform.module.drawgame.dto.DrawGuessWsTicketDTO;
import com.campus.platform.module.drawgame.service.DrawGuessRoomService;
import com.campus.platform.module.drawgame.vo.DrawGuessRecordVO;
import com.campus.platform.module.drawgame.vo.DrawGuessRoomVO;
import com.campus.platform.module.drawgame.websocket.DrawGuessWsTicketService;
import com.campus.platform.module.upload.vo.UploadVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/draw-guess")
@RequiredArgsConstructor
public class DrawGuessController {
    private final DrawGuessRoomService roomService;
    private final DrawGuessWsTicketService ticketService;

    @GetMapping("/rooms")
    public Result<List<DrawGuessRoomVO>> publicRooms() {
        return Result.ok(roomService.listPublicRooms(currentUserId()));
    }

    @GetMapping("/rooms/recent")
    public Result<List<DrawGuessRoomVO>> recentRooms() {
        return Result.ok(roomService.recentRooms(currentUserId()));
    }

    @PostMapping("/rooms")
    public Result<DrawGuessRoomVO> createRoom(@Valid @RequestBody DrawGuessCreateRoomDTO dto) {
        return Result.ok(roomService.createRoom(currentUserId(), dto));
    }

    @PostMapping("/rooms/{roomCode}/join")
    public Result<DrawGuessRoomVO> joinRoom(@PathVariable String roomCode,
                                       @Valid @RequestBody(required = false) DrawGuessJoinRoomDTO dto) {
        return Result.ok(roomService.joinRoom(currentUserId(), roomCode, dto == null ? null : dto.getPassword()));
    }

    @GetMapping("/rooms/{roomId}")
    public Result<DrawGuessRoomVO> room(@PathVariable Long roomId) {
        return Result.ok(roomService.getRoom(roomId, currentUserId()));
    }

    @PostMapping("/rooms/{roomId}/start")
    public Result<DrawGuessRoomVO> start(@PathVariable Long roomId) {
        return Result.ok(roomService.startRoom(roomId, currentUserId()));
    }

    @PostMapping("/rooms/{roomId}/leave")
    public Result<DrawGuessRoomVO> leave(@PathVariable Long roomId) {
        return Result.ok(roomService.leaveRoom(roomId, currentUserId()));
    }

    @PostMapping("/ws-ticket")
    public Result<Map<String, String>> wsTicket(@Valid @RequestBody DrawGuessWsTicketDTO dto) {
        Long userId = currentUserId();
        if (!roomService.isActiveMember(dto.getRoomId(), userId)) {
            return Result.fail(ResultCode.FORBIDDEN, "你不是这个房间的成员");
        }
        return Result.ok(Map.of("ticket", ticketService.issue(userId, dto.getRoomId())));
    }

    @PostMapping("/rooms/{roomId}/rounds/{roundId}/snapshot")
    public Result<UploadVO> uploadSnapshot(@PathVariable Long roomId,
                                      @PathVariable Long roundId,
                                      @RequestParam("file") MultipartFile file) {
        return Result.ok(roomService.uploadSnapshot(roomId, roundId, currentUserId(), file));
    }

    @GetMapping("/records")
    public Result<List<DrawGuessRecordVO>> records() {
        currentUserId();
        return Result.ok(roomService.listRecords());
    }

    private Long currentUserId() {
        Long userId = UserContext.getUid();
        if (userId == null) throw new com.campus.platform.common.BizException(ResultCode.UNAUTHORIZED);
        return userId;
    }
}
