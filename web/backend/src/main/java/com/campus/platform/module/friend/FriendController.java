package com.campus.platform.module.friend;

import com.campus.platform.common.Result;
import com.campus.platform.common.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {
    private final FriendService friends;

    public record Request(@Size(max = 80) String greeting) {}
    public record Response(@NotNull Boolean accept) {}

    @GetMapping
    public Result<List<FriendService.Contact>> list() {
        return Result.ok(friends.list(UserContext.getUid()));
    }

    @GetMapping("/{peerId}")
    public Result<Map<String, String>> status(@PathVariable Long peerId) {
        return Result.ok(Map.of("status", friends.status(UserContext.getUid(), peerId)));
    }

    @PostMapping("/{peerId}")
    public Result<Map<String, String>> request(@PathVariable Long peerId, @Valid @RequestBody Request request) {
        return Result.ok(Map.of("status", friends.request(UserContext.getUid(), peerId, request.greeting())));
    }

    @PutMapping("/{peerId}")
    public Result<Void> respond(@PathVariable Long peerId, @Valid @RequestBody Response response) {
        friends.respond(UserContext.getUid(), peerId, response.accept());
        return Result.ok();
    }

    @DeleteMapping("/{peerId}")
    public Result<Void> remove(@PathVariable Long peerId) {
        friends.remove(UserContext.getUid(), peerId);
        return Result.ok();
    }
}
