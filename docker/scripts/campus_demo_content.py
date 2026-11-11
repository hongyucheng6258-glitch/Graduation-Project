"""Public campus demo records and their linked operations."""
from datetime import timedelta

IDLE = (
    ("两只 E27 暖白台灯灯泡", "生活用品", "bulbs", "两只 6W 暖白灯泡，均能正常点亮；请先核对灯座口径。", "一包中性笔替芯"),
    ("87 键有线机械键盘", "数码电子", "keyboard", "键盘支持有线连接，按键已逐个测试，附原配线。", "可调角度笔记本支架"),
    ("工科数学教材三册", "教材书籍", "textbooks", "含高等数学上下册和线性代数，少量铅笔批注。", "概率论习题册"),
    ("入门羽毛球拍和练习球", "运动器材", "racket", "球拍框无变形，手胶有正常使用痕迹，带两只练习球。", "一副备用运动护腕"),
    ("灰色三折雨伞", "生活用品", "umbrella", "伞骨完好，手动开合，侧边有一小块磨痕。", "空白线圈笔记本"),
    ("黑色科学计算器", "数码电子", "calculator", "屏幕和运算键正常，适合工程课使用，考试前请查考场规定。", "物理实验用直尺和量角器"),
    ("米色帆布双肩包", "服饰鞋包", "backpack", "主仓可装 A4 资料，拉链完好，肩带可调。", "深色帆布手提袋"),
    ("奶白陶瓷杯和杯碟", "生活用品", "mug", "杯口无缺角，约 280mL，已清洗，欢迎当面检查。", "干净的马克杯垫"),
    ("便携 USB-C 扩展坞", "数码电子", "hub", "USB 口和 HDMI 已测试；是否支持视频取决于电脑接口。", "一根短的 USB-C 数据线"),
    ("可调角度白色台灯", "生活用品", "lamp", "书桌用台灯，折叠关节无松动，电源线随灯。", "桌面收纳盒"),
    ("蓝色跑步鞋 42 码", "服饰鞋包", "shoes", "鞋底有轻微磨损，鞋面干净，建议现场试穿。", "运动腰包"),
    ("蓝色保温水壶", "生活用品", "bottle", "约 500mL，密封圈完整，空壶倒置检查无渗漏。", "可折叠便当袋"),
)

EVENTS = (
    ("周末读书会：交换一本喜欢的书", "学习交流", "reading", "图书馆二层研讨室", "请带一本读过的书，每人用三分钟介绍一段喜欢的内容。"),
    ("校园草坪音乐开放麦", "文艺娱乐", "music", "中心草坪临时舞台", "前半场为报名表演，后半场开放合唱。遇雨改到学生活动中心。"),
    ("羽毛球双打交流场", "体育运动", "badminton", "体育馆二号场地", "两人一组轮换，初学者可现场搭档，请穿室内运动鞋。"),
    ("期末复习方法交流", "学习交流", "lecture", "教学楼 B203", "分享错题整理和时间分配的方法，现场预留问答时间。"),
    ("图书馆馆藏整理志愿活动", "志愿服务", "volunteer", "图书馆一层服务台", "先由馆员讲解索书号，再分组整理归还图书。"),
    ("校园摄影漫步", "文艺娱乐", "photowalk", "中心湖东门集合", "步行拍摄校园植物和建筑，交流手机构图技巧。"),
    ("小型机器人创意组队", "竞赛组队", "robot", "创新工坊 102", "围绕巡线任务搭建原型，欢迎编程和机械方向的同学。"),
    ("英语口语圆桌", "学习交流", "language", "外语楼 205", "三到四人一组练习生活话题，准备一段一分钟自我介绍。"),
)

LOST = (
    ("绿色校园卡套", "card", "图书馆借还书台附近", "绿色卡套，内侧细节请认领时描述。"),
    ("白色无线耳机充电盒", "earbuds", "教学楼 B 区走廊长椅", "外壳为白色，盒内是否有耳机请核对。"),
    ("黑色折叠伞", "black-umbrella", "第二食堂门口伞架", "黑色短柄折叠伞，束带细节作为辨认信息。"),
    ("灰色拉链笔袋", "pencil-case", "公共自习室后排", "灰色布面，文具数量可用于核实。"),
    ("银色钥匙圈", "keys", "体育馆东侧入口", "有两把钥匙，具体形状请私信核对。"),
    ("深蓝眼镜盒和透明框眼镜", "glasses", "实验楼二层休息区", "硬质深蓝眼镜盒，内部印记不公开。"),
    ("米色帆布袋", "tote", "图书馆三楼窗边座位", "袋内物品作为归属验证信息，不公开照片。"),
    ("蓝色保温水壶", "lost-bottle", "室外健身区长椅", "壶盖带提环，容量和底部印记可用于核实。"),
)

