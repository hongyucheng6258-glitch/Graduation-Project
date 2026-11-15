"""Populate the local campus installation with linked, fictional campus records."""
import argparse
import base64
import getpass
import json
from datetime import datetime, timedelta
from uuid import uuid4
from zoneinfo import ZoneInfo

import bcrypt

from campus_demo_content import EVENTS, IDLE, LOST, POSTS, NOTICES
from campus_demo_learning import (
    CONVERSATIONS, PARTNERS, QUESTIONS, add_wrong_questions,
)
from seed_campus_demo import ASSETS, BIOS, NAMES, Ref, Seed, literal, mysql

PROJECT = 'ai-campus-docker'
ORANGE = '2310705207'
FIRST_PEER = 2310705301
MARKER = 'WT261116A'
HANDBOOKS = {
    '图书馆使用演示指南.pdf': 'campus-library-handbook',
    '实验记录与复盘演示讲义.pdf': 'campus-lab-handbook',
}
SCENES = (
    ('图书馆研讨室怎么预约', '校园向导', '小组想找地方讨论报告，应该先做什么？',
     '先核对图书馆研讨室的开放时间和人数限制。确认组员可到场后，在馆方系统预约。'),
    ('概率论复习安排', '学习', '距离随堂测验还有五天，怎样安排条件概率复习？',
     '先复习样本空间和条件概率定义，再用树状图解全概率题，最后做两道贝叶斯公式的变式。'),
    ('Python 列表索引排查', '代码', '访问列表最后一项时为什么会越界？',
     '长度为 n 的列表最大下标是 n-1。可用 items[-1] 读取最后一项，并先处理空列表。'),
    ('读书会开场提纲', '活动', '怎样让第一次来的同学也容易开口？',
     '先用一句话介绍带来的书，再给每人两分钟读一小段。留出自由提问时间，允许只旁听。'),
    ('交换教材前核对版本', '闲置', '高数课本交换前需要看哪些信息？',
     '核对版次、出版社和上下册，再检查目录是否符合课程要求，当面说明批注和缺页情况。'),
    ('拾到耳机后的归还步骤', '失物', '怎样核实认领人，又不公开识别细节？',
     '公开拾获的大致位置和时间。请认领人通过私信说明外壳标识或盒内情况，核对后在公共地点交接。'),
    ('四级听力搭子练习安排', '搭子', '两个人时间不完全重合，可以怎么复盘？',
     '每人先独立听同一段材料，再异步共享错词和卡顿处。每周留一次十五分钟语音核对。'),
    ('数据库作业提问思路', '问答', 'SQL 查不到预期结果，提问时要写哪些内容？',
     '附上简化的表结构、几行虚构输入、当前查询和期待结果，说明从哪个连接条件开始不符合预期。'),
)
GAMES = (
    ('晚自习后的画画局', ('书包', '风筝', '台灯')),
    ('雨天校园速写', ('路灯', '雨伞', '帆布袋')),
    ('社团茶歇画画局', ('桂花', '咖啡杯', '黑板')),
)


class CampusSeed(Seed):
    def __init__(self):
        super().__init__()
        self.wrong_questions = []
        self.sql.insert(1, f"SELECT GET_LOCK('campus_records_20261116', 20) INTO @campus_lock")
        self.sql.insert(2, "CREATE TEMPORARY TABLE campus_import_guard (allowed TINYINT NOT NULL)")
        self.sql.append(
            f"INSERT INTO campus_import_guard SELECT IF(@campus_lock=1 AND NOT EXISTS "
            f"(SELECT 1 FROM draw_game_room WHERE room_code='{MARKER}'),1,NULL)"
        )

    def insert(self, table, **fields):
        for key, value in fields.items():
            if isinstance(value, str) and not value.startswith(('data:', '["data:')):
                fields[key] = value.replace('普通话水平测试', '普通话水平考试').replace(
                    '小组演示', '小组展示').replace('·演示', '').replace('分页测试', '分页检查').replace(
                    '已逐个测试', '已逐个检查').replace('HDMI 已测试', 'HDMI 已检查')
        ref = super().insert(table, **fields)
        if table == "wrong_question":
            self.wrong_questions.append(ref)
        return ref

    def ensure_user(self, student_no, nickname, password_hash, created, old_no=None, bio=None):
        fields = f"student_no={literal(student_no)}, nickname={literal(nickname)}, password={literal(password_hash)}"
        if old_no:
            self.sql.append(f"UPDATE `user` SET {fields} WHERE student_no={literal(old_no)}")
        insert_bio = f",bio={literal(bio)}" if bio else ''
        self.sql.append(
            f"INSERT INTO `user` SET {fields},create_time={literal(created)}{insert_bio} "
            f"ON DUPLICATE KEY UPDATE nickname=VALUES(nickname),password=VALUES(password)"
        )
        key = f"@campus_student_{student_no}"
        self.sql.append(f"SELECT id INTO {key} FROM `user` WHERE student_no={literal(student_no)}")
        return Ref(key)

    def add_pair(self, table, one, two, **fields):
        first = Ref(f'LEAST({one.sql},{two.sql})')
        second = Ref(f'GREATEST({one.sql},{two.sql})')
        return self.insert(table, user1_id=first, user2_id=second, **fields)

    def alert(self, user, title, content, created, *, kind='interact', biz=None, target=None, read=0):
        return self.insert('message', user_id=user, type=kind, title=title, content=content, biz_type=biz,
                           biz_id=target, is_read=read, create_time=created)


