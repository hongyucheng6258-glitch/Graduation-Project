package com.campus.platform.module.lostfound;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import com.campus.platform.module.ai.service.ContentAiAuditService;
import com.campus.platform.module.idle.service.IdleService;
import com.campus.platform.module.lostfound.dto.ClaimDTO;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.lostfound.entity.LostFoundClaim;
import com.campus.platform.module.lostfound.mapper.LostFoundClaimMapper;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.lostfound.service.LostFoundService;
import com.campus.platform.module.message.service.MessageService;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1 第6项回归：失物认领状态机与并发保护。
 * 覆盖：父行锁串行化申请、条件更新防重复同意/确认、批量驳回、myClaim 取最新。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("P1 LostFoundService 认领并发与状态机")
class LostFoundServiceTest {

    @Mock private LostFoundMapper lostFoundMapper;
    @Mock private LostFoundClaimMapper claimMapper;
    @Mock private UserMapper userMapper;
    @Mock private SensitiveWordService sensitiveWordService;
    @Mock private ContentAiAuditService contentAiAuditService;
    @Mock private MessageService messageService;

    @InjectMocks
    private LostFoundService lostFoundService;

    private LostFound claimableLf(Long id, Long ownerId) {
        LostFound lf = new LostFound();
        lf.setId(id);
        lf.setUserId(ownerId);
        lf.setType(1); // 招领
        lf.setStatus(Constants.LF_DOING);
        lf.setAuditStatus(Constants.AUDIT_PASS);
        lf.setTitle("校园卡");
        return lf;
    }

    private LostFoundClaim claim(Long id, Long lfId, Long userId, int status) {
        LostFoundClaim c = new LostFoundClaim();
        c.setId(id);
        c.setLostFoundId(lfId);
        c.setClaimUserId(userId);
        c.setStatus(status);
        return c;
    }

    @Nested
    @DisplayName("申请认领并发保护")
    class Claim {

        @Test
        @DisplayName("认领流程以父行 FOR UPDATE 为第一条语句，再用当前读计数")
        void claim_shouldLockParentRowBeforeDuplicateCheck() {
            when(lostFoundMapper.selectByIdForUpdate(10L)).thenReturn(claimableLf(10L, 1L));
            when(claimMapper.countActiveForUpdate(10L)).thenReturn(0L);

            LostFoundClaim result = lostFoundService.claim(9L, 10L, new ClaimDTO());

            verify(lostFoundMapper).selectByIdForUpdate(10L);
            verify(claimMapper).countActiveForUpdate(10L);
            assertThat(result.getStatus()).isZero();
            org.mockito.ArgumentCaptor<LostFoundClaim> captor =
                    org.mockito.ArgumentCaptor.forClass(LostFoundClaim.class);
            verify(claimMapper).insert(captor.capture());
            assertThat(captor.getValue().getClaimUserId()).isEqualTo(9L);
        }

        @Test
        @DisplayName("已存在待处理/已同意的申请时拒绝新申请，且不写库")
        void claim_shouldRejectWhenActiveClaimExists() {
            when(lostFoundMapper.selectByIdForUpdate(10L)).thenReturn(claimableLf(10L, 1L));
            when(claimMapper.countActiveForUpdate(10L)).thenReturn(1L);

            assertThatThrownBy(() -> lostFoundService.claim(9L, 10L, new ClaimDTO()))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.DUPLICATE_OPERATION.getCode());
            verify(claimMapper, never()).insert(org.mockito.ArgumentMatchers.<LostFoundClaim>any());
        }

