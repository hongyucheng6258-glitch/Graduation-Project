"""Live acceptance for Agent appointment confirmation with disposable fixtures."""

from concurrent.futures import ThreadPoolExecutor
import argparse
import json
import os
import subprocess
import uuid
from urllib.parse import urlsplit

import pymysql

from verify_live import ROOT, ask, call, cleanup_student, local_config, ok, token


def verify(base, cfg, db, browser):
    marker = "Appoint" + uuid.uuid4().hex[:8]
    users = []
    item_id = None
    with db.cursor() as cur:
        try:
            for index in range(3):
                cur.execute("INSERT INTO user(student_no,nickname) VALUES(%s,%s)",
                            (f"{marker}{index}", f"Appointment QA {index}"))
                users.append(cur.lastrowid)
            buyer, seller, other = users
            cur.execute("INSERT INTO idle_item(user_id,title,audit_status,status) VALUES(%s,%s,1,0)",
                        (seller, f"{marker} 键盘"))
            item_id = cur.lastrowid
            db.commit()
            bearer, seller_token, other_token = [token(cfg["JWT_SECRET"], uid) for uid in users]
            question = f"找标题含 {marker} 的闲置键盘"
            if browser:
                fixture = {"token": bearer, "question": question, "itemId": item_id, "title": f"{marker} 键盘"}
                subprocess.run(["node", str(ROOT / "tests/agent/verify_idle_appointment.cjs")],
                               env={**os.environ, "CAMPUS_IDLE_FIXTURE": json.dumps(fixture)}, timeout=120, check=True)
                cur.execute("SELECT buyer_id,seller_id,status FROM idle_appointment WHERE item_id=%s", (item_id,))
                assert cur.fetchall() == ((buyer, seller, 0),)
                return

            def prepare(owner_token):
                search = ask(base, owner_token, question, "search_idle_items")
                result = ask(base, owner_token, "我想预约第一个，给发布者留言：我有一本Java教材。先给我确认卡。",
                             "prepare_idle_appointment", session_id=search["sessionId"])
                card, = result["idleAppointments"]
                assert card["itemId"] == item_id and card["state"] == "PENDING"
                return card, result["sessionId"]

            card, session = prepare(bearer)
            contender, _ = prepare(other_token)
            confirm = f'/api/ai/agent/idle-appointments/{card["id"]}/confirm'
            cur.execute("SELECT COUNT(*) FROM idle_appointment WHERE item_id=%s", (item_id,))
            assert cur.fetchone()[0] == 0
            assert call(base, confirm, bearer=other_token)["code"] == 404
            cur.execute("UPDATE idle_item SET expect_item='新的交换条件' WHERE id=%s", (item_id,))
            db.commit()
            assert call(base, confirm, bearer=bearer)["code"] != 200, "Old card accepted a modified offer"
            history = ok(call(base, f"/api/ai/session/{session}/messages", bearer=bearer, method="GET"))
            assert any(attachment["id"] == card["id"] and attachment["state"] == "CHANGED"
                       for row in history["list"] for attachment in row.get("attachments", {}).get("idleAppointments", []))
            cur.execute("UPDATE idle_item SET expect_item=NULL WHERE id=%s", (item_id,))
            db.commit()
            with ThreadPoolExecutor(max_workers=2) as executor:
                receipts = list(executor.map(lambda _: ok(call(base, confirm, bearer=bearer)), range(2)))
            assert receipts[0] == receipts[1] and receipts[0]["state"] == "SUBMITTED"
            appointment_id = receipts[0]["appointmentId"]
            assert call(base, f'/api/ai/agent/idle-appointments/{contender["id"]}/confirm', bearer=other_token)["code"] != 200
            db.commit()
            cur.execute("SELECT id,buyer_id,seller_id,status FROM idle_appointment WHERE item_id=%s", (item_id,))
            assert cur.fetchall() == ((appointment_id, buyer, seller, 0),)
            cur.execute("SELECT COUNT(*) FROM message WHERE user_id=%s AND biz_id=%s", (seller, item_id))
            assert cur.fetchone()[0] == 1
            ok(call(base, f"/api/idle/appoint/{appointment_id}/handle", {"accept": True}, seller_token, method="PUT"))
            history = ok(call(base, f"/api/ai/session/{session}/messages", bearer=bearer, method="GET"))
            assert any(attachment["id"] == card["id"] and attachment["appointmentStatus"] == 1
                       for row in history["list"] for attachment in row.get("attachments", {}).get("idleAppointments", []))
            print("PASS real ordinal appointment, owner-only confirmation, one reservation and notification, seller acceptance and restored card", flush=True)
        finally:
            if item_id is not None:
                cur.execute("DELETE FROM idle_appointment WHERE item_id=%s", (item_id,))
                cur.execute("DELETE FROM idle_item WHERE id=%s", (item_id,))
            for uid in users:
                if browser or item_id is not None:
                    cur.execute("DELETE FROM agent_idle_appointment WHERE user_id=%s", (uid,))
                cur.execute("DELETE FROM message WHERE user_id=%s", (uid,))
                cleanup_student(cur, uid)
            db.commit()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--browser", action="store_true")
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    if urlsplit(args.base).hostname not in ("127.0.0.1", "localhost", "::1"):
        raise SystemExit("Loopback backends only")
    config = local_config()
    with pymysql.connect(host="127.0.0.1", port=int(config.get("MYSQL_PORT", 3306)),
                         user=config.get("MYSQL_USER", "root"), password=config["MYSQL_PASSWORD"],
                         database=config.get("MYSQL_DB", "ai_campus_platform"), charset="utf8mb4") as connection:
        verify(args.base.rstrip("/"), config, connection, args.browser)