def normalize_existing(seed):
    users = f"SELECT id FROM `user` WHERE student_no BETWEEN '{FIRST_PEER}' AND '{FIRST_PEER + len(NAMES) - 1}'"
    for table, columns in {
        'idle_item': {'description': [('演示物品，配图为示意。', ''), ('测试', '检查')]},
        'activity': {'description': [('校园演示活动，配图为示意。', '')]},
        'lost_found': {'description': [('演示记录，配图为物品示意。', '')]},
        'study_partner': {'subject': [('普通话水平测试', '普通话水平考试')]},
        'campus_question': {'title': [('小组演示', '小组展示')]},
        'ai_session': {'title': [('演示指南', '指南'), ('演示讲义', '讲义')]},
        'ai_call_log': {'model': [('campus-demo', '校园助手')], 'error_msg': [('演示记录：', '')]},
    }.items():
        for column, pairs in columns.items():
            for before, after in pairs:
                seed.sql.append(f"UPDATE {table} SET {column}=REPLACE({column},{literal(before)},{literal(after)}) "
                                f"WHERE user_id IN ({users}) AND INSTR({column},{literal(before)})>0")
    for table, fields in {
        'idle_item': ('title', 'description'), 'activity': ('title', 'description'),
        'lost_found': ('title', 'description'), 'post': ('content',),
        'study_partner': ('subject', 'goal', 'intro'), 'campus_question': ('title', 'content'),
        'message': ('title', 'content'), 'wrong_question': ('question', 'tag'),
    }.items():
        for column in fields:
            for label in ('【演示】', '（演示配图）'):
                seed.sql.append(f"UPDATE {table} SET {column}=REPLACE({column},{literal(label)},'') "
                                f"WHERE INSTR({column},{literal(label)})>0")
    seed.sql.append(
        f"UPDATE idle_item SET description=REPLACE(description,{literal('无图物品测试款，')},'') "
        f"WHERE INSTR(description,{literal('无图物品测试款，')})>0")
    seed.sql += [
        f"UPDATE notice SET title=SUBSTRING(title,4),content=REPLACE(content,{literal('**校园演示公告**')},"
        f"{literal('**校园公告**')}) WHERE LEFT(title,3)={literal('演示｜')}",
        f"UPDATE draw_game_member SET nickname=REPLACE(nickname,{literal('·演示')},'') WHERE user_id IN ({users})",
        f"UPDATE message SET title={literal('个人中心使用提醒')},"
        f"content={literal('你可以在个人中心查看收藏、报名、闲置预约和复习记录。')} "
        f"WHERE user_id IN ({users}) AND title={literal('校园演示场景已准备')}",
    ]


