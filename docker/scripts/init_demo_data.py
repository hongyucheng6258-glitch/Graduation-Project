# -*- coding: utf-8 -*-
"""
Ai-campus 演示数据初始化脚本（可重复执行）
================================================
- 连接本地 MySQL，清空演示账号(id=1..6)及其业务数据，再重新插入。
- 不触碰 admin / ai_config / system_config / prompt_template / sensitive_word。
- 图片：从 demo-images/web 读取 JPG，转 base64 data URI，存入 images 字段(JSON 数组)。
- 时间基准：2026-09-22；create_time 落在 2026-09-16 ~ 2026-09-22。
- 用法：python init_demo_data.py
"""
import os
import sys
import json
import base64
import pymysql

DB_PASSWORD = os.environ.get("DB_PASSWORD")
if not DB_PASSWORD:
    raise SystemExit("Set DB_PASSWORD before running this demo-data script.")
if os.environ.get("CONFIRM_DEMO_DATA_RESET") != "YES":
    raise SystemExit("Set CONFIRM_DEMO_DATA_RESET=YES to confirm replacing demo data.")

# ---------------- 配置 ----------------
DB_CONF = dict(
    host="127.0.0.1", port=3306, user="root", password=DB_PASSWORD,
    database="ai_campus_platform", charset="utf8mb4", autocommit=False,
)
IMG_DIR = os.environ.get("IMG_DIR", os.path.join(os.path.dirname(__file__), "demo-images", "web"))
BCRYPT_HASH = "$2a$10$mhhWC1d1vn0htoHLVFgbquJUvefFeCMJAdFLNJb1j4/lXXfnd.lPe"
DEMO_USER_IDS = (1, 2, 3, 4, 5, 6)

# 各表插入行数统计
STATS = {}


def img_uri(filename):
    """读取 web/<filename>.jpg 并返回 data:image/jpeg;base64,..."""
    path = os.path.join(IMG_DIR, filename)
    with open(path, "rb") as f:
        data = base64.b64encode(f.read()).decode("ascii")
    return "data:image/jpeg;base64," + data


def imgs_json(*filenames):
    """多图 -> JSON 字符串数组"""
    return json.dumps([img_uri(fn) for fn in filenames], ensure_ascii=False)


# ---------------- 连接 ----------------
conn = pymysql.connect(**DB_CONF)
cur = conn.cursor()


def q(sql, args=None):
    cur.execute(sql, args or ())
    return cur


def insert(table, cols, rows):
    if not rows:
        STATS[table] = 0
        return
    sql = f"INSERT INTO `{table}` ({','.join('`'+c+'`' for c in cols)}) VALUES ({','.join(['%s']*len(cols))})"
    cur.executemany(sql, rows)
    STATS[table] = len(rows)


def ensure_columns():
    """确保 study_partner 有 images 字段（演示需要，老库可能没有）"""
    q("""SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='study_partner' AND COLUMN_NAME='images'""")
    (cnt,) = cur.fetchone()
    if cnt == 0:
        q("ALTER TABLE `study_partner` ADD COLUMN `images` MEDIUMTEXT NULL COMMENT '图片 JSON 数组' AFTER `contact`")
        print("[setup] study_partner 已添加 images 字段")


# ---------------- 1. 清理旧演示数据 ----------------
def cleanup():
    ensure_columns()
    # 子表先删
    child_tables = [
        "post_like", "post_comment", "activity_signin", "activity_member",
        "idle_review", "idle_appointment", "lost_found_claim",
        "campus_answer", "favorite", "report", "message",
    ]
    for t in child_tables:
        try:
            q(f"DELETE FROM `{t}`")
        except Exception as e:
            print(f"  [warn] delete {t}: {e}")
    # 业务主表：按演示用户删
    q("DELETE FROM `post` WHERE user_id IN %s", (DEMO_USER_IDS,))
    q("DELETE FROM `idle_item` WHERE user_id IN %s", (DEMO_USER_IDS,))
    q("DELETE FROM `activity` WHERE user_id IN %s", (DEMO_USER_IDS,))
    q("DELETE FROM `lost_found` WHERE user_id IN %s", (DEMO_USER_IDS,))
    q("DELETE FROM `study_partner` WHERE user_id IN %s", (DEMO_USER_IDS,))
    q("DELETE FROM `campus_question` WHERE user_id IN %s", (DEMO_USER_IDS,))
    # 公告：admin_id=1
    q("DELETE FROM `notice` WHERE admin_id=1")
    # 用户
    q("DELETE FROM `user` WHERE id IN %s", (DEMO_USER_IDS,))

    # 重置自增
    all_tables = child_tables + [
        "post", "idle_item", "activity", "lost_found", "study_partner",
        "campus_question", "notice", "user",
    ]
    for t in all_tables:
        try:
            q(f"ALTER TABLE `{t}` AUTO_INCREMENT=1")
        except Exception:
            pass
    conn.commit()
    print("[cleanup] 旧演示数据已清空，自增ID重置为1")


# ---------------- 2. 用户 ----------------
def seed_users():
    rows = [
        (1, "2021001", "张三", 1, "计算机学院大三学生，爱编程",  "13800010001"),
        (2, "2021002", "李四", 1, "数理学院大二学生，考研党",   "13800010002"),
        (3, "2021003", "王五", 0, "外国语学院大一新生",         "13800010003"),
        (4, "2021004", "赵六", 2, "艺术学院大四学姐",           "13800010004"),
        (5, "2021005", "陈七", 1, "研究生一年级，AI方向",       "13800010005"),
        (6, "2021006", "周八", 2, "经管学院大三，社团活跃分子", "13800010006"),
    ]
    sql = ("INSERT INTO `user`(id,student_no,nickname,password,gender,bio,phone,status,create_time) "
           "VALUES (%s,%s,%s,%s,%s,%s,%s,0,'2026-09-16 09:00:00')")
    cur.executemany(sql, [(r[0], r[1], r[2], BCRYPT_HASH, r[3], r[4], r[5]) for r in rows])
    STATS["user"] = len(rows)
    print(f"[seed] user = {len(rows)}")


