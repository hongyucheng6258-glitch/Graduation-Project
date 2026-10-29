package com.campus.platform.module.idle.vo;


import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 闂茬疆璇︽儏 VO锛氶檮甯﹀綋鍓嶇敤鎴蜂笌璇ョ墿鍝佺殑鍏崇郴銆? */
@Data
@EqualsAndHashCode(callSuper = true)
public class IdleDetailVO extends IdleItemVO {

    /** 鏄惁鏈汉鍙戝竷 */
    private Boolean isOwner;

    /** 褰撳墠鐢ㄦ埛宸插彂璧风殑棰勭害ID锛堟棤鍒檔ull锛?*/
    private Long myAppointmentId;

    /** 褰撳墠鐢ㄦ埛鍙幓浜掕瘎鐨勫凡瀹屾垚棰勭害ID锛堟棤鍒檔ull锛?*/
    private Long reviewAppointmentId;

    /** 褰撳墠鐢ㄦ埛鏄惁宸茶瘎浠?*/
    private Boolean reviewed;

    /** 鍗栧瑙嗚锛氬緟澶勭悊棰勭害锛堟棤鍒檔ull锛夛紝鐢ㄤ簬璇︽儏椤靛睍绀烘帴鍙?鎷掔粷 */
    private PendingAppointmentVO pendingAppointment;

    /** 鍗栧鍘嗗彶骞冲潎璇勫垎 */
    private Double sellerAvgScore;
}