def refresh_handbooks_and_labels(statements):
    managed = (f"SELECT id FROM `user` WHERE student_no='{ORANGE}' OR "
               f"student_no BETWEEN '{FIRST_PEER}' AND '{FIRST_PEER + len(NAMES) - 1}'")
    for old_name, key in HANDBOOKS.items():
        content = (ASSETS / f'{key}.txt').read_text(encoding='utf-8')
        pdf = (ASSETS / f'{key}.pdf').read_bytes()
        url = 'data:application/pdf;base64,' + base64.b64encode(pdf).decode('ascii')
        statements.append(
            f"UPDATE pdf_document SET file_name={literal(content.splitlines()[0] + '.pdf')},"
            f"file_url={literal(url)},text_content={literal(content)},page_count=1 "
            f"WHERE user_id IN ({managed}) AND file_name={literal(old_name)}"
        )
    old = literal('用投影做小组演示')
    new = literal('用投影做小组展示')
    statements.append(f"UPDATE message SET content=REPLACE(content,{old},{new}) "
                      f"WHERE user_id IN ({managed}) AND INSTR(content,{old})>0")
    statements.append(
        f"UPDATE ai_message m JOIN ai_session s ON s.id=m.session_id "
        f"SET m.content={literal('分页检查要包含什么？')} WHERE s.user_id IN ({managed}) "
        f"AND m.content={literal('分页测试要包含什么？')}"
    )


