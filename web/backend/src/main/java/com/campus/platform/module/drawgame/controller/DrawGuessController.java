package com.campus.platform.module.drawgame.controller;

import com.campus.platform.common.R;
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
    public R<List<DrawGuessRoomVO>> publicRooms() {
        return R.ok(roomService.listPublicRooms(currentUserId()));
    }

    @GetMapping("/rooms/recent")
    public R<List<DrawGuessRoomVO>> recentRooms() {
        return R.ok(roomService.recentRooms(currentUserId()));
    }

    @PostMapping("/rooms")
    public R<DrawGuessRoomVO> createRoom(@Valid @RequestBody DrawGuessCreateRoomDTO dto) {
        return R.ok(roomService.createRoom(currentUserId(), dto));
    }

    @PostMapping("/rooms/{roomCode}/join")
    public R<DrawGuessRoomVO> joinRoom(@PathVariable String roomCode,
                                       @Valid @RequestBody(required = false) DrawGuessJoinRoomDTO dto) {
        return R.ok(roomService.joinRoom(currentUserId(), roomCode, dto == null ? null : dto.getPassword()));
    }

    @GetMapping("/rooms/{roomId}")
    public R<DrawGuessRoomVO> room(@PathVariable Long roomId) {
        return R.ok(roomService.getRoom(roomId, currentUserId()));
    }

    @PostMapping("/rooms/{roomId}/start")
    public R<DrawGuessRoomVO> start(@PathVariable Long roomId) {
        return R.ok(roomService.startRoom(roomId, currentUserId()));
    }

    @PostMapping("/rooms/{roomId}/leave")
    public R<DrawGuessRoomVO> leave(@PathVariable Long roomId) {
        return R.ok(roomService.leaveRoom(roomId, currentUserId()));
    }

    @PostMapping("/ws-ticket")
    public R<Map<String, String>> wsTicket(@Valid @RequestBody DrawGuessWsTicketDTO dto) {
        Long userId = currentUserId();
        if (!roomService.isActiveMember(dto.getRoomId(), userId)) {
            return R.fail(ResultCode.FORBIDDEN, "你不是这个房间的成员");
        }
        return R.ok(Map.of("ticket", ticketService.issue(userId, dto.getRoomId())));
    }

    @PostMapping("/rooms/{roomId}/rounds/{roundId}/snapshot")
    public R<UploadVO> uploadSnapshot(@PathVariable Long roomId,
                                      @PathVariable Long roundId,
                                      @RequestParam("file") MultipartFile file) {
        return R.ok(roomService.uploadSnapshot(roomId, roundId, currentUserId(), file));
    }

    @GetMapping("/records")
    public R<List<DrawGuessRecordVO>> records() {
        currentUserId();
        return R.ok(roomService.listRecords());
    }

    private Long currentUserId() {
        Long userId = UserContext.getUid();
        if (userId == null) throw new com.campus.platform.common.BizException(ResultCode.UNAUTHORIZED);
        return userId;
    }
}
