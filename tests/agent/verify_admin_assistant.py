"""Verify the admin assistant against a loopback API, real MySQL and model."""

import argparse
import json
import os
import subprocess
import uuid
from urllib.parse import urlsplit

import pymysql

from verify_live import ROOT, call, local_config, ok, token


def verify(base, cfg, db, browser, evidence):
    route = "/api/admin/assistant"
    admins = []
    marker = uuid.uuid4().hex[:8]
    author = None
    with db.cursor() as cur:
        try:
            for role in ("audit", "super"):
                cur.execute("INSERT INTO admin(username,password,role) VALUES(%s,'unused',%s)",
                            (f"assistant-{role}-{marker}", role))
                admins.append(cur.lastrowid)
            db.commit()
            reader, other = [token(cfg["JWT_SECRET"], uid, "admin") for uid in admins]
            if evidence:
                risk_url = "/api/admin/audit/pending?type=idle&riskOnly=true&pageSize=1"
                earlier_risk_count = ok(call(base, risk_url, bearer=reader, method="GET"))["total"]
                cur.execute("INSERT INTO user(student_no,nickname) VALUES(%s,'助手验收学生')", (f"AA{marker}",))
                author = cur.lastrowid
                cur.execute("INSERT INTO idle_item(user_id,title,description,audit_status,status,ai_risk_level,ai_audit_reason) VALUES(%s,%s,'现场描述',0,0,2,'验收风险提示')",
                            (author, f"AuditProof{marker} 键盘"))
                idle_id = cur.lastrowid
                cur.execute("INSERT INTO report(reporter_id,target_type,target_id,reason_type,reason,status) VALUES(%s,'idle',%s,'other','验收举报理由',0)",
                            (author, idle_id))
                report_id = cur.lastrowid
                db.commit()
                idle_question = f"查待审核闲置，标题关键词只用 AuditProof{marker}，先看有风险提示的。"
                report_question = f"查看举报编号 {report_id} 的举报理由和被举报内容。"
                if browser:
                    fixture = {"token": reader, "idleId": idle_id, "reportId": report_id, "idleQuestion": idle_question,
                               "reportQuestion": report_question, "title": f"AuditProof{marker} 键盘"}
                    subprocess.run(["node", str(ROOT / "tests/agent/verify_admin_assistant.cjs")],
                                   env={**os.environ, "CAMPUS_ADMIN_ASSISTANT_FIXTURE": json.dumps(fixture)}, timeout=120, check=True)
                    return
                first = ok(call(base, route + "/ask", {"question": idle_question}, reader))
                assert first["turn"]["tool"] == "list_idle_audits"
                idle, = first["turn"]["records"]
                assert idle["id"] == str(idle_id) and idle["riskReason"] == "验收风险提示"
                session = first["sessionId"]
                next_turn = ok(call(base, route + "/ask", {"question": report_question, "sessionId": session}, reader))
                assert next_turn["turn"]["tool"] == "get_report_evidence"
                report, = next_turn["turn"]["records"]
                assert report["id"] == str(report_id) and report["reason"] == "验收举报理由" and report["target"]["title"] == idle["title"]
                absent = ok(call(base, route + "/ask", {"question": f"查看举报编号 {report_id + 1000000}", "sessionId": session}, reader))
                assert absent["turn"]["records"] == []
                db.commit()
                cur.execute("SELECT body FROM admin_assistant_turn WHERE session_id=%s ORDER BY id DESC LIMIT 1", (session,))
                assert json.loads(cur.fetchone()[0])["refs"] == []
                cur.execute("SELECT audit_status FROM idle_item WHERE id=%s", (idle_id,))
                assert cur.fetchone()[0] == 0
                cur.execute("SELECT status FROM report WHERE id=%s", (report_id,))
                assert cur.fetchone()[0] == 0
                risk_page = ok(call(base, risk_url + f"&pageNum={earlier_risk_count + 1}", bearer=reader, method="GET"))
                assert risk_page["total"] == earlier_risk_count + 1 and risk_page["list"][0]["id"] == idle_id
                cur.execute("UPDATE idle_item SET audit_status=1 WHERE id=%s", (idle_id,))
                cur.execute("UPDATE report SET status=1 WHERE id=%s", (report_id,))
                db.commit()
                restored = ok(call(base, f"{route}/sessions/{session}", bearer=reader, method="GET"))
                assert [turn["records"][0]["status"] for turn in restored if turn["records"]] == [1, 1]
                print("PASS live idle audit evidence, report source, read-only state and live history hydration", flush=True)
                return
            student = token(cfg["JWT_SECRET"], admins[0])
            counts = ok(call(base, "/api/admin/stats/pending-counts", bearer=reader, method="GET"))
            question = "目前有多少待处理举报？请查询实时待办。"
            if browser:
                fixture = {"token": reader, "question": question, "count": counts["report"]}
                subprocess.run(["node", str(ROOT / "tests/agent/verify_admin_assistant.cjs")],
                               env={**os.environ, "CAMPUS_ADMIN_ASSISTANT_FIXTURE": json.dumps(fixture)}, timeout=100, check=True)
                return
            assert call(base, route + "/sessions", bearer=student, method="GET")["code"] == 403
            result = ok(call(base, route + "/ask", {"question": question}, reader))
            assert result["turn"]["tool"] == "get_pending_tasks"
            assert [(task["key"], task["count"]) for task in result["turn"]["tasks"]] == [("report", counts["report"])]
            session = result["sessionId"]
            assert ok(call(base, route + "/sessions", bearer=other, method="GET")) == []
            assert call(base, f"{route}/sessions/{session}", bearer=other, method="GET")["code"] == 404
            assert call(base, route + "/ask", {"question": question, "sessionId": session}, other)["code"] == 404
            second = ok(call(base, route + "/ask", {"question": "待审核闲置有多少？", "sessionId": session}, reader))
            assert second["sessionId"] == session
            assert [(task["key"], task["count"]) for task in second["turn"]["tasks"]] == [("idle", counts["idle"])]
            restored = ok(call(base, f"{route}/sessions/{session}", bearer=reader, method="GET"))
            assert [turn["tasks"][0]["key"] for turn in restored] == ["report", "idle"]
            assert ok(call(base, "/api/admin/stats/pending-counts", bearer=reader, method="GET")) == counts
            print("PASS live admin tool, real dashboard counts, history, account isolation and student rejection", flush=True)
        finally:
            if author is not None:
                cur.execute("DELETE FROM report WHERE reporter_id=%s", (author,))
                cur.execute("DELETE FROM idle_item WHERE user_id=%s", (author,))
                cur.execute("DELETE FROM user WHERE id=%s", (author,))
            cur.execute("SHOW TABLES LIKE 'admin_assistant_session'")
            has_sessions = cur.fetchone() is not None
            for uid in admins:
                if has_sessions:
                    cur.execute("DELETE FROM admin_assistant_session WHERE admin_id=%s", (uid,))
                cur.execute("DELETE FROM admin WHERE id=%s", (uid,))
            db.commit()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--browser", action="store_true")
    parser.add_argument("--evidence", action="store_true")
    args = parser.parse_args()
    if urlsplit(args.base).hostname not in ("127.0.0.1", "localhost", "::1"):
        raise SystemExit("Loopback backends only")
    config = local_config()
    with pymysql.connect(host="127.0.0.1", port=int(config.get("MYSQL_PORT", 3306)),
                         user=config.get("MYSQL_USER", "root"), password=config["MYSQL_PASSWORD"],
                         database=config.get("MYSQL_DB", "ai_campus_platform"), charset="utf8mb4") as connection:
        verify(args.base.rstrip("/"), config, connection, args.browser, args.evidence)