def campus_life(seed, people, now):
    participants = len(people)
    cohort_names = ('图书馆', '学生社区', '创新工坊', '校园社团', '院系联络组', '东区书友会')
    locations = ('图书馆东门', '学生活动中心一层', '第二食堂外侧', '体育馆南门', '创新工坊大厅')
    public = {key: [] for key in ('idle', 'activity', 'lostfound', 'post', 'partner', 'qa')}
    for lap in range(6):
        for index, owner in enumerate(people):
            buyer = people[(index + 1 + lap % 3) % participants]
            friend = people[(index + 5) % participants]
            created = now - timedelta(days=33 - 4 * lap, hours=index % 8)
            title, category, photo, desc, want = IDLE[(index + lap * 5) % len(IDLE)]
            meeting_place = locations[(index + lap) % len(locations)]
            item_state = lap % 3
            item = seed.insert('idle_item', user_id=owner, title=f'{title}｜{meeting_place}可交接', category=category,
                               description=f'{desc} 方便在{meeting_place}核对物品。', expect_item=want,
                               images=seed.images(f'idle-{photo}'), status=item_state, audit_status=1,
                               create_time=created, update_time=created + timedelta(days=2))
            public['idle'].append(item)
            appointment = seed.insert(
                'idle_appointment', item_id=item, buyer_id=buyer, seller_id=owner,
                message=f'我可以带{want}，方便在{meeting_place}碰面吗？',
                status=(0, 1, 3)[item_state], create_time=created + timedelta(hours=4),
                update_time=created + timedelta(days=2),
            )
            if item_state:
                done = int(item_state == 2)
                meet = created + timedelta(days=2) if done else now + timedelta(days=2 + lap, hours=index % 4)
                seed.insert('idle_handoff', appointment_id=appointment, version=2, proposed_by=owner,
                            meeting_at=meet, location=meeting_place, confirmed=1, buyer_done=done, seller_done=done,
                            reminder_sent=done)
                if done:
                    for author, recipient, comment in (
                        (buyer, owner, '对方提前告知了使用痕迹，当面核对后完成交换。'),
                        (owner, buyer, '约在教学区公共地点，提前沟通了到达时间。'),
                    ):
                        seed.insert('idle_review', appointment_id=appointment, from_user_id=author,
                                    to_user_id=recipient, score=4 + index % 2, content=comment,
                                    create_time=meet + timedelta(hours=1))
            seed.alert(buyer, '闲置预约进度', f'你预约的「{title}」可在我的预约中查看。',
                       created + timedelta(days=2), biz='handoff_buyer', target=appointment, read=int(lap < 4))

            event_title, event_type, event_photo, venue, event_desc = EVENTS[(index * 3 + lap) % len(EVENTS)]
            start = (now + timedelta(days=(3 + lap + index % 5) if lap >= 2 else -6 + lap * 3)).replace(
                hour=14 + index % 5, minute=30, second=0)
            deadline = start - timedelta(hours=16)
            past, full = start < now, lap == 5
            event = seed.insert('activity', user_id=owner, title=f'{cohort_names[lap]} · {event_title}',
                                description=event_desc, location=venue, category=event_type,
                                images=seed.images(f'campus-{event_photo}'), start_time=start,
                                end_time=start + timedelta(hours=2), signup_deadline=deadline,
                                max_members=3 if full else 20, audit_status=1, status=2 if past else 0,
                                create_time=created)
            public['activity'].append(event)
            for place in (1, 3, 6):
                member = people[(index + place) % participants]
                when = created + timedelta(hours=place)
                membership = seed.insert('activity_member', activity_id=event, user_id=member,
                                         remark='已确认地点和活动时间。', status=1, create_time=when)
                if past:
                    seed.insert('activity_reminder', member_id=membership, start_time=start)
                    seed.insert('activity_signin', activity_id=event, user_id=member,
                                sign_time=start + timedelta(minutes=place))
                if place == 1 or member is people[0]:
                    seed.alert(member, '活动报名已通过', f'已报名「{event_title}」，地点为{venue}。',
                               when + timedelta(minutes=20), biz='activity', target=event, read=int(past))
            if full:
                waiting = people[(index + 8) % participants]
                seed.insert('activity_waitlist', activity_id=event, user_id=waiting, state='WAITING',
                            queued_at=created + timedelta(hours=8))
                seed.alert(waiting, '已加入活动候补', f'「{event_title}」已满额，有空位时会通知你。',
                           created + timedelta(hours=8), biz='activity', target=event)

            lost_name, lost_photo, spot, clue = LOST[(index + lap * 3) % len(LOST)]
            recovered = lap in (0, 3)
            found = recovered or lap % 2 == 0
            lost = seed.insert('lost_found', user_id=owner, type=int(found),
                               title=f'{"拾到" if found else "寻找"}{lost_name}｜{cohort_names[lap]}',
                               description=f'{clue} 请通过站内私信核对细节。', images=seed.images(f'lost-{lost_photo}'),
                               location=spot, happen_time=created - timedelta(hours=1),
                               audit_status=1, status=int(recovered), create_time=created)
            public['lostfound'].append(lost)
            if found:
                seed.insert('lost_found_claim', lost_found_id=lost, claim_user_id=friend,
                            message=f'我那天经过{spot}，可以核对物品细节和时间。',
                            status=3 if recovered else 0, create_time=created + timedelta(hours=2),
                            update_time=created + timedelta(hours=5))
                seed.alert(owner, '收到失物认领申请', f'有同学认领「{lost_name}」，请先核对细节。',
                           created + timedelta(hours=2), biz='lostfound', target=lost, read=int(recovered))

            post_photo, body = POSTS[(index * 5 + lap) % len(POSTS)]
            journal = (
                '回去准备和室友交换今天各自记下的一件小事。',
                '忙完课设后难得有空，想把这段日常记下来。',
                '下次想约同班同学一起，顺便听听大家的近况。',
                '这周课排得满，给自己留这一小段休息时间挺好。',
                '本来只是顺路经过，后来比计划多留了半小时。',
                '最近总在图书馆和教学楼两头跑，今天终于换了个节奏。',
            )[lap]
            post = seed.insert('post', user_id=owner, content=f'{body}\n{journal}', images=seed.images(f'campus-{post_photo}'),
                               like_count=3, comment_count=2, audit_status=1, create_time=created)
            public['post'].append(post)
            for offset in (1, 4, 7):
                seed.insert('post_like', post_id=post, user_id=people[(index + offset) % participants],
                            create_time=created + timedelta(minutes=15 + offset))
            for offset, comment in (
                (2, ('我也常去那边，下次路过会留意。', '喜欢这组记录，读完想出去走走。')[lap % 2]),
                (5, ('谢谢分享，周末打算和室友一起去。', '今天也忙了一天，看到这条正好歇一会。')[index % 2]),
            ):
                seed.insert('post_comment', post_id=post, user_id=people[(index + offset) % participants],
                            content=comment, create_time=created + timedelta(minutes=40 + offset))

            subject, goal, schedule, introduction = PARTNERS[(index + lap * 5) % len(PARTNERS)]
            partner = seed.insert('study_partner', user_id=owner, subject=subject, goal=goal, schedule=schedule,
                                  intro=introduction, contact='见面时间可在站内商量。', status=int(lap < 2),
                                  audit_status=1, audit_source='manual', create_time=created)
            public['partner'].append(partner)

            topic, question_title, question_body, answer = QUESTIONS[(index + lap * 7) % len(QUESTIONS)]
            accepted = int(lap not in (2, 5))
            situations = (
                '我先把问题记在复习本里，想弄清楚后再做一遍。',
                '同组几个人看法不太一样，想找一个便于验证的办法。',
                '下周要跟同学交流这件事，我想先整理好自己的做法。',
                '手头时间不多，想知道最先该检查哪一步。',
                '上次碰到类似情况时处理得比较匆忙，这次想事先弄明白。',
                '希望了解原因，后面遇到相近的情况能自己判断。',
            )
            question = seed.insert('campus_question', user_id=owner, title=question_title,
                                   content=f'{question_body}\n{situations[lap]}',
                                   category=topic, status=0, view_count=20 + (5 * index + 7 * lap) % 68,
                                   create_time=created)
            public['qa'].append(question)
            reply = seed.insert('campus_answer', question_id=question, user_id=friend, content=answer,
                                is_accepted=accepted, create_time=created + timedelta(hours=3))
            if accepted:
                seed.update('campus_question', question, status=1, accepted_answer_id=reply)
            seed.alert(owner, '互助问答有新回复', question_title, created + timedelta(hours=3), biz='qa',
                       target=question, read=accepted)
            for kind, ref in (('idle', item), ('activity', event), ('post', post)):
                seed.insert('favorite', user_id=buyer, target_type=kind, target_id=ref,
                            create_time=created + timedelta(hours=3))
    return public