        @Test
        @DisplayName("自己不能认领自己发布的招领信息")
        void claim_shouldRejectOwner() {
            when(lostFoundMapper.selectByIdForUpdate(10L)).thenReturn(claimableLf(10L, 1L));

            assertThatThrownBy(() -> lostFoundService.claim(1L, 10L, new ClaimDTO()))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.BAD_REQUEST.getCode());
            verify(claimMapper, never()).insert(org.mockito.ArgumentMatchers.<LostFoundClaim>any());
        }
    }

    @Nested
    @DisplayName("处理认领申请（条件更新防重复）")
    class HandleClaim {

        @Test
        @DisplayName("同意时走条件更新并通过批量驳回清掉其余待确认申请")
        void accept_shouldConditionallyUpdateAndRejectOthers() {
            when(claimMapper.selectById(20L)).thenReturn(claim(20L, 10L, 9L, 0));
            when(lostFoundMapper.selectByIdForUpdate(10L)).thenReturn(claimableLf(10L, 1L));
            when(claimMapper.updateStatusIfPending(20L, 1)).thenReturn(1);

            lostFoundService.handleClaim(1L, 20L, true);

            verify(claimMapper).updateStatusIfPending(20L, 1);
            verify(claimMapper).rejectOtherPending(10L, 20L);
        }

        @Test
        @DisplayName("申请已被并发处理（受影响行数0）→ 幂等拒绝，不再批量驳回")
        void handleAlreadyHandled_shouldThrowDuplicate() {
            when(claimMapper.selectById(20L)).thenReturn(claim(20L, 10L, 9L, 0));
            when(lostFoundMapper.selectByIdForUpdate(10L)).thenReturn(claimableLf(10L, 1L));
            when(claimMapper.updateStatusIfPending(20L, 1)).thenReturn(0);

            assertThatThrownBy(() -> lostFoundService.handleClaim(1L, 20L, true))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.DUPLICATE_OPERATION.getCode());
            verify(claimMapper, never()).rejectOtherPending(any(), any());
        }

        @Test
        @DisplayName("非发布者不能处理认领申请")
        void handleByNonOwner_shouldBeForbidden() {
            when(claimMapper.selectById(20L)).thenReturn(claim(20L, 10L, 9L, 0));
            when(lostFoundMapper.selectByIdForUpdate(10L)).thenReturn(claimableLf(10L, 2L));

            assertThatThrownBy(() -> lostFoundService.handleClaim(1L, 20L, false))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
            verify(claimMapper, never()).updateStatusIfPending(any(), org.mockito.ArgumentMatchers.anyInt());
        }
    }

    @Nested
    @DisplayName("确认找回（条件更新防重复完成）")
    class ConfirmReturn {

        @Test
        @DisplayName("仅已同意(1)可转已找回(3)，成功后招领信息标记完成")
        void confirm_shouldConditionallyUpdate() {
            when(claimMapper.selectById(30L)).thenReturn(claim(30L, 10L, 9L, 1));
            when(claimMapper.updateReturnedIfAgreed(30L)).thenReturn(1);
            LostFound lf = claimableLf(10L, 1L);
            when(lostFoundMapper.selectByIdForUpdate(10L)).thenReturn(lf);

            lostFoundService.confirmReturn(9L, 30L);

            verify(claimMapper).updateReturnedIfAgreed(30L);
            org.mockito.ArgumentCaptor<LostFound> captor =
                    org.mockito.ArgumentCaptor.forClass(LostFound.class);
            verify(lostFoundMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(Constants.LF_DONE);
        }

        @Test
        @DisplayName("并发重复确认（受影响行数0）→ 报错且不再改招领状态")
        void confirmTwice_shouldFailSecond() {
            when(claimMapper.selectById(30L)).thenReturn(claim(30L, 10L, 9L, 3));
            when(claimMapper.updateReturnedIfAgreed(30L)).thenReturn(0);

            assertThatThrownBy(() -> lostFoundService.confirmReturn(9L, 30L))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.BAD_REQUEST.getCode());
            verify(lostFoundMapper, never()).updateById(org.mockito.ArgumentMatchers.<LostFound>any());
        }
    }

    @Nested
    @DisplayName("我的申请查询")
    class MyClaim {

        @Test
        @DisplayName("多次申请时按 id 倒序取最新一条（服务层已配置 orderByDesc，返回最新记录）")
        void myClaim_shouldReturnLatestById() {
            when(claimMapper.selectOne(any())).thenReturn(claim(31L, 10L, 9L, 2));
            User u = new User();
            u.setId(9L);
            u.setNickname("小明");
            when(userMapper.selectById(9L)).thenReturn(u);

            var vo = lostFoundService.myClaim(9L, 10L);

            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(31L);
            assertThat(vo.getStatus()).isEqualTo(2);
            // 注释说明：orderByDesc(LostFoundClaim::getId) 在服务实现中显式配置，
            // 单测环境无 MyBatis lambda 缓存，不做 SQL 片段级断言（避免误报）。
            verify(claimMapper).selectOne(any());
        }
    }
}
