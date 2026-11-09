"""Live idle guide acceptance using only temporary local MySQL fixtures."""

import argparse
import json
import os
import subprocess
import uuid
from urllib.parse import urlsplit

import pymysql

from verify_live import ROOT, ask, call, cleanup_student, local_config, ok, token


def verify(base, cfg, db, browser):
    suffix = uuid.uuid4().hex[:8]
    marker = "IdleGuide" + suffix
    users, items = [], []
    with db.cursor() as cur:
        try:
            for index in range(2):
                cur.execute("INSERT INTO user(student_no,nickname) VALUES(%s,%s)",
                            (f"IG{suffix}{index}", f"Guide Test {index}"))
                users.append(cur.lastrowid)
            buyer, seller = users
            for variant, audit, status, exchange in (
                    ("有线键盘", 1, 0, "编程书"),
                    ("无线键盘", 1, 0, "编程书"),
                    ("待审键盘", 0, 0, "编程书"),
                    ("已下架键盘", 1, 3, "编程书"),
                    ("已预约键盘", 1, 1, "编程书"),
                    ("不匹配键盘", 1, 0, "篮球"),
            ):
                cur.execute("""
                    INSERT INTO idle_item(user_id,title,description,category,expect_item,audit_status,status,view_count)
                    VALUES(%s,%s,%s,'数码',%s,%s,%s,0)
                    """, (seller, marker + variant, "键盘按键正常", exchange, audit, status))
                items.append(cur.lastrowid)
            db.commit()
            first, second = items[1], items[0]
            bearer = token(cfg["JWT_SECRET"], buyer)
            question = f"请搜索标题关键词为 {marker}、期望换物包含编程书的校园闲置。按查询结果的顺序给我。"
            if browser:
                fixture = {"token": bearer, "question": question, "itemId": first, "title": marker + "无线键盘"}
                subprocess.run(["node", str(ROOT / "tests/agent/verify_idle_guide.cjs")],
                               env={**os.environ, "CAMPUS_IDLE_FIXTURE": json.dumps(fixture)}, timeout=100, check=True)
                return
            result = ask(base, bearer, question, "search_idle_items")
            assert [card["itemId"] for card in result["idleItems"]] == [first, second], result["idleItems"]
            assert result["idleSearch"] is True
            blocked_chat = call(base, "/api/chat/conversations", {"targetUserId": seller,
                                "contextType": "idle", "contextId": items[2]}, bearer)
            assert blocked_chat["code"] == 404
            print("PASS real search excludes unaudited, off-shelf, reserved and wrong exchange items", flush=True)
            detail_code = "UpdatedSwitch" + suffix
            cur.execute("UPDATE idle_item SET description=%s WHERE id=%s", (detail_code, second))
            cur.executemany("INSERT INTO ai_message(session_id,role,content) VALUES(%s,%s,%s)",
                            [(result["sessionId"], role, "收到") for _ in range(7) for role in ("user", "assistant")])
            db.commit()
            detail = ask(base, bearer, "刚才闲置搜索结果的第二个是什么情况？请查实时详情并原样写出描述。",
                         "get_idle_detail", detail_code, result["sessionId"])
            assert [card["itemId"] for card in detail["idleItems"]] == [second]
            assert detail["idleSearch"] is False
            cur.execute("SELECT view_count FROM idle_item WHERE id=%s", (second,))
            assert cur.fetchone()[0] == 0
            history = ok(call(base, f'/api/ai/session/{result["sessionId"]}/messages?pageSize=30', bearer=bearer, method="GET"))
            assert any([card["itemId"] for card in row.get("attachments", {}).get("idleItems", [])]
                       == [first, second] for row in history["list"])
            print("PASS ordinal follow-up uses fresh detail and the original cards restore", flush=True)
            foreign = call(base, "/api/ai/agent/ask",
                           {"question": "请查看第二个", "sessionId": result["sessionId"]},
                           token(cfg["JWT_SECRET"], seller))
            assert foreign["code"] == 404
            print("PASS another account cannot reuse the search session", flush=True)
        finally:
            if len(users) == 2:
                cur.execute("SELECT id FROM chat_conversation WHERE user1_id=%s AND user2_id=%s", tuple(sorted(users)))
                for (conversation_id,) in cur.fetchall():
                    cur.execute("DELETE FROM chat_conversation_member WHERE conversation_id=%s", (conversation_id,))
                    cur.execute("DELETE FROM chat_conversation WHERE id=%s", (conversation_id,))
            for item_id in items:
                cur.execute("DELETE FROM idle_item WHERE id=%s", (item_id,))
            for uid in users:
                cleanup_student(cur, uid)
            db.commit()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--browser", action="store_true")
    args = parser.parse_args()
    if urlsplit(args.base).hostname not in ("127.0.0.1", "localhost", "::1"):
        raise SystemExit("Loopback backends only")
    config = local_config()
    db = pymysql.connect(host="127.0.0.1", port=int(config.get("MYSQL_PORT", 3306)),
                         user=config.get("MYSQL_USER", "root"), password=config["MYSQL_PASSWORD"],
                         database=config.get("MYSQL_DB", "ai_campus_platform"), charset="utf8mb4")
    with db:
        verify(args.base.rstrip("/"), config, db, args.browser)