def social(seed, people, now, known_pairs):
    friendships, conversations = known_pairs
    for index, one in enumerate(people):
        for offset in (1, 2, 4):
            other = (index + offset) % len(people)
            if tuple(sorted((index, other))) in friendships:
                continue
            two = people[other]
            pair = seed.add_pair('friendship', one, two, requester_id=one, status='accepted',
                                 greeting='之前在校园活动见过，想加个好友。', update_time=now - timedelta(days=12))
            if offset == 1:
                seed.alert(one, '好友申请已通过', '现在可以在通讯录找到对方。', now - timedelta(days=12),
                           biz='friendship', target=pair, read=1)
        if 7 <= index <= 10 and (0, index) not in friendships:
            primary = people[0]
            requester = one if index % 2 else primary
            recipient = primary if index % 2 else one
            pending = seed.add_pair('friendship', one, primary, requester_id=requester, status='pending',
                                    greeting='想一起约个时间交流复习资料。', update_time=now - timedelta(days=2))
            seed.alert(recipient, '新的好友申请', '想一起约个时间交流复习资料。', now - timedelta(days=2),
                       biz='friendship', target=pending)

    for offset in (5, 6, 11, 12, 13):
        if (0, offset) not in friendships and offset != len(people) - 4:
            seed.add_pair('friendship', people[0], people[offset], requester_id=people[offset], status='accepted',
                          greeting='下次一起去图书馆复习吧。', update_time=now - timedelta(days=10))

    for index in range(len(people)):
        for offset in (1, 2, 3):
            other = (index + offset) % len(people)
            if tuple(sorted((index, other))) in conversations:
                continue
            one, two = people[index], people[other]
            started = now - timedelta(days=7 - offset, hours=index % 10)
            chat = seed.add_pair('chat_conversation', one, two, context_type='profile', context_id=two,
                                 create_time=started)
            write_chat(seed, chat, (one, two), CONVERSATIONS[(index + offset) % len(CONVERSATIONS)], started,
                       f'direct-{index}-{offset}')

    for index in range(10):
        names = ('概率论课后答疑', '校园摄影周末约拍', '东区食堂吃饭搭子', '图书馆读书交流',
                 '周三羽毛球双打', '二教课设接口联调', '四级晨间听力', '志愿活动签到联络',
                 '线代公式卡交换', '夜跑与拉伸')
        leader = people[0 if index < 5 else index]
        members = (leader, people[index + 1], people[index + 3])
        started = now - timedelta(days=4, hours=index)
        chat = seed.insert('chat_conversation', group_name=names[index], group_owner_id=leader, create_time=started)
        write_chat(seed, chat, members, CONVERSATIONS[index % len(CONVERSATIONS)], started, f'group-{index}')
        invitee = people[0 if index >= 5 else index + 6]
        seed.insert('chat_group_invite', conversation_id=chat, inviter_id=leader, invitee_id=invitee)
        seed.alert(invitee, '收到群聊邀请', f'邀请你加入「{names[index]}」。', started + timedelta(hours=1),
                   biz='group_invite', target=chat)


