package com.campus.platform.module.friend;

import com.campus.platform.common.R;
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
    public R<List<FriendService.Contact>> list() {
        return R.ok(friends.list(UserContext.getUid()));
    }

    @GetMapping("/{peerId}")
    public R<Map<String, String>> status(@PathVariable Long peerId) {
        return R.ok(Map.of("status", friends.status(UserContext.getUid(), peerId)));
    }

    @PostMapping("/{peerId}")
    public R<Map<String, String>> request(@PathVariable Long peerId, @Valid @RequestBody Request request) {
        return R.ok(Map.of("status", friends.request(UserContext.getUid(), peerId, request.greeting())));
    }

    @PutMapping("/{peerId}")
    public R<Void> respond(@PathVariable Long peerId, @Valid @RequestBody Response response) {
        friends.respond(UserContext.getUid(), peerId, response.accept());
        return R.ok();
    }

    @DeleteMapping("/{peerId}")
    public R<Void> remove(@PathVariable Long peerId) {
        friends.remove(UserContext.getUid(), peerId);
        return R.ok();
    }
}
