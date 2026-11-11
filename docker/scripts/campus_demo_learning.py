"""Fictional campus conversations, study records, and finished games."""

from datetime import timedelta


PARTNERS = [
    ("计算机二级 Python", "做完近三套真题并整理易错点", "周二、周四 19:00｜图书馆三层研讨区", "我在复习文件读写和字典，想找一位同学轮流讲错题。"),
    ("大学英语四级", "坚持四周听力打卡，完成两套真题", "工作日 07:40｜东区操场看台", "每天二十分钟影子跟读，周日挑一段复盘，不赶进度。"),
    ("高等数学", "补上极限和连续两章", "周三 15:40｜明理楼 B204", "先一起做课后题，再各讲一道，我可以整理当天的笔记。"),
    ("Java Web", "完成课程设计的接口联调", "周六 14:00｜信息楼 307", "我负责后端，想找在写 Vue 的同学互看接口文档与错误提示。"),
    ("数据结构", "用 C++ 实现链表、栈和队列", "周一 18:30｜图书馆南区自习室", "希望每次用一个小输入手推过程，有独立思路再交流。"),
    ("线性代数", "把矩阵秩与线性方程组理顺", "周五 16:00｜知行楼一层共享桌", "交换一张手写公式卡，再互相出三道短题。"),
    ("普通话水平测试", "每周录两段命题说话", "周三 20:00｜线上语音，先站内约时间", "重点练翘舌音和语速，互相反馈时标注具体句子。"),
    ("摄影后期", "一起完成校园夜景组图", "周五 19:20｜南门梧桐步道", "相机和手机均可，我会带一个小三脚架，雨天延期。"),
    ("概率论", "做完条件概率和贝叶斯练习", "周日 09:30｜图书馆二层 2B", "我能整理树状图例题，期待有人从列联表角度讲一遍。"),
    ("数据库原理", "练习 JOIN、分组与索引", "周四 17:10｜信息楼 205", "准备了一份课程选课样例库，愿意一起写查询并解释结果。"),
    ("日语入门", "认读平假名并练校园自我介绍", "周日 15:00｜大学生活动中心大厅", "刚学完前两课，希望慢慢练，每次半小时即可。"),
    ("学术写作", "完成课程调研报告初稿", "周六 10:00｜图书馆一层讨论区", "互读提纲，只反馈论据是否支撑结论，引用来源各自核对。"),
]

QUESTIONS = [
    ("技术", "为什么 LEFT JOIN 加了 WHERE 后漏掉没选课的学生？", "我想查所有学生与本学期的选课数，限定学期后没选课的人就消失了。", "把学期条件放到 LEFT JOIN 的 ON 子句。未匹配行在右表字段上是 NULL，WHERE 学期等于指定值会删掉这些行。计数用 COUNT(选课表.id)，未匹配的学生就会得到 0。"),
    ("生活", "大件纸箱想拆开再回收，有没有省事的方法？", "寝室刚拆了两箱书，箱子太大，能直接压扁吗？", "先取掉快递面单、胶带和填充物，再沿纸箱底部接缝拆开压平。沾油或湿透的部分另外放，最后按回收点张贴的分类说明投放。"),
    ("课程", "栈为什么能检查括号匹配？", "我能数左右括号个数，但输入 ([)] 会被误判，应该怎么改？", "读到左括号就入栈，读到右括号时检查栈顶是否为同一类型的左括号，匹配才弹出。右括号遇空栈、类型不同，或结束后栈非空都不合法。"),
    ("课程", "小组报告的访谈记录怎么放比较清晰？", "正文只有三千字，四份访谈逐字稿全部放进去显得太长。", "正文用主题组织结论，引用最能说明观点的一两句话，并标注匿名访谈编号。访谈提纲和脱敏全文放附录，编号对应，便于读者核查。"),
    ("其他", "校园夜景用手机拍总是糊，有什么简单设置？", "主要拍静止的建筑和灯光，手持夜景模式经常虚。", "找栏杆或三脚架固定手机，开两秒定时再拍。选亮暗交界处对焦并稍微压低曝光。夜景模式适合静止建筑，遇到走动的人可以改用普通模式。"),
    ("考试", "看四级听力原文很简单，闭着眼听却跟不上", "我认识大部分词，很多句子听完只有零散单词，怎样练比较好？", "选三十秒原声，先盲听记关键词，再逐句听写和核对，标出连读、弱读或遗漏。最后离开文本跟读两遍，隔天盲听同一段检验是否听懂。"),
    ("技术", "Git rebase 途中想回到开始前怎么办？", "我是在自己的练习分支操作，解决第一个冲突后发现基线选错了。", "rebase 尚未结束时运行 git rebase --abort，可以返回操作前的分支状态。先用 git status 确认正在 rebase，再重新检查目标分支。"),
    ("生活", "寝室搬书怎么分箱比较好抬？", "同一个大纸箱塞满教材之后搬不动，有没有装箱经验？", "书用小箱分开装，每箱先试提。大书平铺在底部，轻衣物填空隙。底部用胶带加固并标注箱内物品，两个人搬时提前清空过道。"),
    ("课程", "实验报告里平均速率该怎么求？", "两段路的行程分别是 120 米和 180 米，用时 30 秒和 45 秒。要先对每段速率取平均吗？", "平均速率用总路程除以总时间，即 (120+180)/(30+45)=4 米每秒。这里两段速率刚好相同。一般情况下直接平均各段速率会忽略用时差异。"),
    ("技术", "怎样写出更容易复现的小组项目问题记录？", "我们现在只写页面打不开，另一个同学总复现不了。", "说明所在分支、入口地址、前置条件、逐步操作、预期和实际结果，并附错误时间或请求状态。账号、令牌及个人信息先脱敏，再粘贴必要的日志片段。"),
    ("生活", "想组织五个人轮流带领晨跑，怎么记打卡？", "目前都在群里发截图，月底难统计，而且有人不想公开运动记录。", "用共享表只记日期、值班人和到场代号，参加者自愿登记即可。具体运动数据各自保留，值班人每周核对缺漏并提前说明雨天规则。"),
    ("技术", "用投影做小组演示，字幕字号多少合适？", "打算在普通教室展示六页，最后一排有点远。", "正文先按约 24 到 28 磅排，标题再大一些。用实际投影试看最后一排，字小就缩短句子、拆页或放大图注，并用高对比度配色。"),
]