def write_chat(seed, chat, members, messages, start, key):
    last_read = [None] * len(members)
    for index, content in enumerate(messages):
        sender = index % len(members)
        receipt = index != len(messages) - 1
        time = start + timedelta(minutes=3 * index)
        message = seed.insert('chat_message', conversation_id=chat, sender_id=members[sender],
                              receiver_id=members[1 - sender] if len(members) == 2 else None,
                              client_message_id=f'wt261116-{key}-{index}', message_type='text', content=content,
                              status=int(receipt), read_time=time + timedelta(minutes=1) if receipt else None,
                              create_time=time)
        if receipt:
            last_read = [message] * len(members)
    seed.update('chat_conversation', chat, last_message_id=message, last_message_summary=messages[-1],
                last_message_time=time, update_time=time)
    for index, member in enumerate(members):
        seed.insert('chat_conversation_member', conversation_id=chat, user_id=member,
                    unread_count=int(index != sender), last_read_message_id=last_read[index],
                    read_time=time - timedelta(minutes=2), pinned=int(index == 0 and key.startswith('group')),
                    create_time=start, update_time=time)


def study(seed, people, now):
    for index, learner in enumerate(people):
        first = len(seed.wrong_questions)
        add_wrong_questions(seed, learner, now)
        content = (
            ('求 x 趋于 0 时 sin(5x)/x 的极限。', '5', '提取系数 5 后应用基本极限。'),
            ('求 y=x³eˣ 的导数。', 'eˣ(x³+3x²)', '分别对两个因子求导，再相加。'),
            ('求 0 到 2 上 2x 的定积分。', '4', '原函数为 x²，代入上下限。'),
            ('怎样定义 f(2)，使 f(x)=(x²-4)/(x-2) 在 x=2 连续？', '4', '约分后得到 x+2，因此极限为 4。'),
        )
        for source, (question, answer, explanation) in zip(seed.wrong_questions[first:], content):
            seed.insert('wrong_question_generated', user_id=learner, wrong_question_id=source,
                        question=question, answer=answer, analysis=explanation, create_time=now - timedelta(days=2))
        records = SCENES if index == 0 else tuple(SCENES[(index + offset) % len(SCENES)] for offset in range(4))
        for offset, (title, topic, question, response) in enumerate(records):
            started = now - timedelta(days=1 + offset, hours=index % 7)
            scene = 'code_fix' if topic == '代码' else ('agent' if topic in ('校园向导', '活动', '闲置', '失物', '搭子', '问答') else 'chat')
            session = seed.insert('ai_session', user_id=learner, scene=scene, title=title, create_time=started,
                                  update_time=started + timedelta(seconds=4))
            seed.insert('ai_message', session_id=session, role='user', content=question, create_time=started)
            seed.insert('ai_message', session_id=session, role='assistant', content=response,
                        create_time=started + timedelta(seconds=4))
        for offset, (key, title, question, answer) in enumerate((
            ('campus-library-handbook', '图书馆研讨室使用指南', '小组讨论前需要做什么？',
             '先确认研讨室可预约时段，预约后按时到场，取消使用时及时释放时段。'),
            ('campus-lab-handbook', '实验记录与复盘讲义', '需要删除异常读数吗？',
             '保留原读数，记录当时的操作和环境。需要更正时写明更正依据。'),
        )):
            started = now - timedelta(days=4 + offset, hours=index % 7)
            doc = seed.pdf(learner, key, started)
            session = seed.insert('ai_session', user_id=learner, scene='pdf', title=title, doc_id=doc,
                                  create_time=started, update_time=started + timedelta(minutes=2))
            seed.insert('ai_message', session_id=session, role='user', content=question, create_time=started)
            seed.insert('ai_message', session_id=session, role='assistant', content=answer,
                        create_time=started + timedelta(minutes=2))


def games(seed, people, now):
    nicknames = ('橙子', *NAMES)
    for index in range(18):
        title, words = GAMES[index % len(GAMES)]
        seats = (index % len(people), (index + 1) % len(people), (index + 3) % len(people))
        if 1 <= index <= 4:
            seats = (0, index + 1, index + 3)
        started = now - timedelta(days=1 + index, hours=3)
        finished = started + timedelta(minutes=5)
        room = seed.insert('draw_game_room', room_code=MARKER if index == 0 else f'WT2611{index:03}',
                           owner_user_id=people[seats[0]], title=title, status='FINISHED',
                           max_players=6, rounds_per_player=1, current_turn=3, created_at=started,
                           updated_at=finished)
        for seat, person in enumerate(seats, 1):
            seed.insert('draw_game_member', room_id=room, user_id=people[person], seat_no=seat,
                        nickname=nicknames[person], score=(3, 3, 2)[seat - 1], active=0,
                        joined_at=started + timedelta(seconds=seat * 3), last_visited_at=finished)
        for turn, (person, word) in enumerate(zip(seats, words), 1):
            turn_start = started + timedelta(seconds=90 + 60 * (turn - 1))
            end = turn_start + timedelta(seconds=60)
            artwork = seed.image_resource(people[person], f'game-{(index % len(GAMES)) * 3 + turn}', end)
            seed.insert('draw_game_round', room_id=room, turn_number=turn, drawer_user_id=people[person],
                        word=word, status='FINISHED', started_at=turn_start, ended_at=end,
                        snapshot_resource_id=artwork)