# ---------------- 3. 闲置物品 ----------------
def seed_idle_item():
    # (id, user_id, title, description, images, category, audit_status, audit_reason, status, create_time)
    # 有图 1-11
    data = [
        (1,  1, "二手自行车", "黑色男式山地车，八九成新，变速正常，代步神器，宿舍楼下可看车。", "idle_01_bicycle.jpg", "电子数码", 1, None, 0, "2026-09-16 10:00:00"),
        (2,  2, "高等数学教材", "同济第七版上册，有少量笔记，考研复习必备，考研党出。",       "idle_02_mathbook.jpg", "教材书籍", 1, None, 2, "2026-09-16 11:00:00"),
        (3,  3, "桌面台灯",     "白色LED护眼学习台灯，三档调光，考研熬夜必备，宿舍闲置。",     "idle_03_desk_lamp.jpg", "生活日用", 1, None, 2, "2026-09-16 14:00:00"),
        (4,  4, "无线耳机",     "白色真无线蓝牙耳机，续航约4小时，充电盒齐全，音质不错。",     "idle_04_earbuds.jpg",   "电子数码", 1, None, 0, "2026-09-17 09:00:00"),
        (5,  5, "机械键盘",     "青轴87键白色背光，打字手感清脆，换了静电容所以出。",         "idle_05_keyboard.jpg",  "电子数码", 1, None, 1, "2026-09-17 10:00:00"),
        (6,  6, "牛津高阶词典", "第10版双解词典，九成新，准备考雅思所以出。",                 "idle_06_dictionary.jpg","教材书籍", 1, None, 0, "2026-09-17 15:00:00"),
        (7,  1, "保温杯",       "不锈钢500ml深蓝色，保温效果好，冬天喝水必备。",             "idle_07_tumbler.jpg",   "生活日用", 1, None, 1, "2026-09-17 16:00:00"),
        (8,  2, "USB小风扇",    "桌面白色小风扇，三档风力，夏天宿舍救星。",                   "idle_08_fan.jpg",       "生活日用", 1, None, 0, "2026-09-18 09:00:00"),
        (9,  3, "标准7号篮球",  "橙色室外用篮球，打了半年，手感好，社团退了所以出。",         "idle_09_basketball.jpg","运动户外", 1, None, 0, "2026-09-18 10:00:00"),
        (10, 4, "瑜伽垫",       "紫色TPE瑜伽垫，卷起状态，无异味，宿舍锻炼用。",               "idle_10_yoga_mat.jpg",  "运动户外", 1, None, 0, "2026-09-18 14:00:00"),
        (11, 5, "充电宝",       "10000mAh黑色轻薄款，支持双向快充，考研随身带。",             "idle_11_powerbank.jpg", "电子数码", 1, None, 0, "2026-09-18 16:00:00"),
        # idle_12 无图测试
        (12, 6, "闲置收纳箱",   "无图物品测试款，塑料收纳箱两个，宿舍整理出。", None, "生活日用", 1, None, 0, "2026-09-19 09:00:00"),
        # 额外 12 条无图
        (13, 1, "Java编程书套装", "《Java核心技术》卷一卷二，八成新，计算机学院教材。",   None, "教材书籍", 1, None, 0, "2026-09-19 10:00:00"),
        (14, 2, "考研英语真题",  "历年考研英语真题解析，已做完一遍，带笔记。",             None, "教材书籍", 1, None, 0, "2026-09-19 11:00:00"),
        (15, 3, "女生双肩包",    "浅灰色帆布双肩包，容量大，上课通勤都可。",               None, "服饰鞋包", 1, None, 0, "2026-09-19 14:00:00"),
        (16, 4, "速写本",        "A4空白速写本两本，艺术学院用，画了几页。",               None, "其他",     1, None, 0, "2026-09-19 15:00:00"),
        (17, 5, "台式显示器",    "24寸1080P显示器，HDMI接口，实验室换了4K所以出。",        None, "电子数码", 1, None, 2, "2026-09-19 16:00:00"),
        (18, 6, "羽毛球拍",      "尤尼克斯入门拍，打了一年，社团双打用。",                 None, "运动户外", 1, None, 0, "2026-09-20 09:00:00"),
        (19, 1, "台灯灯泡",      "LED灯泡两个，螺口，宿舍台灯备用。",                       None, "生活日用", 1, None, 0, "2026-09-20 10:00:00"),
        (20, 2, "考研政治讲义",  "肖秀荣考研政治精讲精练，九成新，带划线。",                None, "教材书籍", 1, None, 3, "2026-09-20 11:00:00"),
        # 待审核
        (21, 3, "闲置耳机架",    "金属耳机架一个，桌面整理用。",                             None, "生活日用", 0, None, 0, "2026-09-21 09:00:00"),
        (22, 4, "全新键盘膜",    "笔记本键盘膜，型号不对所以出。",                           None, "生活日用", 0, None, 0, "2026-09-21 10:00:00"),
        # 驳回
        (23, 5, "疑似违禁物品",  "标题含敏感内容，被AI审核标记。",                           None, "其他",     2, "内容涉及违规信息，请修改后重新发布", 3, "2026-09-21 11:00:00"),
        (24, 6, "外部引流商品",  "描述中包含外部联系方式。",                                 None, "其他",     2, "禁止外部引流，请移除联系方式",       3, "2026-09-21 14:00:00"),
    ]
    rows = []
    for d in data:
        (iid, uid, title, desc, img, cat, ast, areason, st, ct) = d
        images = imgs_json(img) if img else None
        rows.append((iid, uid, title, desc, images, cat, ast, areason, st, ct))
    cols = ["id", "user_id", "title", "description", "images", "category",
            "audit_status", "audit_reason", "status", "create_time"]
    insert("idle_item", cols, rows)
    print(f"[seed] idle_item = {len(rows)}")


# ---------------- 4. 闲置预约 ----------------
def seed_idle_appointment():
    # (id, item_id, buyer_id, seller_id, message, status, create_time)
    # item 状态自洽：
    #   item2,3,17 已完成(2) <- appt 2,3,8
    #   item5,7  已预约(1)  <- appt 1,6
    #   其余在架
    data = [
        (1, 5,  2, 5, "你好，键盘还在吗？想当面看一下。",            1, "2026-09-18 11:00:00"),  # 已接受
        (2, 2,  3, 2, "高数书还在吗？我正好需要。",                    3, "2026-09-17 09:00:00"),  # 已完成
        (3, 3,  4, 3, "台灯能送到宿舍楼下吗？",                          3, "2026-09-17 15:00:00"),  # 已完成
        (4, 4,  5, 4, "耳机续航怎么样？",                                0, "2026-09-20 10:00:00"),  # 待确认
        (5, 6,  6, 6, "词典能便宜点吗？",                                2, "2026-09-20 11:00:00"),  # 已拒绝
        (6, 7,  1, 1, "保温杯今天能交易吗？",                            1, "2026-09-18 17:00:00"),  # 已接受
        (7, 8,  2, 2, "小风扇我临时不要了，抱歉。",                      4, "2026-09-19 09:00:00"),  # 已取消
        (8, 17, 3, 5, "显示器还在吗？我实验室正好需要。",                3, "2026-09-20 15:00:00"),  # 已完成
    ]
    cols = ["id", "item_id", "buyer_id", "seller_id", "message", "status", "create_time"]
    insert("idle_appointment", cols, data)
    print(f"[seed] idle_appointment = {len(data)}")