WRONG_QUESTIONS = [
    ("高等数学", "极限", "求 x→0 时 sin(3x)/x 的极限。", "3", "1", "拆成 3×sin(3x)/(3x)，后者按基本极限趋于 1。"),
    ("高等数学", "求导", "求 y=x²eˣ 的导数。", "eˣ(x²+2x)", "2xeˣ", "按乘积法则分别求导，得到 2xeˣ+x²eˣ。"),
    ("高等数学", "定积分", "计算 0 到 1 上函数 3x² 的定积分。", "1", "3", "原函数为 x³，代入积分上限减去下限即可。"),
    ("高等数学", "函数连续", "当 x≠1 时，f(x)=(x²-1)/(x-1)。应定义 f(1) 为多少才连续？", "2", "0", "x≠1 时约分得 x+1，所以 x→1 的极限为 2。"),
    ("线性代数", "行列式", "求矩阵 [[2, 3], [1, 4]] 的行列式。", "5", "11", "二阶行列式为主对角线乘积减副对角线乘积，8−3=5。"),
    ("线性代数", "矩阵的秩", "矩阵 [[1, 2], [2, 4]] 的秩为多少？", "1", "2", "第二行是第一行的两倍，只有一个线性无关的行向量。"),
    ("线性代数", "矩阵乘法", "计算 [[1,2],[0,1]] 乘以列向量 [3,4]。", "[11,4]", "[3,8]", "逐行与列向量点乘，第一行得 3+8，第二行得 4。"),
    ("概率论", "条件概率", "已知 P(A∩B)=0.18，P(B)=0.30，求 P(A|B)。", "0.6", "0.054", "在 B 已发生的范围内归一化：0.18/0.30=0.6。"),
    ("概率论", "独立事件", "A、B 独立，P(A)=0.4，P(B)=0.5，求 P(A∪B)。", "0.7", "0.9", "交集概率为 0.2，并集用容斥原理得到 0.4+0.5−0.2。"),
    ("概率论", "期望", "投一次公平骰子，点数 X 的期望是多少？", "3.5", "3", "对所有可能点数乘各自概率后求和，(1+2+3+4+5+6)/6=3.5。"),
    ("数据结构", "栈", "依次执行 push(1)、push(2)、pop()、push(3)，栈顶是什么？", "3", "1", "pop 删除 2，随后 3 入栈，栈从底到顶为 1、3。"),
    ("数据结构", "复杂度", "在长度为 n 的有序数组中做二分查找，最坏时间复杂度是多少？", "O(log n)", "O(n)", "每次比较后候选区间减半，至多经过对数级次数后只剩一个位置。"),
    ("数据库", "聚合查询", "一列有 7、NULL、9 三条记录，COUNT(该列) 返回多少？", "2", "3", "COUNT(列) 跳过 NULL，COUNT(*) 才计算所有行。"),
    ("数据库", "分组条件", "按班级分组，只保留人数超过 30 的班级，应在 WHERE 还是 HAVING 判断？", "HAVING COUNT(*) > 30", "WHERE COUNT(*) > 30", "WHERE 在分组前筛选原始行，HAVING 在分组后使用聚合结果。"),
    ("Java", "字符串", "用 new String 创建的两个内容相同的字符串，比较文本内容应用什么方法？", "equals()", "==", "String 的 equals 比较字符内容，== 判断两个引用是否指向同一个对象。"),
    ("大学英语", "主谓一致", "补全 The number of students ___ increasing.（is/are）", "is", "are", "主语中心词是单数 number，of students 只作修饰，所以用 is。"),
    ("大学英语", "动词时态", "改错：She has finished the report yesterday.", "She finished the report yesterday.", "She has finish the report yesterday.", "yesterday 指明确的过去时间，应使用一般过去时，finish 的过去式是 finished。"),
    ("大学物理", "匀加速运动", "物体从静止以 2 m/s² 加速 4 秒，位移是多少？", "16 m", "8 m", "位移为 ½at²=½×2×16=16 m。末速度为 8 m/s。"),
]