def workflow_records(seed, people, public, now, admin):
    for index, user in enumerate(people):
        for offset in range(3):
            position = index + offset * len(people)
            event = public['activity'][position]
            item = public['idle'][position]
            created = now - timedelta(days=2 + offset)
            seed.alert(user, '发布内容审核通过', '你的闲置物品已在校园闲置列表公开。', created,
                       kind='audit', biz='idle', target=item, read=int(offset > 0))
            seed.insert('agent_action', id=str(uuid4()), user_id=user, activity_id=event,
                        activity_title=EVENTS[(index * 3 + offset) % len(EVENTS)][0],
                        remark='想参加这场校园活动。', state='PENDING',
                        expires_at=created + timedelta(minutes=30), created_at=created)
            seed.insert('agent_idle_draft', id=str(uuid4()), user_id=user, item_id=item,
                        title=IDLE[(index + offset * 5) % len(IDLE)][0],
                        description=IDLE[(index + offset * 5) % len(IDLE)][3],
                        category=IDLE[(index + offset * 5) % len(IDLE)][1],
                        expect_item=IDLE[(index + offset * 5) % len(IDLE)][4], expires_at=created)
            title, category, _, location, description = EVENTS[(index * 3 + offset) % len(EVENTS)]
            seed.insert('agent_activity_draft', id=str(uuid4()), user_id=user, activity_id=event, title=title,
                        description=description, category=category, location=location, expires_at=created)
        seed.alert(user, '图书馆自习区使用提醒', '讨论请使用研讨室，离开时请带走随身物品。',
                   now - timedelta(days=1), kind='system', read=index % 2)
        seed.insert('report', reporter_id=user, target_type='post', target_id=public['post'][(index + 1) % len(people)],
                    reason_type='other', reason=('请核对随手拍是否涉及他人隐私。', '正文的集合信息可能已经变更。')[index % 2],
                    status=index % 2, create_time=now - timedelta(days=1))
    for index, (title, body, photo) in enumerate(NOTICES):
        created = now - timedelta(days=2 + index)
        seed.insert('notice', admin_id=admin, title=f'本周安排｜{title}', content=body, cover=seed.cover(f'campus-{photo}'),
                    status=1 if index < 5 else 0, create_time=created,
                    publish_time=created + timedelta(hours=1) if index < 5 else None)


def moderation_and_drafts(seed, people, public, now):
    for index, user in enumerate(people):
        created = now - timedelta(hours=3, minutes=index * 2)
        lost = seed.insert('lost_found', user_id=user, type=1, title='教学楼拾获一把折叠伞',
                           description='请认领人核对伞柄的挂绳特征，方便在一楼前台交接。',
                           location='教学楼一楼前台', images=seed.images('lost-black-umbrella'),
                           audit_status=0, status=0, happen_time=created - timedelta(hours=1), create_time=created)
        seed.insert('post', user_id=user, content='傍晚路过体育场，沿跑道走了两圈。晚饭前的风比中午凉快。',
                    images=seed.images('campus-track'), audit_status=0, create_time=created + timedelta(minutes=15))
        seed.insert('study_partner', user_id=user, subject='周末课程复盘', goal='交流两道课后题的解题思路',
                    schedule='周六下午，图书馆一层', intro='希望先各自完成，再交换草稿。',
                    audit_status=0, status=0, create_time=created + timedelta(minutes=30))
        seed.insert('idle_item', user_id=user, title='宿舍用折叠台灯', category='生活用品',
                    description='灯臂可折叠，外壳有轻微使用痕迹。', images=seed.images('idle-lamp'),
                    expect_item='一本空白记录本', audit_status=0, status=0, create_time=created)
        start = (now + timedelta(days=20 + index % 5)).replace(hour=15, minute=0, second=0)
        seed.insert('activity', user_id=user, title='期末复习笔记交流', category='学习交流',
                    description='每人带一页整理过的笔记，在研讨室交换复习思路。',
                    images=seed.images('campus-study-desk'), location='图书馆二层研讨室',
                    start_time=start, end_time=start + timedelta(hours=1), signup_deadline=start - timedelta(days=1),
                    max_members=12, audit_status=0, status=0, create_time=created)
        answer_target = public['qa'][(index + 1) % len(people)]
        for kind, form, target, submitted in (
            ('lost', {'type': 1, 'title': '教学楼拾获一把折叠伞', 'location': '教学楼一楼前台'}, None, lost),
            ('partner', {'subject': '数据库课程复盘', 'goal': '把连接查询和分组条件理清'}, None, None),
            ('question', {'title': '期末复习怎么安排间隔', 'content': '两门课的考试时间只差一天。', 'category': '考试'}, None, None),
            ('answer', {'content': QUESTIONS[(index + 1) % len(QUESTIONS)][3]}, answer_target, None),
        ):
            seed.insert('agent_campus_draft', id=str(uuid4()), user_id=user, kind=kind,
                        form=json.dumps(form, ensure_ascii=False), target_id=target, submitted_id=submitted,
                        expires_at=now + timedelta(minutes=20))

    seed.sql.append(
        f"INSERT IGNORE INTO user_block (user_id,blocked_user_id,create_time) "
        f"VALUES ({people[0].sql},{people[14].sql},{literal(now - timedelta(hours=2))})"
    )