# ---------------- 5. 闲置互评 ----------------
def seed_idle_review():
    # 只对已完成的预约(2,3,8)评价；这里对 appt 2 和 appt 3 双向评价 = 4 条
    data = [
        (1, 2,  3, 2, 5, "卖家很爽快，书很新，交易愉快！",            "2026-09-18 10:00:00"),
        (2, 2,  2, 3, 5, "买家很守时，沟通顺畅。",                    "2026-09-18 10:30:00"),
        (3, 3,  4, 3, 4, "台灯能用，就是包装简陋了点。",              "2026-09-18 16:00:00"),
        (4, 3,  3, 4, 5, "买家主动送楼下，态度很好。",                "2026-09-18 16:30:00"),
    ]
    cols = ["id", "appointment_id", "from_user_id", "to_user_id", "score", "content", "create_time"]
    insert("idle_review", cols, data)
    print(f"[seed] idle_review = {len(data)}")


# ---------------- 6. 活动 ----------------
def seed_activity():
    # (id, user_id, title, desc, images, category, location, start, end, deadline, max, audit, areason, status, ct)
    data = [
        # 有海报 1-6
        (1,  1, "校园杯3v3篮球争霸赛", "三人制篮球争霸赛，欢迎各学院组队参加，冠军有奖杯和奖金。",
         imgs_json("activity_01_basketball.jpg"), "体育竞技", "东区篮球场",
         "2026-09-26 14:00:00", "2026-09-26 18:00:00", "2026-09-25 23:59:00", 16,
         1, None, 0, "2026-09-16 10:00:00"),
        (2,  2, "志愿同行社区服务", "周末去附近社区做志愿服务，陪伴老人和小朋友，欢迎报名。",
         imgs_json("activity_02_volunteer.jpg"), "志愿服务", "学校正门集合",
         "2026-09-27 08:30:00", "2026-09-27 17:00:00", "2026-09-26 23:59:00", 0,
         1, None, 0, "2026-09-16 11:00:00"),
        (3,  3, "音为有你校园音乐节", "校园乐队专场演出，草坪音乐会，带野餐垫来听。",
         imgs_json("activity_03_music_festival.jpg"), "文艺演出", "西区操场草坪",
         "2026-09-28 19:00:00", "2026-09-28 21:30:00", "2026-09-27 23:59:00", 200,
         1, None, 0, "2026-09-17 09:00:00"),
        (4,  4, "Code Jam 24小时编程马拉松", "24小时极限编程挑战，组队完成作品，导师现场指导。",
         imgs_json("activity_04_code_jam.jpg"), "学术讲座", "计算机楼B座",
         "2026-10-01 09:00:00", "2026-10-02 09:00:00", "2026-09-30 23:59:00", 50,
         1, None, 0, "2026-09-17 10:00:00"),
        (5,  5, "书香校园读书分享会", "每月一期读书分享，本期主题《人类简史》。",
         imgs_json("activity_05_book_club.jpg"), "社团活动", "图书馆2楼研讨室",
         "2026-09-18 19:00:00", "2026-09-18 21:00:00", "2026-09-17 23:59:00", 30,
         1, None, 2, "2026-09-15 14:00:00"),
        (6,  6, "迎新晚会新起点新征程", "2026级迎新晚会，各学院节目轮番登场。",
         imgs_json("activity_06_welcome_party.jpg"), "文艺演出", "大礼堂",
         "2026-09-19 19:30:00", "2026-09-19 22:00:00", "2026-09-18 23:59:00", 500,
         1, None, 2, "2026-09-15 15:00:00"),
        # 无图 7-18
        (7,  1, "人工智能前沿讲座", "邀请业界专家分享大模型最新进展，欢迎研究生参加。",
         None, "学术讲座", "理科楼报告厅",
         "2026-09-29 14:00:00", "2026-09-29 16:00:00", "2026-09-28 23:59:00", 0,
         1, None, 0, "2026-09-18 09:00:00"),
        (8,  2, "考研数学冲刺串讲", "张老师亲授考研高数重点题型串讲。",
         None, "学术讲座", "教学楼A101",
         "2026-09-30 18:30:00", "2026-09-30 20:30:00", "2026-09-29 23:59:00", 40,
         1, None, 0, "2026-09-18 10:00:00"),
        (9,  3, "秋季校园马拉松", "5公里校园跑，完赛有纪念奖牌。",
         None, "体育竞技", "东校门起点",
         "2026-10-11 08:00:00", "2026-10-11 10:00:00", "2026-10-10 23:59:00", 0,
         1, None, 0, "2026-09-18 14:00:00"),
        (10, 4, "敬老院慰问志愿活动", "重阳节前去敬老院陪老人聊天。",
         None, "志愿服务", "敬老院",
         "2026-09-15 09:00:00", "2026-09-15 11:30:00", "2026-09-14 23:59:00", 0,
         1, None, 2, "2026-09-13 10:00:00"),
        (11, 5, "摄影社外拍活动", "秋日银杏林外拍，提供相机借用。",
         None, "社团活动", "银杏大道",
         "2026-09-16 15:00:00", "2026-09-16 17:30:00", "2026-09-15 23:59:00", 0,
         1, None, 2, "2026-09-14 09:00:00"),
        (12, 6, "桌游之夜", "狼人杀、阿瓦隆，新人友好。",
         None, "其他", "社团活动中心302",
         "2026-09-24 19:00:00", "2026-09-24 22:00:00", "2026-09-23 23:59:00", 0,
         0, None, 0, "2026-09-21 09:00:00"),
        (13, 1, "校外旅游团购", "组织周末外出旅游。",
         None, "其他", "待定",
         "2026-09-27 07:00:00", "2026-09-27 20:00:00", "2026-09-26 23:59:00", 0,
         2, "校外旅游存在安全风险，请联系学校备案", 3, "2026-09-21 10:00:00"),
        (14, 2, "电影放映会", "本周五经典电影放映。",
         None, "文艺演出", "学生活动中心",
         "2026-09-13 19:00:00", "2026-09-13 21:00:00", "2026-09-12 23:59:00", 0,
         1, None, 3, "2026-09-11 14:00:00"),
        (15, 3, "羽毛球双打赛", "院级双打比赛，名额有限报满即止。",
         None, "体育竞技", "体育馆羽毛球馆",
         "2026-09-28 09:00:00", "2026-09-28 17:00:00", "2026-09-27 23:59:00", 6,
         1, None, 1, "2026-09-18 16:00:00"),
        (16, 4, "图书馆整理志愿", "周末图书馆书架整理志愿活动。",
         None, "志愿服务", "图书馆一楼",
         "2026-09-29 09:00:00", "2026-09-29 11:00:00", "2026-09-28 23:59:00", 0,
         1, None, 0, "2026-09-19 10:00:00"),
        (17, 5, "辩论社招新表演赛", "欢迎围观辩论社年度表演赛。",
         None, "社团活动", "教学楼B201",
         "2026-09-17 19:00:00", "2026-09-17 21:00:00", "2026-09-16 23:59:00", 0,
         1, None, 2, "2026-09-14 15:00:00"),
        (18, 6, "代刷课时服务", "声称可代刷网课。",
         None, "其他", "线上",
         "2026-09-25 00:00:00", "2026-09-25 23:59:00", "2026-09-24 23:59:00", 0,
         2, "代刷课时违反校规，已驳回", 3, "2026-09-21 15:00:00"),
    ]
    cols = ["id", "user_id", "title", "description", "images", "category", "location",
            "start_time", "end_time", "signup_deadline", "max_members",
            "audit_status", "audit_reason", "status", "create_time"]
    insert("activity", cols, data)
    print(f"[seed] activity = {len(data)}")