POSTS = (
    ("library", "午后的光从图书馆玻璃门照进来。靠窗的座位安静，整理完两章笔记才发现已经到饭点。 #自习"),
    ("track", "跑道边的晚霞把云染成了橘色。今天慢跑四圈，最后一圈和同学一起走完。 #校园随手拍"),
    ("study-desk", "复习到一半去楼下买了杯热饮，回来看昨天的错题，思路比早上顺了。 #期末复习"),
    ("flowers", "宿舍窗台的小花长新叶了，值日时顺手把窗边收拾了一遍。 #宿舍生活"),
    ("reading", "读书会把大家带来的书摆满了一桌。最喜欢的是一位同学分享的旅行随笔。 #校园读书"),
    ("music", "散步经过草坪开放麦，听到了一首熟悉的民谣，坐到最后一首才回去。 #校园音乐"),
    ("badminton", "双打练习时和搭档约定了补位信号，后半场配合顺了不少。 #羽毛球"),
    ("volunteer", "跟着馆员学会了按索书号归位，原来字母后的数字顺序也要细看。 #校园志愿"),
    ("photowalk", "沿中心湖走了一圈，换了三个角度才拍到树影落在水面上。 #校园摄影"),
    ("robot", "小车终于能沿黑线绕过第一个弯。我们把传感器的位置再往前挪了一点。 #课程项目"),
    ("language", "口语圆桌练了怎么向新同学介绍校园。讲到食堂时大家意外地聊得最多。 #英语学习"),
    ("lecture", "讲座里提到先做闭卷回忆再订正，我准备在下一轮概率论复习里试试。 #学习方法"),
    ("library", "今天把借来的资料还回去了，顺便在大厅看了馆藏推荐。想借的那本已预约。 #图书馆"),
    ("track", "运动会前的跑道比平时热闹。没有跑成绩，先把呼吸节奏和步频练稳。 #体育"),
    ("study-desk", "把作业草稿和实验记录按日期分开，找上周的数据终于不用翻整摞纸。 #学习日常"),
    ("flowers", "室友出门前给植物浇了水，下午太阳刚好晒到窗台。 #校园随手拍"),
    ("reading", "借到了读书会上同学推荐的那本书，先在公交上读了序言。 #读书记录"),
    ("photowalk", "同一个路口，上午和傍晚拍出来的影子方向完全不同。 #校园摄影"),
    ("robot", "焊接练习先拿废板试手。工坊老师提醒我们关电源再调整接线。 #创新工坊"),
    ("language", "和搭档对练时把生词写成小卡片，轮到谁卡住就翻一张提示。 #自习搭子"),
)

NOTICES = (
    ("图书馆自习区座位使用提醒", "请按实际到馆时间预约，离开较长时间请释放座位。具体开放时段以馆方当天标识为准。", "library"),
    ("体育馆交流活动场地说明", "室内球场请穿运动鞋。活动参与者应按各活动详情的集合时间到场。", "badminton"),
    ("读书会书籍交换说明", "请写上书名和归还方式，不在封面写私人联系方式。无意交换的书可仅带来分享。", "reading"),
    ("创新工坊开放日安排说明", "进入操作区域前需听取安全说明，设备由值班同学协助调试。", "robot"),
    ("学习空间文明使用提示", "讨论请移至研讨室，离开前带走个人物品和垃圾。", "study-desk"),
    ("校内拾物认领提示", "卡证等物品请联系失主或交校内服务点。核实细节时请保护个人信息。", "library"),
)


