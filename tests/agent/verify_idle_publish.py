"""Opt-in live test for the idle draft and explicit confirmation workflow."""

from concurrent.futures import ThreadPoolExecutor
import argparse
import json
import os
import subprocess
import uuid

import pymysql

from verify_live import ROOT, ask, call, cleanup_student, local_config, ok, token


def verify(db, cfg, browser):
    base = "http://127.0.0.1:8080"
    marker = "Draft" + uuid.uuid4().hex[:8]
    users = []
    with db.cursor() as cur:
        try:
            for index in range(2):
                cur.execute("INSERT INTO user(student_no,nickname) VALUES(%s,%s)", (f"{marker}{index}", "Guide Draft Test"))
                users.append(cur.lastrowid)
            db.commit()
            owner, stranger = users
            bearer = token(cfg["JWT_SECRET"], owner)
            foreign = token(cfg["JWT_SECRET"], stranger)
            question = f"请帮我发布一件闲置，标题写 {marker} 键盘，分类是数码电子，描述是按键正常，希望换一本Java教材。请生成草稿给我确认。"
            if browser:
                fixture = {"token": bearer, "question": question, "title": f"{marker} 浏览器修改后的键盘"}
                subprocess.run(["node", str(ROOT / "tests/agent/verify_idle_publish.cjs")],
                               env={**os.environ, "CAMPUS_IDLE_FIXTURE": json.dumps(fixture)}, timeout=130, check=True)
                cur.execute("SELECT title,images FROM idle_item WHERE user_id=%s", (owner,))
                item, = cur.fetchall()
                assert item[0] == fixture["title"] and len(json.loads(item[1])) == 1
                return
            result = ask(base, bearer, question, "prepare_idle_publish")
            draft, = result["idleDrafts"]
            draft_id = draft["id"]
            assert draft["state"] == "DRAFT"
            cur.execute("SELECT COUNT(*) FROM idle_item WHERE user_id=%s", (owner,))
            assert cur.fetchone()[0] == 0
            path = f"/api/ai/agent/idle-drafts/{draft_id}"
            assert call(base, path, bearer=foreign, method="GET")["code"] == 404
            assert ok(call(base, path, bearer=bearer, method="GET"))["title"] == draft["title"]
            title = f"{marker} 修改后的键盘"
            form = {"title": title, "description": "所有按键测试正常", "category": "数码电子", "expectItem": "Java教材", "imageIds": []}
            confirm = path + "/confirm"
            assert call(base, confirm, form, foreign)["code"] == 404
            cur.execute("UPDATE agent_idle_draft SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=%s", (draft_id,))
            db.commit()
            assert ok(call(base, path, bearer=bearer, method="GET"))["state"] == "EXPIRED"
            assert call(base, confirm, form, bearer)["code"] != 200
            cur.execute("UPDATE agent_idle_draft SET expires_at=DATE_ADD(NOW(),INTERVAL 1 HOUR) WHERE id=%s", (draft_id,))
            cur.execute("INSERT INTO upload_resource(owner_user_id,resource_url,resource_type,content_type,file_size) VALUES(%s,%s,'image','image/png',1)",
                        (stranger, "data:image/png;base64,AA=="))
            foreign_image = cur.lastrowid
            db.commit()
            assert call(base, confirm, {**form, "imageIds": [foreign_image]}, bearer)["code"] != 200
            with ThreadPoolExecutor(max_workers=2) as executor:
                receipts = list(executor.map(lambda _: ok(call(base, confirm, form, bearer)), range(2)))
            assert receipts[0] == receipts[1]
            assert receipts[0]["state"] == "SUBMITTED"
            db.commit()
            cur.execute("SELECT id,title,audit_status FROM idle_item WHERE user_id=%s", (owner,))
            item, = cur.fetchall()
            assert item[:2] == (receipts[0]["itemId"], title)
            history = ok(call(base, f'/api/ai/session/{result["sessionId"]}/messages', bearer=bearer, method="GET"))
            assert any(card["id"] == draft_id and card["state"] == "SUBMITTED"
                       for row in history["list"] for card in row.get("attachments", {}).get("idleDrafts", []))
            print("PASS live draft, owner access, expiration, image ownership, concurrent confirmation, persisted receipt", flush=True)
        finally:
            for uid in users:
                cur.execute("DELETE FROM agent_idle_draft WHERE user_id=%s", (uid,))
                cur.execute("DELETE FROM upload_resource WHERE owner_user_id=%s", (uid,))
                cur.execute("DELETE FROM idle_item WHERE user_id=%s", (uid,))
                cleanup_student(cur, uid)
            db.commit()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--browser", action="store_true")
    args = parser.parse_args()
    config = local_config()
    with pymysql.connect(host="127.0.0.1", port=int(config.get("MYSQL_PORT", 3306)),
                         user=config.get("MYSQL_USER", "root"), password=config["MYSQL_PASSWORD"],
                         database=config.get("MYSQL_DB", "ai_campus_platform"), charset="utf8mb4") as connection:
        verify(connection, config, args.browser)