# ---------------- 7. 活动报名 ----------------
def seed_activity_member():
    # 只对审核通过的活动报名；uk(activity_id,user_id)
    # (id, activity_id, user_id, remark, status, ct)
    data = [
        # 活动1 篮球赛 max=16
        (1,  1, 1, "张三+室友组队", 1, "2026-09-17 09:00:00"),
        (2,  1, 2, "数理学院代表",   1, "2026-09-17 10:00:00"),
        (3,  1, 3, "想报名围观",     0, "2026-09-18 09:00:00"),
        (4,  1, 4, "女生队",         2, "2026-09-18 10:00:00"),
        # 活动3 音乐节
        (5,  3, 1, "和同学一起",     1, "2026-09-17 11:00:00"),
        (6,  3, 6, "经管学院",       1, "2026-09-17 12:00:00"),
        # 活动4 编程马拉松
        (7,  4, 2, "想挑战一下",     1, "2026-09-18 09:30:00"),
        # 活动5 读书分享会(已结束)
        (8,  5, 1, "已报名",         1, "2026-09-16 09:00:00"),
        (9,  5, 2, "已报名",         1, "2026-09-16 10:00:00"),
        (10, 5, 3, "已报名",         1, "2026-09-16 11:00:00"),
        # 活动6 迎新晚会(已结束)
        (11, 6, 4, "节目组成员",     1, "2026-09-16 14:00:00"),
        (12, 6, 5, "嘉宾",           1, "2026-09-16 15:00:00"),
        (13, 6, 6, "工作人员",       1, "2026-09-16 16:00:00"),
        # 活动7 讲座
        (14, 7, 3, "想旁听",         1, "2026-09-19 09:00:00"),
        # 活动10 敬老院(已结束)
        (15, 10, 1, "志愿者",        1, "2026-09-13 10:00:00"),
        # 活动11 摄影社外拍(已结束)
        (16, 11, 2, "社员",          1, "2026-09-14 09:00:00"),
        # 活动15 羽毛球 max=6 已满
        (17, 15, 1, "单打",          1, "2026-09-19 09:00:00"),
        (18, 15, 2, "单打",          1, "2026-09-19 10:00:00"),
        (19, 15, 3, "双打",          1, "2026-09-19 11:00:00"),
        (20, 15, 4, "双打",          1, "2026-09-19 12:00:00"),
        (21, 15, 5, "双打",          1, "2026-09-19 13:00:00"),
        (22, 15, 6, "双打",          1, "2026-09-19 14:00:00"),
    ]
    cols = ["id", "activity_id", "user_id", "remark", "status", "create_time"]
    insert("activity_member", cols, data)
    print(f"[seed] activity_member = {len(data)}")


# ---------------- 8. 活动签到 ----------------
def seed_activity_signin():
    # 只对已通过报名且活动已结束的签到；uk(activity_id,user_id)
    # 已结束且 approved 活动：5,6,10,11,17
    data = [
        (1, 5,  1, "2026-09-18 18:55:00"),
        (2, 5,  2, "2026-09-18 18:56:00"),
        (3, 5,  3, "2026-09-18 18:57:00"),
        (4, 6,  4, "2026-09-19 19:25:00"),
        (5, 6,  5, "2026-09-19 19:26:00"),
        (6, 10, 1, "2026-09-15 08:55:00"),
    ]
    cols = ["id", "activity_id", "user_id", "sign_time"]
    insert("activity_signin", cols, data)
    print(f"[seed] activity_signin = {len(data)}")


# ---------------- 9. 失物招领 ----------------
def seed_lost_found():
    # (id, user_id, type, title, desc, images, location, happen, contact, audit, areason, status, ct)
    data = [
        (1,  1, 0, "丢失校园一卡通", "蓝色挂绳，卡号尾号8801，求捡到的同学联系我。",
         imgs_json("lost_01_card.jpg"), "图书馆3楼", "2026-09-20 16:00:00", "微信 zhangsan2021",
         1, None, 0, "2026-09-20 18:00:00"),
        (2,  2, 0, "丢失一串钥匙", "含哆啦A梦钥匙扣，共3把钥匙。",
         imgs_json("lost_02_keys.jpg"), "食堂二楼", "2026-09-20 12:30:00", "QQ 100200300",
         1, None, 0, "2026-09-20 13:00:00"),
        (3,  3, 0, "丢失黑色折叠伞", "上周在教学楼B座捡到？不对，是丢了，求归还。",
         imgs_json("lost_03_umbrella.jpg"), "教学楼B座", "2026-09-18 17:00:00", "电话 13800010003",
         1, None, 1, "2026-09-18 18:00:00"),
        (4,  4, 0, "丢失黑色钱包", "内有身份证和银行卡，急急急。",
         imgs_json("lost_04_wallet.jpg"), "操场看台", "2026-09-19 18:00:00", "微信 zhao666",
         1, None, 0, "2026-09-19 19:00:00"),
        (5,  5, 1, "捡到白色AirPods", "在图书馆自习室捡到，失主请联系。",
         imgs_json("lost_05_airpods.jpg"), "图书馆4楼", "2026-09-20 20:00:00", "QQ 200300400",
         1, None, 0, "2026-09-20 21:00:00"),
        (6,  6, 1, "捡到线性代数课本", "封面有笔记，书名《线性代数》同济版。",
         imgs_json("lost_06_textbook.jpg"), "教学楼A201", "2026-09-19 10:00:00", "电话 13800010006",
         1, None, 1, "2026-09-19 11:00:00"),
        # 无图 7-18
        (7,  1, 0, "丢失黑色水杯", "膳魔师保温杯，杯底有标签。",
         None, "体育馆", "2026-09-21 10:00:00", "微信 zhangsan2021",
         1, None, 0, "2026-09-21 11:00:00"),
        (8,  2, 0, "丢失U盘", "金士顿16G，红色。",
         None, "计算机楼机房", "2026-09-21 14:00:00", "QQ 100200300",
         1, None, 0, "2026-09-21 15:00:00"),
        (9,  3, 1, "捡到一支钢笔", "金色钢笔，笔帽有刻字。",
         None, "外国语学院走廊", "2026-09-21 09:00:00", "电话 13800010003",
         1, None, 0, "2026-09-21 10:00:00"),
        (10, 4, 1, "捡到一副眼镜", "黑框眼镜，放在食堂桌子上。",
         None, "食堂一楼", "2026-09-20 18:00:00", "微信 zhao666",
         1, None, 0, "2026-09-20 19:00:00"),
        (11, 5, 0, "丢失电动车充电器", "48V充电器，停在车棚。",
         None, "学生宿舍车棚", "2026-09-18 19:00:00", "QQ 200300400",
         1, None, 1, "2026-09-18 20:00:00"),
        (12, 6, 1, "捡到一个笔记本", "线圈本，内有课堂笔记。",
         None, "图书馆2楼", "2026-09-17 16:00:00", "电话 13800010006",
         1, None, 2, "2026-09-17 17:00:00"),
        (13, 1, 0, "丢失AirPods充电盒", "单独充电盒，不含耳机。",
         None, "教学楼C座", "2026-09-21 16:00:00", "微信 zhangsan2021",
         0, None, 0, "2026-09-21 17:00:00"),
        (14, 2, 1, "捡到一个充电宝", "黑色10000mAh。",
         None, "地铁站出口", "2026-09-21 18:00:00", "QQ 100200300",
         0, None, 0, "2026-09-21 19:00:00"),
        (15, 3, 0, "丢失身份证", "姓名王五，急急急。",
         None, "未知", "2026-09-20 20:00:00", "电话 13800010003",
         2, "包含个人隐私信息，请模糊处理后重新发布", 2, "2026-09-20 21:00:00"),
        (16, 4, 1, "有偿捡到手机", "声称捡到手机索要报酬。",
         None, "校门口", "2026-09-20 22:00:00", "微信 zhao666",
         2, "失物招领不得索要报酬", 2, "2026-09-20 23:00:00"),
        (17, 5, 0, "丢失黑色双肩包", "内有笔记本电脑。",
         None, "图书馆5楼", "2026-09-21 15:00:00", "QQ 200300400",
         1, None, 0, "2026-09-21 16:00:00"),
        (18, 6, 1, "捡到一串佛珠", "木质佛珠，失主请联系。",
         None, "体育馆更衣室", "2026-09-20 09:00:00", "电话 13800010006",
         1, None, 0, "2026-09-20 10:00:00"),
    ]
    cols = ["id", "user_id", "type", "title", "description", "images", "location",
            "happen_time", "contact", "audit_status", "audit_reason", "status", "create_time"]
    insert("lost_found", cols, data)
    print(f"[seed] lost_found = {len(data)}")