AI_CONVERSATIONS = [
    ("把分组 SQL 讲明白", (
        ("用选课表解释一下 WHERE 和 HAVING。", "假设 enroll 有 student_id、course_id 和 term。WHERE term='秋季' 先筛出秋季的记录，GROUP BY student_id 再按学生分组，HAVING COUNT(*) > 2 最后筛出秋季选了超过两门课的学生。"),
        ("没有选课的学生怎么一起统计？", "从 student 表出发 LEFT JOIN enroll，把 term 条件写进 ON。再按学生编号分组，用 COUNT(enroll.course_id) 统计匹配的选课，未匹配时该列为 NULL，得到 0。"),
    )),
    ("四级听力一周复习安排", (
        ("早上只有二十分钟，怎样练四级听力？", "前 3 分钟盲听并记关键词，接着用 8 分钟逐句听写，花 5 分钟对照原文找连读和弱读，最后 4 分钟不看文本跟读。周五重听周一的材料，对比错词数量。"),
        ("总是漏掉转折之后的内容。", "第二遍刻意标记 but、however 和 actually。听到这些词后，用一两个词记下说话者真正的决定，再回看原文检查是否抓住了转折后的主旨。"),
    )),
    ("课设接口联调检查", (
        ("两个同学一起联调接口，如何记录问题？", "给每条记录写清页面入口、触发操作、请求方法和地址、响应状态与预期。先核对请求参数和响应结构，再把能稳定复现的问题分给对应同学，提交修复后走同一条流程复测。"),
        ("分页测试要包含什么？", "覆盖第一页、末页、超过末页和空筛选结果。检查前端页码起点与接口约定是否相同，切换筛选条件后能否回第一页，以及总数是否按筛选条件计算。"),
    )),
    ("链表反转手推", (
        ("用 1→2→3 解释单链表原地反转。", "每步先存 current.next，再把 current.next 指向 previous，随后把 previous 移到 current，把 current 移到暂存节点。最后 previous 指向 3，链为 3→2→1。"),
    )),
]

CONVERSATIONS = [
    ("周四复习 Python 那场还在三层吗？我带文件读写的练习。", "在三层靠窗研讨区，我带字典的两道题。", "好，我先把两道题手推一遍，见面再对。", "到门口站内发我一句，我们一起找座。"),
    ("你昨天讲的 WHERE 和 HAVING 我弄清楚了，感谢！", "有空再试下把 WHERE 的学期条件挪到 ON，比较两张结果表。", "我试了，没选课的同学用 COUNT(课程编号) 得 0。", "对，后面写课程项目查询就能直接用上。"),
    ("周日晨跑提前十分钟到可以吗？", "可以，我会先到看台做拉伸，天气不好就临时取消。", "我只记到场代号，运动数据大家自己留。", "行，我周六晚再发一次集合时间。"),
    ("调研报告里引用受访者原话要写姓名吗？", "我们用访谈编号，先把宿舍号、联系方式之类的信息去掉。", "好，我把正文和附录的编号统一。", "我晚上核对一下每条引用对应的录音位置。"),
    ("周五拍校园夜景，想先去梧桐步道还是操场？", "先去梧桐步道，等路灯全亮再试一组长曝光。", "我带手机夹和小脚架，天气预报如果下雨再改。", "好，先到的人在南门里侧等，别挡通道。"),
    ("线代复习时我把矩阵秩和行数搞混了。", "挑一个两行成倍数的矩阵先做行变换，第二行会变成零行。", "原来要数非零行，我把这个例子加进公式卡。", "也可以试一个三行但只有两个独立方向的例子。"),
]


