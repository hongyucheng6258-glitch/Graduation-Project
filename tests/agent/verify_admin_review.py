"""Live acceptance for single-item moderation and exactly-once notification."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import os
import subprocess
import time
import uuid
from urllib.parse import urlsplit

import pymysql

from verify_live import ROOT, call, local_config, ok, token


def verify(base, db, cfg, browser, published_fixtures):
    path = "/api/admin/assistant"
    marker = "Review" + uuid.uuid4().hex[:8]
    admin_ids, item_ids = [], []
    student_id = None
    with db.cursor() as cur:
        try:
            for role in ("audit", "super"):
                cur.execute("INSERT INTO admin(username,password,role) VALUES(%s,'unused',%s)", (f"{marker}{role}", role))
                admin_ids.append(cur.lastrowid)
            cur.execute("INSERT INTO user(student_no,nickname) VALUES(%s,'审核验收学生')", (marker,))
            student_id = cur.lastrowid
            db.commit()
            student = token(cfg["JWT_SECRET"], student_id)
            for suffix in ("keyboard", "mouse"):
                if published_fixtures:
                    form = {"title": marker + suffix, "description": "成色九成新，校外领取方式待核对"}
                    created = ok(call(base, "/api/idle", form, student))
                    item_ids.append(created["id"])
                    assert created["auditStatus"] == 0, "Use an isolated stack with default content audit rules"
                else:
                    cur.execute("INSERT INTO idle_item(user_id,title,description,audit_status,status) VALUES(%s,%s,'成色九成新',0,0)",
                                (student_id, f"{marker}{suffix}"))
                    item_ids.append(cur.lastrowid)
            db.commit()
            first, second = [token(cfg["JWT_SECRET"], uid, "admin") for uid in admin_ids]
            item = item_ids[0]
            question = f"查找待审核闲置，标题关键词只用 {marker}keyboard。"
            if browser:
                fixture = {"token": first, "question": question, "itemId": item, "title": marker + "keyboard"}
                subprocess.run(["node", str(ROOT / "tests/agent/verify_admin_review.cjs")],
                               env={**os.environ, "CAMPUS_ADMIN_REVIEW_FIXTURE": json.dumps(fixture)}, timeout=110, check=True)
                cur.execute("SELECT audit_status FROM idle_item WHERE id=%s", (item,))
                assert cur.fetchone()[0] == 2
                cur.execute("SELECT COUNT(*) FROM message WHERE user_id=%s AND biz_type='idle' AND biz_id=%s", (student_id, item))
                assert cur.fetchone()[0] == 1
                return

            def session(bearer):
                answer = ok(call(base, path + "/ask", {"question": question}, bearer))
                assert answer["turn"]["records"][0]["id"] == str(item)
                return answer["sessionId"]

            a, b = session(first), session(second)
            prepare_path = path + "/reviews/prepare"

            def prepare(bearer, session_id, decision="PASS", reason=""):
                return ok(call(base, prepare_path, {"sessionId": session_id, "itemId": str(item),
                                                    "decision": decision, "reason": reason}, bearer))

            def confirm(card, bearer):
                return call(base, f'{path}/reviews/{card["id"]}/confirm', bearer=bearer)

            assert call(base, prepare_path, {"sessionId": a, "itemId": str(item), "decision": "PASS"}, student)["code"] == 403
            assert call(base, prepare_path, {"sessionId": a, "itemId": str(item_ids[1]), "decision": "PASS"}, first)["code"] == 404
            assert call(base, prepare_path, {"sessionId": a, "itemId": str(item), "decision": "PASS"}, second)["code"] == 404
            stale = prepare(first, a)
            cur.execute("UPDATE idle_item SET description='内容已变更，校外' WHERE id=%s", (item,))
            db.commit()
            assert confirm(stale, first)["code"] != 200
            previous_edit = prepare(first, a)
            if published_fixtures:
                cur.execute("SELECT audit_revision FROM idle_item WHERE id=%s", (item,))
                old_revision = cur.fetchone()[0]
                db.commit()
                form = {"title": marker + "keyboard", "description": "内容已变更，校外"}
                assert ok(call(base, f"/api/idle/{item}", form, student, method="PUT"))["auditStatus"] == 0
                cur.execute("SELECT audit_revision FROM idle_item WHERE id=%s", (item,))
                assert cur.fetchone()[0] != old_revision
            else:
                cur.execute("UPDATE idle_item SET audit_revision=%s WHERE id=%s", (str(uuid.uuid4()), item))
            db.commit()
            assert confirm(previous_edit, first)["code"] != 200, "Re-submission with the same content accepted a stale card"
            expired = prepare(first, a)
            cur.execute("UPDATE admin_assistant_review SET expires_at=DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id=%s", (expired["id"],))
            db.commit()
            assert confirm(expired, first)["code"] != 200
            waiting = prepare(first, a)
            cur.execute("UPDATE admin_assistant_review SET expires_at=DATE_ADD(NOW(6), INTERVAL 1 SECOND) WHERE id=%s", (waiting["id"],))
            db.commit()
            cur.execute("SELECT id FROM idle_item WHERE id=%s FOR UPDATE", (item,))
            with ThreadPoolExecutor(max_workers=1) as pool:
                request = pool.submit(confirm, waiting, first)
                time.sleep(1.3)
                db.commit()
                assert request.result()["code"] != 200
            accept, reject = prepare(first, a), prepare(second, b, "REJECT", "需补充交换说明")
            assert confirm(accept, second)["code"] == 404
            cur.execute("UPDATE admin SET status=1 WHERE id=%s", (admin_ids[0],))
            db.commit()
            assert confirm(accept, first)["code"] == 401
            cur.execute("UPDATE admin SET status=0 WHERE id=%s", (admin_ids[0],))
            db.commit()
            cur.execute("SELECT audit_status FROM idle_item WHERE id=%s", (item,))
            assert cur.fetchone()[0] == 0
            cur.execute("SELECT COUNT(*) FROM message WHERE user_id=%s", (student_id,))
            assert cur.fetchone()[0] == 0

            with ThreadPoolExecutor(max_workers=3) as pool:
                pending = [pool.submit(confirm, accept, first), pool.submit(confirm, accept, first), pool.submit(confirm, reject, second)]
                results = [job.result() for job in pending]
            if results[0]["code"] == 200:
                assert ok(results[0]) == ok(results[1]) and results[2]["code"] != 200
                winner, bearer, winning_session, expected = accept, first, a, 1
            else:
                assert results[1]["code"] != 200 and results[2]["code"] == 200
                winner, bearer, winning_session, expected = reject, second, b, 2
            receipt = ok(confirm(winner, bearer))
            assert receipt["state"] == "APPLIED" and receipt["auditStatus"] == expected
            restored = ok(call(base, f"{path}/sessions/{winning_session}/reviews", bearer=bearer, method="GET"))
            assert any(row == receipt for row in restored)
            db.commit()
            cur.execute("SELECT audit_status FROM idle_item WHERE id=%s", (item,))
            assert cur.fetchone()[0] == expected
            messages = ok(call(base, "/api/message/list", bearer=student, method="GET"))["list"]
            assert len([row for row in messages if row["bizType"] == "idle" and row["bizId"] == item]) == 1
            item = item_ids[1]
            question = f"查找待审核闲置，标题关键词只用 {marker}mouse。"
            second_session = session(first)
            assistant_card = prepare(first, second_session)
            with ThreadPoolExecutor(max_workers=2) as pool:
                assistant_response = pool.submit(confirm, assistant_card, first)
                manual_response = pool.submit(call, base, f"/api/admin/audit/idle/{item}/reject?pendingOnly=true",
                                              {"reason": "需要补充描述"}, second, method="PUT")
                assert sum(job.result()["code"] == 200 for job in (assistant_response, manual_response)) == 1
            messages = ok(call(base, "/api/message/list", bearer=student, method="GET"))["list"]
            assert len([row for row in messages if row["bizType"] == "idle" and row["bizId"] == item]) == 1
            db.commit()
            cur.execute("SELECT audit_status FROM idle_item WHERE id=%s", (item,))
            status_before_offline = cur.fetchone()[0]
            ok(call(base, f"/api/idle/{item}", bearer=student, method="DELETE"))
            db.commit()
            cur.execute("SELECT audit_status,status FROM idle_item WHERE id=%s", (item,))
            assert cur.fetchone() == (status_before_offline, 3)
            print("PASS pending review, changed and expired cards, ownership, revoked access, concurrency, receipt and real student notification", flush=True)
        finally:
            cur.execute("SHOW TABLES LIKE 'admin_assistant_review'")
            has_reviews = cur.fetchone() is not None
            for uid in admin_ids:
                if has_reviews:
                    cur.execute("DELETE FROM admin_assistant_review WHERE admin_id=%s", (uid,))
                cur.execute("DELETE FROM admin_assistant_session WHERE admin_id=%s", (uid,))
                cur.execute("DELETE FROM admin WHERE id=%s", (uid,))
            if student_id is not None:
                cur.execute("DELETE FROM message WHERE user_id=%s", (student_id,))
                cur.execute("DELETE FROM idle_item WHERE user_id=%s", (student_id,))
                cur.execute("DELETE FROM user WHERE id=%s", (student_id,))
            db.commit()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--browser", action="store_true")
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--published-fixtures", action="store_true")
    args = parser.parse_args()
    if urlsplit(args.base).hostname not in ("127.0.0.1", "localhost", "::1"):
        raise SystemExit("Loopback backends only")
    config = local_config()
    with pymysql.connect(host="127.0.0.1", port=int(config.get("MYSQL_PORT", 3306)),
                         user=config.get("MYSQL_USER", "root"), password=config["MYSQL_PASSWORD"],
                         database=config.get("MYSQL_DB", "ai_campus_platform"), charset="utf8mb4") as connection:
        verify(args.base.rstrip("/"), connection, config, args.browser, args.published_fixtures)