# ---------------- 10. 失物认领 ----------------
def seed_lost_found_claim():
    # 只对审核通过(1)的失物/招领认领
    data = [
        (1, 1, 2,  "这是我的校园卡，卡号尾号8801，我有照片为证。", "微信 lisi002", 0, "2026-09-21 09:00:00"),
        (2, 2, 3,  "钥匙扣是哆啦A梦的，我能描述细节。",             "QQ 100200300", 1, "2026-09-21 10:00:00"),
        (3, 4, 5,  "钱包里有我学生证，请联系我。",                   "QQ 200300400", 2, "2026-09-21 11:00:00"),
        (4, 5, 6,  "耳机是我的，壳上有划痕。",                       "电话 13800010006", 0, "2026-09-21 12:00:00"),
        (5, 7, 1,  "水杯是我的，杯底贴了姓名贴。",                   "微信 zhangsan2021", 1, "2026-09-21 13:00:00"),
        (6, 9, 4,  "钢笔是我丢的，刻字是W.L。",                       "微信 zhao666", 2, "2026-09-21 14:00:00"),
    ]
    cols = ["id", "lost_found_id", "claim_user_id", "message", "contact", "status", "create_time"]
    insert("lost_found_claim", cols, data)
    print(f"[seed] lost_found_claim = {len(data)}")


# ---------------- 11. 校园动态 ----------------
def seed_post():
    # (id, user_id, content, images, audit, areason, ct, like_count, comment_count)
    data = [
        (1,  1, "今天阳光真好，走在校园林荫道上感觉特别治愈。",
         imgs_json("post_01_campus_view.jpg"), 1, None, "2026-09-16 10:00:00", 5, 2),
        (2,  2, "今天食堂两荤一素，性价比真高。",
         imgs_json("post_02_canteen.jpg"),     1, None, "2026-09-16 12:30:00", 3, 1),
        (3,  3, "图书馆自习日，从早学到晚，效率拉满。",
         imgs_json("post_03_library.jpg", "post_07_lab.jpg"), 1, None, "2026-09-16 14:00:00", 8, 3),
        (4,  4, "操场晚霞太美了，随手拍一张。",
         imgs_json("post_04_sunset.jpg"),     1, None, "2026-09-16 18:30:00", 6, 2),
        (5,  5, "宿舍书桌整理完毕，绿植+电脑+台灯，学习氛围拉满。",
         imgs_json("post_05_dorm.jpg"),       1, None, "2026-09-17 09:00:00", 4, 1),
        (6,  6, "樱花大道又到了盛开的季节，虽然不是春天但也很美。",
         imgs_json("post_06_cherry.jpg", "post_01_campus_view.jpg"), 1, None, "2026-09-17 10:00:00", 7, 2),
        (7,  1, "实验室日常，一堆机器在跑实验。",
         imgs_json("post_07_lab.jpg"),        1, None, "2026-09-17 15:00:00", 2, 0),
        (8,  2, "毕业季快到了，开始怀念去年合影。",
         imgs_json("post_08_graduation.jpg"), 1, None, "2026-09-17 16:00:00", 9, 4),
        # 无图 9-20
        (9,  3, "今天英语角聊得很开心，认识了几个新朋友。",
         None, 1, None, "2026-09-18 10:00:00", 3, 1),
        (10, 4, "艺术展的作业终于交了，松一口气。",
         None, 1, None, "2026-09-18 14:00:00", 2, 0),
        (11, 5, "论文又被导师打回来了，继续改。",
         None, 1, None, "2026-09-18 16:00:00", 4, 2),
        (12, 6, "社团招新今天圆满结束，欢迎新成员！",
         None, 1, None, "2026-09-19 10:00:00", 5, 1),
        (13, 1, "今天跑了5公里，状态不错。",
         None, 1, None, "2026-09-19 18:00:00", 3, 0),
        (14, 2, "考研倒计时，每天6点起。",
         None, 1, None, "2026-09-20 07:00:00", 6, 2),
        (15, 3, "大一新生活适应中，课程有点多。",
         None, 1, None, "2026-09-20 12:00:00", 2, 0),
        (16, 4, "大四秋招开始投简历了，紧张。",
         None, 1, None, "2026-09-20 15:00:00", 4, 1),
        (17, 5, "这条动态待审核中。",
         None, 0, None, "2026-09-21 09:00:00", 0, 0),
        (18, 6, "这条也在审核中。",
         None, 0, None, "2026-09-21 10:00:00", 0, 0),
        (19, 1, "这条内容违规被驳回了。",
         None, 2, "内容含违规信息", "2026-09-21 11:00:00", 0, 0),
        (20, 2, "这条也被驳回。",
         None, 2, "含外部联系方式", "2026-09-21 14:00:00", 0, 0),
    ]
    rows = [(d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7], d[8]) for d in data]
    # 列顺序: id,user_id,content,images,audit_status,audit_reason,create_time,like_count,comment_count
    cols = ["id", "user_id", "content", "images", "audit_status", "audit_reason",
            "create_time", "like_count", "comment_count"]
    insert("post", cols, rows)
    print(f"[seed] post = {len(rows)}")