def add_learning(seed, users, now):
    for index, (subject, goal, schedule, intro) in enumerate(PARTNERS):
        seed.insert(
            "study_partner", user_id=users[(index + 1) % len(users)], subject=subject,
            goal=goal, schedule=schedule, intro=intro, contact="可在站内私信",
            status=int(index >= 10), audit_status=1, audit_source="manual",
            create_time=now - timedelta(days=2 + index, hours=1),
        )

    for index, (category, title, question, answer) in enumerate(QUESTIONS):
        asked_at = now - timedelta(days=2 + index)
        question_id = seed.insert(
            "campus_question", user_id=users[index], title=title, content=question,
            category=category, status=0, view_count=26 + (index * 19) % 111,
            create_time=asked_at,
        )
        answer_id = seed.insert(
            "campus_answer", question_id=question_id, user_id=users[(index + 3) % len(users)],
            content=answer, is_accepted=1, create_time=asked_at + timedelta(hours=3),
        )
        seed.update("campus_question", question_id, status=1, accepted_answer_id=answer_id)

    add_wrong_questions(seed, users[0], now)
    add_ai_conversations(seed, users, now)
    add_chat(seed, users, now)
    add_archived_games(seed, users, now)


def add_wrong_questions(seed, user, now):
    # Prior reviews, current status, score, consecutive correct answers, and days until review.
    review_states = (
        ((), 0, 0, 0, 0),
        ((1,), 1, 30, 0, 1),
        ((2,), 2, 70, 1, 1),
        ((2, 3), 3, 100, 2, 3),
        ((0,), 0, 5, 0, 0),
        ((1, 2), 2, 70, 1, 1),
    )
    for index, (subject, chapter, question, answer, wrong_answer, analysis) in enumerate(WRONG_QUESTIONS):
        levels, status, score, consecutive, interval = review_states[index % len(review_states)]
        last_review = now - timedelta(hours=index + 2) if levels else None
        created = now - timedelta(days=7 + index)
        next_review = last_review + timedelta(days=interval) if levels else created
        question_id = seed.insert(
            "wrong_question", user_id=user, subject=subject, tag=chapter, question=question,
            correct_answer=answer, analysis=analysis, my_answer=wrong_answer,
            error_reason="概念不清", question_type="简答", chapter=chapter,
            difficulty="中" if index % 3 == 1 else "易", knowledge_points=chapter,
            analyze_status=0, status=status, mastery_score=score,
            review_count=len(levels), wrong_count=1 + levels.count(0),
            consecutive_correct_count=consecutive, last_review_time=last_review,
            next_review_time=next_review, source="manual", create_time=created,
        )
        for offset, level in enumerate(levels):
            reviewed_at = last_review - timedelta(days=len(levels) - offset - 1)
            seed.insert(
                "wrong_question_review", user_id=user, wrong_question_id=question_id,
                user_answer=answer if level >= 2 else wrong_answer,
                is_correct=int(level >= 2), mastery_level=level,
                review_note=("先回看课本例题", "能说出用到的概念，答案还不完整",
                             "合上笔记完成了这题", "独立完成并能解释原来的错误")[level],
                review_time=reviewed_at,
            )