def add_content(seed, users, now, admin_id):
    idle_ids = []
    for index in range(24):
        title, category, image, description, expected = IDLE[index % len(IDLE)]
        owner = users[index % len(users)]
        buyer = users[(index + 3) % len(users)]
        created = now - timedelta(days=1 + index % 7, hours=index % 5)
        status = 2 if index in (9, 18, 21) else 1 if index in (7, 16, 19) else 0
        audit = 0 if index in (22, 23) else 1
        listing = seed.insert("idle_item", user_id=owner,
                              title=title if index < 12 else f"{title}（可校内交接）",
                              description=f"演示物品，配图为示意。{description}", images=seed.images(f"idle-{image}"),
                              expect_item=expected, category=category, audit_status=audit, status=status,
                              create_time=created, update_time=created)
        idle_ids.append(listing)
        if status or index in (0, 2, 4, 6):
            started = created + timedelta(hours=2)
            finished = started + timedelta(hours=4) if status == 2 else started
            appointment = seed.insert("idle_appointment", item_id=listing, buyer_id=buyer, seller_id=owner,
                                      message=f"我有{expected}，方便在图书馆门口当面确认吗？",
                                      status={0: 0, 1: 1, 2: 3}[status], create_time=started, update_time=finished)
            if status == 2:
                seed.insert("idle_review", appointment_id=appointment, from_user_id=buyer, to_user_id=owner,
                            score=5, content="按约定时间到场，物品状态与描述一致。", create_time=finished + timedelta(hours=1))
    activity_ids = []
    for index in range(20):
        title, category, photo, location, description = EVENTS[index % len(EVENTS)]
        start = (now + timedelta(days=index + 2 if index < 12 else -(index - 9))).replace(
            hour=14 + index % 4, minute=30, second=0)
        if index in (0, 8, 16):
            next_saturday = (5 - now.weekday()) % 7 or 7
            last_saturday = (now.weekday() - 5) % 7 or 7
            offset = next_saturday + (7 if index == 8 else 0) if index != 16 else -last_saturday
            start = (now + timedelta(days=offset)).replace(hour=15, minute=0, second=0)
        deadline = start - timedelta(hours=16)
        end = start + timedelta(hours=2)
        created = min(now - timedelta(hours=index + 2), deadline - timedelta(days=3))
        owner = users[(index + 1) % len(users)]
        past = start < now
        max_members = 12 + 4 * (index % 4)
        event = seed.insert("activity", user_id=owner, title=title if index < 8 else f"{title}（第 {index // 8 + 1} 场）",
                            description=f"校园演示活动，配图为示意。{description}",
                            images=seed.images(f"campus-{photo}"), category=category, location=location,
                            start_time=start, end_time=end, signup_deadline=deadline, max_members=max_members,
                            audit_status=0 if index == 11 else 1, status=2 if past else 0, create_time=created)
        activity_ids.append(event)
        if index == 11:
            continue
        if index == 0:
            seed.insert("activity_member", activity_id=event, user_id=users[0], remark="已准备分享书目。",
                        status=1, create_time=created + timedelta(minutes=20))
        for attendee in range(1, 3 + index % 4):
            user = users[(index + 1 + attendee) % len(users)]
            joined = created + timedelta(minutes=15 * attendee)
            seed.insert("activity_member", activity_id=event, user_id=user, remark="已阅读活动说明。",
                        status=0 if not past and attendee == 1 else 1, create_time=joined)
            if past and attendee % 2 == 0:
                seed.insert("activity_signin", activity_id=event, user_id=user, sign_time=start + timedelta(minutes=attendee))
    for index in range(16):
        item, photo, place, clue = LOST[index % len(LOST)]
        found = index % 3 != 1
        owner = users[(index + 5) % len(users)]
        claimant = users[(index + 8) % len(users)]
        created = now - timedelta(days=2 + index % 9, hours=index % 4)
        state = 1 if index in (2, 5, 8) else 0
        if state:
            found = True
        item_id = seed.insert("lost_found", user_id=owner, type=int(found),
                              title=("拾到" if found else "寻找") + item + ("（教学区）" if index >= 8 else ""),
                              description=f"演示记录，配图为物品示意。{clue}请通过站内私信联系。",
                              images=seed.images(f"lost-{photo}"), location=place, happen_time=created - timedelta(hours=1),
                              audit_status=0 if index == 15 else 1, status=state, create_time=created)
        if state or index in (0, 3, 6):
            claim_time = created + timedelta(hours=2)
            seed.insert("lost_found_claim", lost_found_id=item_id, claim_user_id=claimant,
                        message="我可以提供物品内部特征和经过该处的大概时间，请私信核对。",
                        status=3 if state else 0, create_time=claim_time, update_time=claim_time + timedelta(hours=1))
    post_ids = []
    for index, (photo, text) in enumerate(POSTS):
        owner = users[(index + 2) % len(users)]
        created = now - timedelta(hours=3 * index + 2)
        approved = index not in (18, 19)
        likes = index % 5 + 1 if approved else 0
        post = seed.insert("post", user_id=owner, content=text, images=seed.images(f"campus-{photo}"),
                           like_count=likes, comment_count=1 if approved else 0, audit_status=int(approved), create_time=created)
        post_ids.append(post)
        for reader in range(likes):
            seed.insert("post_like", post_id=post, user_id=users[(index + 3 + reader) % len(users)],
                        create_time=created + timedelta(minutes=15 + 3 * reader))
        if approved:
            comments = ("刚好路过那里，今天的光线确实很好。", "下次有活动我也想去看看。", "这个方法我准备试一下。")
            seed.insert("post_comment", post_id=post, user_id=users[(index + 4) % len(users)],
                        content=comments[index % len(comments)], create_time=created + timedelta(minutes=40))
    for index, (title, body, photo) in enumerate(NOTICES):
        created = now - timedelta(days=1 + index * 2)
        status = 1 if index < 4 else 2 if index == 4 else 0
        seed.insert("notice", admin_id=admin_id, title=f"演示｜{title}",
                    content=f"**校园演示公告**\n\n{body}", cover=seed.cover(f"campus-{photo}"),
                    status=status, publish_time=created + timedelta(hours=1) if status else None, create_time=created)
    for kind, ids in (("idle", idle_ids), ("activity", activity_ids), ("post", post_ids)):
        for index in range(1, 5):
            seed.insert("favorite", user_id=users[0], target_type=kind, target_id=ids[index],
                        create_time=now - timedelta(minutes=20 + 5 * index))
    for index, (kind, target, reason) in enumerate((
        ("idle", idle_ids[2], "教材版本信息有歧义，希望发布者补充出版年份。"),
        ("activity", activity_ids[3], "页面介绍和日程中的地点需要核对。"),
        ("post", post_ids[3], "发布者可能无意中拍到了私人空间，请管理员核实。"),
    )):
        created = now - timedelta(hours=2 + index)
        seed.insert("report", reporter_id=users[(index + 9) % len(users)], target_type=kind, target_id=target,
                    reason_type="other", reason=reason, status=0, create_time=created)
    seed.insert("message", user_id=users[0], type="system", title="校园演示场景已准备",
                content="可查看我的收藏、报名、闲置预约和学习复习记录。演示内容不代表真实校园安排。",
                is_read=0, create_time=now - timedelta(minutes=10))