# ---------------- 12. 动态评论 ----------------
def seed_post_comment():
    # 只对审核通过(1)的动态(1-16)评论
    data = [
        (1,  1, 2,  "好美啊！这是哪里？",            "2026-09-16 11:00:00"),
        (2,  1, 3,  "求拍摄参数",                    "2026-09-16 11:30:00"),
        (3,  2, 3,  "食堂二楼？我也爱那家",          "2026-09-16 13:00:00"),
        (4,  3, 1,  "自习辛苦了！",                  "2026-09-16 15:00:00"),
        (5,  3, 4,  "图书馆几楼？",                  "2026-09-16 15:30:00"),
        (6,  3, 5,  "加油！",                        "2026-09-16 16:00:00"),
        (7,  4, 6,  "晚霞绝了",                      "2026-09-16 19:00:00"),
        (8,  4, 1,  "我也常去操场看日落",            "2026-09-16 19:30:00"),
        (9,  5, 2,  "书桌布置得真好",                "2026-09-17 10:00:00"),
        (10, 6, 3,  "樱花大道！春天必去",            "2026-09-17 11:00:00"),
        (11, 6, 4,  "美哭",                          "2026-09-17 11:30:00"),
        (12, 8, 5,  "毕业快乐！",                    "2026-09-17 17:00:00"),
        (13, 8, 6,  "怀念毕业照",                    "2026-09-17 17:30:00"),
        (14, 8, 1,  "时间真快",                      "2026-09-17 18:00:00"),
        (15, 8, 2,  "祝前程似锦",                    "2026-09-17 18:30:00"),
    ]
    cols = ["id", "post_id", "user_id", "content", "create_time"]
    insert("post_comment", cols, data)
    print(f"[seed] post_comment = {len(data)}")


# ---------------- 13. 动态点赞 ----------------
def seed_post_like():
    # 只对审核通过(1)的动态(1-16)点赞；uk(post_id,user_id)
    # 设计：每个动态被多个不同用户点赞，无重复
    likes = [
        # post 1: users 2,3,4,5,6
        (1, 1, 2), (2, 1, 3), (3, 1, 4), (4, 1, 5), (5, 1, 6),
        # post 2: users 1,3,4
        (6, 2, 1), (7, 2, 3), (8, 2, 4),
        # post 3: users 1,2,4,5,6,2? no dup. 1,2,4,5,6
        (9, 3, 1), (10, 3, 2), (11, 3, 4), (12, 3, 5), (13, 3, 6),
        # post 4: users 1,3,5,6
        (14, 4, 1), (15, 4, 3), (16, 4, 5), (17, 4, 6),
        # post 8: users 1,2,3,4,5,6 (6 likes)
        (18, 8, 1), (19, 8, 2), (20, 8, 3),
    ]
    rows = [(lid, pid, uid, "2026-09-17 12:00:00") for (lid, pid, uid) in likes]
    cols = ["id", "post_id", "user_id", "create_time"]
    insert("post_like", cols, rows)
    print(f"[seed] post_like = {len(rows)}")


# ---------------- 14. 学习搭子 ----------------
def seed_study_partner():
    # (id, user_id, subject, goal, schedule, intro, contact, audit, areason, status, ct)
    data = [
        (1,  1, "考研数学", "考上985学硕", "每天早7晚10图书馆",
         "计算机大三，数学基础还行，想找个一起刷题的搭子。", "微信 zhangsan2021",
         1, None, 0, "2026-09-16 10:00:00"),
        (2,  2, "英语口语", "雅思7.5", "每周一三五晚英语角",
         "数理学院大二，口语薄弱，想找语伴。", "QQ 100200300",
         1, None, 0, "2026-09-16 11:00:00"),
        (3,  3, "编程算法", "秋招进大厂", "周末一起刷LeetCode",
         "外院大一零基础学Python，求带。", "电话 13800010003",
         1, None, 1, "2026-09-17 09:00:00"),
        (4,  4, "运动健身", "减脂10斤", "每天操场5公里",
         "艺术学院大四，想找跑步搭子。", "微信 zhao666",
         1, None, 0, "2026-09-17 10:00:00"),
        (5,  5, "CPA备考", "过会计+税法", "图书馆3楼自习",
         "研一，CPA备考，找一起早起的搭子。", "QQ 200300400",
         1, None, 0, "2026-09-18 09:00:00"),
        (6,  6, "雅思托福", "托福100+", "每天背单词+听力",
         "经管大三，准备申请，找搭子互相督促。", "电话 13800010006",
         1, None, 0, "2026-09-18 10:00:00"),
        (7,  1, "考研数学", "已找到搭子", "每天图书馆",
         "已找到搭子，谢谢大家。", "微信 zhangsan2021",
         1, None, 2, "2026-09-18 14:00:00"),
        (8,  2, "英语口语", "英语角练口语", "每周二四晚",
         "想找native speaker最好。", "QQ 100200300",
         1, None, 0, "2026-09-19 09:00:00"),
        (9,  3, "编程算法", "学数据结构", "周末实验室",
         "想找一起学数据结构的同学。", "电话 13800010003",
         0, None, 0, "2026-09-21 09:00:00"),
        (10, 4, "CPA备考", "代报名CPA", "线上",
         "声称可代报名CPA。", "微信 zhao666",
         2, "违规代报名，已驳回", 2, "2026-09-21 10:00:00"),
        (11, 5, "雅思托福", "口语模考", "周末图书馆",
         "找搭子做口语模考对练。", "QQ 200300400",
         1, None, 0, "2026-09-19 15:00:00"),
        (12, 6, "运动健身", "羽毛球双打", "体育馆每周六",
         "已找到双打搭档。", "电话 13800010006",
         1, None, 1, "2026-09-20 10:00:00"),
    ]
    # 图片：study_01~04 给前4条
    images_map = {
        1: "study_01_kaoyan.jpg",
        2: "study_02_english.jpg",
        3: "study_03_coding.jpg",
        4: "study_04_sports.jpg",
    }
    rows = []
    for d in data:
        (sid, uid, subj, goal, sched, intro, contact, ast, areason, st, ct) = d
        img = imgs_json(images_map[sid]) if sid in images_map else None
        rows.append((sid, uid, subj, goal, sched, intro, contact, img, ast, areason, st, ct))
    cols = ["id", "user_id", "subject", "goal", "schedule", "intro", "contact",
            "images", "audit_status", "audit_reason", "status", "create_time"]
    insert("study_partner", cols, rows)
    print(f"[seed] study_partner = {len(rows)}")