def add_ai_conversations(seed, users, now):
    for index, (title, exchanges) in enumerate(AI_CONVERSATIONS):
        user = users[0 if index < 3 else 4]
        started = now - timedelta(days=index + 1, hours=4)
        session = seed.insert(
            "ai_session", user_id=user, scene="chat", title=title, create_time=started,
            update_time=started + timedelta(minutes=(len(exchanges) - 1) * 2, seconds=3),
        )
        for turn, (question, response) in enumerate(exchanges):
            asked_at = started + timedelta(minutes=turn * 2)
            answered_at = asked_at + timedelta(seconds=3)
            completion_tokens = len(response)
            seed.insert("ai_message", session_id=session, role="user", content=question, create_time=asked_at)
            seed.insert(
                "ai_message", session_id=session, role="assistant", content=response,
                tokens=completion_tokens, create_time=answered_at,
            )
            seed.insert(
                "ai_call_log", user_id=user, scene="chat", model="campus-demo",
                prompt_tokens=180 + len(question) + turn * 130, completion_tokens=completion_tokens,
                cost_ms=1420 + index * 190 + turn * 73, status=0, create_time=answered_at,
            )
    seed.insert(
        "ai_call_log", user_id=users[5], scene="chat", model="campus-demo",
        cost_ms=30000, status=1, error_msg="演示记录：模型请求超时，未生成回复。",
        create_time=now - timedelta(days=1, hours=6),
    )


def add_chat(seed, users, now):
    for index, messages in enumerate(CONVERSATIONS, start=1):
        started = now - timedelta(hours=3 + index * 6)
        members = (users[0], users[index])
        conversation = seed.insert(
            "chat_conversation", user1_id=members[0], user2_id=members[1],
            context_type="profile", context_id=members[1], create_time=started,
        )
        unread = index in (1, 3)
        last_read = [None, None]
        read_times = [None, None]
        for position, content in enumerate(messages):
            sender = position % 2
            receiver = 1 - sender
            sent_at = started + timedelta(minutes=position * 3)
            is_read = not (unread and position == len(messages) - 1)
            read_at = sent_at + timedelta(minutes=1) if is_read else None
            message_id = seed.insert(
                "chat_message", conversation_id=conversation, sender_id=members[sender],
                receiver_id=members[receiver], client_message_id=f"campus-demo-{index}-{position}",
                message_type="text", content=content, status=int(is_read),
                read_time=read_at, create_time=sent_at,
            )
            if is_read:
                last_read[receiver] = message_id
                read_times[receiver] = read_at
        seed.update(
            "chat_conversation", conversation, last_message_id=message_id,
            last_message_summary=messages[-1], last_message_time=sent_at, update_time=sent_at,
        )
        for member_index, user in enumerate(members):
            seed.insert(
                "chat_conversation_member", conversation_id=conversation, user_id=user,
                unread_count=int(unread and member_index == 0), last_read_message_id=last_read[member_index],
                read_time=read_times[member_index], create_time=started,
                update_time=read_times[member_index] or started,
            )


def add_archived_games(seed, users, now):
    names = ("林予安", "陈知夏", "周明澈", "许书禾", "何沐阳", "叶听澜", "梁嘉言")
    archives = (
        ("WTGY2A", "晚自习后的校园词汇", (0, 1, 2), ("书包", "风筝", "台灯")),
        ("WTGY3B", "梧桐步道周末场", (3, 0, 4), ("路灯", "雨伞", "帆布袋")),
        ("WTGY4C", "社团迎新茶歇场", (5, 2, 6), ("桂花", "咖啡杯", "黑板")),
    )
    for index, (code, title, seats, words) in enumerate(archives):
        started = now - timedelta(days=3 + index * 2, hours=2)
        finished = started + timedelta(minutes=4, seconds=45)
        room = seed.insert(
            "draw_game_room", room_code=code, owner_user_id=users[seats[0]], title=title,
            status="FINISHED", current_turn=len(words), max_players=6, rounds_per_player=1,
            created_at=started, updated_at=finished,
        )
        for seat, member_index in enumerate(seats, start=1):
            seed.insert(
                "draw_game_member", room_id=room, user_id=users[member_index], seat_no=seat,
                nickname=names[member_index] + "·演示", score=(3, 3, 2)[seat - 1], active=0,
                joined_at=started + timedelta(seconds=seat * 5), last_visited_at=finished,
            )
        for turn, (drawer_index, word) in enumerate(zip(seats, words), start=1):
            round_started = started + timedelta(seconds=(120, 165, 225)[turn - 1])
            ended = round_started + timedelta(seconds=45 if turn == 1 else 60)
            snapshot = seed.image_resource(users[drawer_index], f"game-{index * 3 + turn}", ended)
            seed.insert(
                "draw_game_round", room_id=room, turn_number=turn, drawer_user_id=users[drawer_index],
                word=word, status="FINISHED", started_at=round_started,
                snapshot_resource_id=snapshot, ended_at=ended,
            )