def existing_social_pairs():
    lookup = {ORANGE: 0}
    for index in range(len(NAMES)):
        lookup[f'CAMPUSDEMO{index + 1:02}'] = index + 1
        lookup[str(FIRST_PEER + index)] = index + 1
    results = []
    for table in ('friendship', 'chat_conversation'):
        rows = mysql(PROJECT, f"""SELECT a.student_no,b.student_no FROM {table} r
            JOIN user a ON a.id=r.user1_id JOIN user b ON b.id=r.user2_id;""")
        pairs = set()
        for row in rows.splitlines():
            left, right = row.split('\t')
            if left in lookup and right in lookup:
                pairs.add(tuple(sorted((lookup[left], lookup[right]))))
        results.append(pairs)
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--password-stdin', action='store_true', help='Read a password from standard input')
    options = parser.parse_args()
    if mysql(PROJECT, f"SELECT COUNT(*) FROM draw_game_room WHERE room_code='{MARKER}';") != '0':
        if options.apply:
            statements = ['START TRANSACTION']
            refresh_handbooks_and_labels(statements)
            mysql(PROJECT, ';\n'.join([*statements, 'COMMIT']) + ';\n')
        print('Campus records are already present.')
        return
    admin = mysql(PROJECT, "SELECT id FROM admin WHERE role='super' AND status=0 ORDER BY id LIMIT 1;")
    if not admin.isdigit():
        raise RuntimeError('No active administrator found.')
    password = input() if options.password_stdin else getpass.getpass('Password for the campus accounts: ')
    if len(password) < 6:
        raise ValueError('Enter at least six characters.')
    password_hash = bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=10)).decode()
    now = datetime.now(ZoneInfo('Asia/Shanghai')).replace(tzinfo=None, microsecond=0)
    seed = CampusSeed()
    people = [seed.ensure_user(ORANGE, '橙子', password_hash, now - timedelta(days=60), bio='喜欢读书、跑步和在校园里随手拍。')]
    for index, name in enumerate(NAMES):
        people.append(seed.ensure_user(str(FIRST_PEER + index), name, password_hash, now - timedelta(days=80 + index),
                                       old_no=f'CAMPUSDEMO{index + 1:02}', bio=BIOS[index % len(BIOS)]))
    normalize_existing(seed)
    refresh_handbooks_and_labels(seed.sql)
    public = campus_life(seed, people, now)
    social(seed, people, now, existing_social_pairs())
    study(seed, people, now)
    games(seed, people, now)
    workflow_records(seed, people, public, now, int(admin))
    moderation_and_drafts(seed, people, public, now)
    seed.sql.append('COMMIT' if options.apply else 'ROLLBACK')
    mysql(PROJECT, ';\n'.join(seed.sql) + ';\n')
    print(f"{'Committed' if options.apply else 'Validated and rolled back'} {seed.serial} records for "
          f"{len(people)} accounts using {len(seed.covers)} matching campus images.")


if __name__ == '__main__':
    main()