# ---------------- 15. 校园问答 ----------------
def seed_campus_question():
    # (id, user_id, title, content, category, status, accepted_answer_id, view, ct)
    # 1,2,3 已解决且有采纳回答；4,5,6 有回答但未解决；7-12 无回答
    data = [
        (1,  1, "计算机学院机房怎么预约？", "听说有线上预约系统，找不到入口。",
         "学业课程", 1, None, 12, "2026-09-16 09:00:00"),
        (2,  2, "考研自习室占座规则是什么？", "图书馆自习室能不能放书占座？",
         "考研就业", 1, None, 25, "2026-09-16 10:00:00"),
        (3,  3, "校园网怎么连接？", "大一新生不知道怎么连校园网。",
         "校园生活", 1, None, 30, "2026-09-16 11:00:00"),
        (4,  4, "选修课怎么退？", "选了一门选修课想退，找不到地方。",
         "学业课程", 0, None, 8, "2026-09-17 09:00:00"),
        (5,  5, "秋招简历投递渠道", "学校有没有统一的招聘平台？",
         "考研就业", 0, None, 15, "2026-09-17 10:00:00"),
        (6,  6, "社团招新一般在什么时候？", "想加入辩论社。",
         "社团活动", 0, None, 20, "2026-09-17 11:00:00"),
        (7,  1, "食堂三楼怎么样？", "听说三楼新开了窗口。",
         "校园生活", 0, None, 10, "2026-09-18 09:00:00"),
        (8,  2, "图书馆借书期限多久？", "一次能借几本？",
         "校园生活", 0, None, 6, "2026-09-18 10:00:00"),
        (9,  3, "体测标准是什么？", "大一体测怕不及格。",
         "学业课程", 0, None, 18, "2026-09-19 09:00:00"),
        (10, 4, "校医院挂号时间", "发烧了想去校医院。",
         "校园生活", 0, None, 5, "2026-09-19 14:00:00"),
        (11, 5, "保研绩点怎么算？", "研一回顾保研规则。",
         "考研就业", 0, None, 9, "2026-09-20 09:00:00"),
        (12, 6, "其他问题", "想问下校园卡挂失怎么办？",
         "其他", 0, None, 4, "2026-09-20 10:00:00"),
    ]
    cols = ["id", "user_id", "title", "content", "category", "status",
            "accepted_answer_id", "view_count", "create_time"]
    insert("campus_question", cols, data)
    print(f"[seed] campus_question = {len(data)}")


# ---------------- 16. 校园问答回答 ----------------
def seed_campus_answer():
    # (id, question_id, user_id, content, is_accepted, ct)
    # Q1: 2 answers, id=1 accepted; Q2: 2 answers, id=3 accepted; Q3: 2 answers, id=5 accepted
    # Q4: 1; Q5: 1; Q6: 2
    data = [
        (1,  1, 2,  "在企业微信->服务->机房预约里。", 1, "2026-09-16 09:30:00"),
        (2,  1, 3,  "也可以直接去B座前台登记。",     0, "2026-09-16 10:00:00"),
        (3,  2, 4,  "不能占座，每天闭馆会清理。",     1, "2026-09-16 10:30:00"),
        (4,  2, 5,  "可以放书但不能超过1小时。",     0, "2026-09-16 11:00:00"),
        (5,  3, 6,  "连SSID=CAMPUS，学号登录即可。", 1, "2026-09-16 11:30:00"),
        (6,  3, 1,  "密码是身份证后6位。",           0, "2026-09-16 12:00:00"),
        (7,  4, 5,  "教务系统->选课->退课。",         0, "2026-09-17 09:30:00"),
        (8,  5, 6,  "学校就业网有统一招聘平台。",     0, "2026-09-17 10:30:00"),
        (9,  6, 1,  "一般九月开学季招新。",           0, "2026-09-17 11:30:00"),
        (10, 6, 2,  "辩论社在社联公众号有招新推送。", 0, "2026-09-17 12:00:00"),
    ]
    cols = ["id", "question_id", "user_id", "content", "is_accepted", "create_time"]
    insert("campus_answer", cols, data)

    # 回写问题的 accepted_answer_id
    q("UPDATE campus_question SET accepted_answer_id=1 WHERE id=1")
    q("UPDATE campus_question SET accepted_answer_id=3 WHERE id=2")
    q("UPDATE campus_question SET accepted_answer_id=5 WHERE id=3")
    print(f"[seed] campus_answer = {len(data)}")


# ---------------- 17. 公告 ----------------
def seed_notice():
    md1 = ("# 关于新学期校园网升级的通知\n\n"
           "各位同学：\n\n"
           "我校校园网将于本周末进行升级维护，期间网络将短暂中断。\n\n"
           "- **维护时间**：周六 02:00 - 06:00\n"
           "- **影响范围**：全校有线及无线网络\n\n"
           "请提前保存好工作，由此带来的不便敬请谅解。")
    md2 = ("# 校园秋季活动预告\n\n"
           "本月将举办以下精彩活动：\n\n"
           "1. 校园杯3v3篮球争霸赛（9月26日）\n"
           "2. 校园音乐节（9月28日）\n"
           "3. Code Jam 编程马拉松（10月1日）\n\n"
           "欢迎各位同学踊跃报名参加！")
    md3 = ("# 中秋节放假安排\n\n"
           "根据国务院通知，结合我校实际，中秋节放假安排如下：\n\n"
           "- **放假时间**：10月4日 - 10月6日\n"
           "- **补课安排**：10月7日（周三）正常上课\n\n"
           "请同学们提前安排好假期行程，注意安全。")
    md4 = ("# 平台功能更新说明\n\n"
           "本次更新内容：\n\n"
           "- 新增校园问答模块\n"
           "- 优化闲置物品搜索体验\n"
           "- 修复动态图片加载问题\n\n"
           "如遇问题请联系管理员。")
    md5 = ("# 国庆假期安全提醒\n\n"
           "国庆假期将至，提醒同学们：\n\n"
           "- 离校前关好门窗水电\n"
           "- 注意人身和财产安全\n"
           "- 外出游玩结伴而行\n\n"
           "祝大家度过一个愉快的假期！")
    data = [
        (1, 1, "关于校园网升级维护的通知", md1, 1, "2026-09-20 09:00:00", "2026-09-20 09:00:00"),
        (2, 1, "校园秋季活动预告",         md2, 1, "2026-09-20 14:00:00", "2026-09-20 14:00:00"),
        (3, 1, "中秋节放假安排",           md3, 1, "2026-09-21 09:00:00", "2026-09-21 09:00:00"),
        (4, 1, "平台功能更新说明",         md4, 1, "2026-09-21 15:00:00", "2026-09-21 15:00:00"),
        (5, 1, "国庆假期安全提醒",         md5, 1, "2026-09-22 09:00:00", "2026-09-22 09:00:00"),
    ]
    cols = ["id", "admin_id", "title", "content", "status", "publish_time", "create_time"]
    insert("notice", cols, data)
    print(f"[seed] notice = {len(data)}")