def backfill_legacy(seed):
    items = (
        (12, "闲置收纳箱", "legacy-storage"), (13, "Java编程书套装", "legacy-java"), (14, "考研英语真题", "legacy-english"),
        (15, "女生双肩包", "idle-backpack"),
        (16, "速写本", "legacy-sketchbook"), (17, "台式显示器", "legacy-monitor"),
        (18, "羽毛球拍", "legacy-racket"), (19, "台灯灯泡", "legacy-bulbs"),
        (20, "考研政治讲义", "legacy-notes"), (21, "闲置耳机架", "legacy-headphone-stand"),
        (22, "全新键盘膜", "legacy-keyboard-cover"),
    )
    for row_id, title, image in items:
        seed.backfill("idle_item", row_id, title, image)
    activities = (
        (7, "人工智能前沿讲座", "campus-lecture"), (8, "考研数学冲刺串讲", "campus-lecture"),
        (9, "秋季校园马拉松", "legacy-runners"), (11, "摄影社外拍活动", "campus-photowalk"),
        (12, "桌游之夜", "legacy-boardgame"), (14, "电影放映会", "legacy-movie"),
        (15, "羽毛球双打赛", "campus-badminton"), (16, "图书馆整理志愿", "campus-volunteer"),
        (17, "辩论社招新表演赛", "legacy-debate"),
    )
    for row_id, title, image in activities:
        seed.backfill("activity", row_id, title, image)
    lost_items = (
        (7, "丢失黑色水杯", "legacy-black-bottle"), (8, "丢失U盘", "legacy-usb"),
        (9, "捡到一支钢笔", "legacy-pen"), (10, "捡到一副眼镜", "lost-glasses"),
        (11, "丢失电动车充电器", "legacy-charger"), (12, "捡到一个笔记本", "legacy-notebook"),
        (13, "丢失AirPods充电盒", "lost-earbuds"), (14, "捡到一个充电宝", "legacy-power-bank"),
    )
    for row_id, title, image in lost_items:
        seed.backfill("lost_found", row_id, title, image)