# ---------------- 18. 消息通知 ----------------
def seed_message():
    # (id, user_id, type, title, content, biz_type, biz_id, is_read, ct)
    data = [
        # system
        (1,  1, "system", "欢迎使用Ai-campus", "欢迎来到校园综合服务平台，完善个人资料吧。", "system", None, 1, "2026-09-16 09:00:00"),
        (2,  2, "system", "校园网升级通知", "本周末校园网升级维护，请提前知悉。",       "system", None, 1, "2026-09-20 09:00:00"),
        (3,  3, "system", "中秋放假安排", "10月4日-6日放假，请合理安排。",             "system", None, 0, "2026-09-21 09:00:00"),
        (4,  4, "system", "平台功能更新", "新增校园问答模块，欢迎体验。",               "system", None, 1, "2026-09-21 15:00:00"),
        (5,  5, "system", "国庆安全提醒", "假期注意人身财产安全。",                     "system", None, 0, "2026-09-22 09:00:00"),
        (6,  6, "system", "欢迎使用Ai-campus", "完善个人资料，体验更多功能。",       "system", None, 1, "2026-09-16 09:30:00"),
        # audit - 审核结果
        (7,  5, "audit", "闲置物品审核通过", "您发布的「台式显示器」已审核通过。",     "idle", 17, 1, "2026-09-19 17:00:00"),
        (8,  5, "audit", "闲置物品审核驳回", "您发布的「疑似违禁物品」被驳回。",       "idle", 23, 1, "2026-09-21 12:00:00"),
        (9,  6, "audit", "闲置物品审核驳回", "您发布的「外部引流商品」被驳回。",       "idle", 24, 0, "2026-09-21 15:30:00"),
        (10, 1, "audit", "活动审核驳回", "您发布的「校外旅游团购」被驳回。",           "activity", 13, 1, "2026-09-21 11:00:00"),
        (11, 6, "audit", "活动审核驳回", "您发布的「代刷课时服务」被驳回。",           "activity", 18, 0, "2026-09-21 16:00:00"),
        (12, 3, "audit", "失物招领审核驳回", "您发布的「丢失身份证」被驳回。",         "lostfound", 15, 1, "2026-09-20 22:00:00"),
        (13, 1, "audit", "动态审核驳回", "您发布的动态被驳回。",                       "post", 19, 1, "2026-09-21 11:30:00"),
        (14, 3, "audit", "学习搭子审核通过", "您发布的「编程算法」搭子信息待审核。",   "qa", None, 0, "2026-09-21 09:30:00"),
        # interact - 业务互动
        (15, 5, "interact", "收到新的预约", "李四预约了您的「机械键盘」。",            "idle", 5, 1, "2026-09-18 11:30:00"),
        (16, 2, "interact", "预约已完成", "您与李四的「高等数学教材」交易已完成。",    "idle", 2, 1, "2026-09-18 10:00:00"),
        (17, 3, "interact", "报名已通过", "您报名的「读书分享会」已通过。",            "activity", 5, 1, "2026-09-16 12:00:00"),
        (18, 6, "interact", "失物认领通过", "您发布的「一串钥匙」已被认领。",          "lostfound", 2, 1, "2026-09-21 10:30:00"),
        (19, 1, "interact", "回答被采纳", "您在「计算机学院机房怎么预约？」的回答被采纳。", "qa", 1, 0, "2026-09-16 10:00:00"),
        (20, 1, "interact", "收到新评论", "李四评论了您的动态「校园林荫道」。",        "post", 1, 1, "2026-09-16 11:30:00"),
    ]
    cols = ["id", "user_id", "type", "title", "content", "biz_type", "biz_id", "is_read", "create_time"]
    insert("message", cols, data)
    print(f"[seed] message = {len(data)}")


# ---------------- 19. 收藏 ----------------
def seed_favorite():
    # 只收藏审核通过的内容；uk(user_id,target_type,target_id)
    data = [
        (1, 1, "idle",      4,  "2026-09-17 10:00:00"),
        (2, 2, "idle",      6,  "2026-09-17 11:00:00"),
        (3, 3, "activity",   1,  "2026-09-17 09:00:00"),
        (4, 4, "activity",   3,  "2026-09-17 10:00:00"),
        (5, 5, "post",      1,  "2026-09-16 11:00:00"),
        (6, 6, "post",      4,  "2026-09-16 19:00:00"),
        (7, 1, "lostfound",  5,  "2026-09-20 21:30:00"),
        (8, 2, "lostfound",  1,  "2026-09-20 19:00:00"),
    ]
    cols = ["id", "user_id", "target_type", "target_id", "create_time"]
    insert("favorite", cols, data)
    print(f"[seed] favorite = {len(data)}")


# ---------------- 20. 举报 ----------------
def seed_report():
    # (id, reporter_id, target_type, target_id, reason_type, reason, status, handle_result, handler_id, handle_time, ct)
    data = [
        (1, 1, "idle",      23, "违规商品", "该物品疑似违禁，请求处理。",     1, "已下架并通知发布者", 1, "2026-09-21 12:00:00", "2026-09-21 11:30:00"),
        (2, 2, "post",      19, "内容违规", "动态含违规信息。",               1, "已驳回动态",         1, "2026-09-21 12:00:00", "2026-09-21 11:30:00"),
        (3, 3, "comment",   1,  "不当言论", "评论内容不当。",                 0, None, None, None,                  "2026-09-21 14:00:00"),
        (4, 4, "idle",      24, "外部引流", "商品描述含外部联系方式。",       1, "已下架并警告发布者", 1, "2026-09-21 16:00:00", "2026-09-21 15:30:00"),
    ]
    cols = ["id", "reporter_id", "target_type", "target_id", "reason_type", "reason",
            "status", "handle_result", "handler_id", "handle_time", "create_time"]
    insert("report", cols, data)
    print(f"[seed] report = {len(data)}")


# ---------------- 主流程 ----------------
def main():
    print("=" * 60)
    print("Ai-campus 演示数据初始化")
    print("=" * 60)
    try:
        cleanup()
        seed_users()
        seed_idle_item()
        seed_idle_appointment()
        seed_idle_review()
        seed_activity()
        seed_activity_member()
        seed_activity_signin()
        seed_lost_found()
        seed_lost_found_claim()
        seed_post()
        seed_post_comment()
        seed_post_like()
        seed_study_partner()
        seed_campus_question()
        seed_campus_answer()
        seed_notice()
        seed_message()
        seed_favorite()
        seed_report()
        conn.commit()
        print("=" * 60)
        print("[done] 全部数据已提交")
        print("=" * 60)
        print("各表插入行数：")
        total = 0
        for t, n in STATS.items():
            print(f"  {t:20s} : {n:4d}")
            total += n
        print(f"  {'TOTAL':20s} : {total:4d}")
    except Exception as e:
        conn.rollback()
        print(f"[error] {e}")
        import traceback; traceback.print_exc()
        sys.exit(1)
    finally:
        cur.close()
        conn.close()


if __name__ == "__main__":
    main()
