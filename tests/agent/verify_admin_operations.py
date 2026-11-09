"""Live acceptance for the remaining admin assistant operations."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import os
import subprocess
import time
from urllib.parse import urlsplit
from urllib.request import Request, urlopen
import uuid

import pymysql

from verify_live import ROOT, call, local_config, ok, pdf_bytes, token


def verify(base, cfg, db, rag, browser):
    route = "/api/admin/assistant"
    marker = "Ops" + uuid.uuid4().hex[:9]
    admins = []
    student_id = None
    rule_id = None
    with db.cursor() as cur:
        try:
            for role in ("super", "audit"):
                cur.execute("INSERT INTO admin(username,password,role) VALUES(%s,'unused',%s)", (marker + role, role))
                admins.append(cur.lastrowid)
            cur.execute("INSERT INTO user(student_no,nickname) VALUES(%s,'管理端验收学生')", (marker,))
            student_id = cur.lastrowid
            db.commit()
            manager, auditor = [token(cfg["JWT_SECRET"], uid, "admin") for uid in admins]
            student = token(cfg["JWT_SECRET"], student_id)
            if browser:
                cur.execute("INSERT INTO agent_run(user_id,prompt_version,model_name,tools,elapsed_ms,status) VALUES(%s,0,'qa',%s,42,'FAILED')",
                            (student_id, marker))
                db.commit()
                fixture = {"token": manager, "marker": marker}
                if rag:
                    import base64
                    fixture["pdf"] = base64.b64encode(pdf_bytes(marker)).decode()
                subprocess.run(["node", str(ROOT / "tests/agent/verify_admin_operations.cjs")],
                               env={**os.environ, "CAMPUS_OPS_FIXTURE": json.dumps(fixture)}, timeout=180, check=True)
                return

            notice = route + "/notice-drafts"
            draft = ok(call(base, notice, {"request": f"为图书馆撰写周五闭馆通知，标题包含 {marker}，暂未确定具体时间。"}, manager))
            assert draft["noticeId"] is None
            cur.execute("SELECT COUNT(*) FROM notice WHERE admin_id=%s", (admins[0],))
            assert cur.fetchone()[0] == 0
            draft_url = f'{notice}/{draft["id"]}'
            assert call(base, draft_url, bearer=auditor, method="GET")["code"] == 404
            form = {"title": marker + " 核对后的通知", "content": "图书馆周五闭馆，具体时段以馆方公布为准。"}
            with ThreadPoolExecutor(max_workers=2) as pool:
                receipts = list(pool.map(lambda _: ok(call(base, draft_url + "/confirm", form, manager)), range(2)))
            assert receipts[0] == receipts[1] and receipts[0]["noticeId"]
            assert call(base, draft_url + "/confirm", {**form, "title": marker + " 另一窗口"}, manager)["code"] != 200
            notice_id = receipts[0]["noticeId"]
            assert call(base, f"/api/notice/{notice_id}", bearer=student, method="GET")["code"] != 200
            assert ok(call(base, f"/api/admin/notice/{notice_id}", bearer=manager, method="GET"))["title"] == form["title"]
            ok(call(base, f"/api/admin/notice/{notice_id}/publish", bearer=manager, method="PUT"))
            assert ok(call(base, f"/api/notice/{notice_id}", bearer=student, method="GET"))["title"] == form["title"]
            print("PASS model notice draft, owner binding, duplicate save, student visibility and manual publication", flush=True)

            cur.execute("INSERT INTO agent_run(user_id,prompt_version,model_name,tools,elapsed_ms,status) VALUES(%s,0,'qa',%s,42,'FAILED')",
                        (student_id, marker + ',' + marker))
            db.commit()
            diagnostics = ok(call(base, route + "/diagnostics?days=1", bearer=auditor, method="GET"))
            assert diagnostics["failed"] >= 1
            assert next(row for row in diagnostics["tools"] if row["name"] == marker) == {"name": marker, "runs": 1, "failed": 1}
            print("PASS real diagnostics counts and same-run tool deduplication", flush=True)
            if not rag:
                return

            rule = route + "/rules"
            boundary = "Rule" + marker
            upload = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{marker}.pdf"\r\n'
                      'Content-Type: application/pdf\r\n\r\n').encode() + pdf_bytes(marker) + f'\r\n--{boundary}--\r\n'.encode()
            content_type = f"multipart/form-data; boundary={boundary}"
            assert call(base, rule, upload, auditor, content_type=content_type)["code"] == 403
            rule_id = ok(call(base, rule, upload, manager, content_type=content_type))["id"]
            for _ in range(80):
                docs = ok(call(base, rule, bearer=auditor, method="GET"))
                doc = next(item for item in docs if item["id"] == rule_id)
                if doc["state"] == "READY":
                    break
                assert doc["state"] != "FAILED", doc
                time.sleep(1)
            else:
                raise AssertionError("Internal document indexing timed out")
            assert call(base, f"/api/ai/knowledge/{rule_id}", bearer=student, method="GET")["code"] == 404
            result = ok(call(base, rule + "/ask", {"question": "What is the makerspace access identifier? Give the exact identifier."}, auditor))
            assert marker in result["answer"]
            source = next(citation for citation in result["citations"] if citation["documentId"] == rule_id)
            assert source["page"] == 2 and source["version"]
            file_path = f'{rule}/{rule_id}/file?version={source["version"]}'
            with urlopen(Request(base + file_path, headers={"Authorization": "Bearer " + auditor}), timeout=10) as response:
                assert response.headers["Content-Type"] == "application/pdf" and response.read(5) == b"%PDF-"
            student_search = ok(call(base, "/api/ai/agent/ask", {"question": "请查资料，makerspace access identifier 是什么？"}, student))
            assert marker not in student_search["answer"] and all(row["documentId"] != rule_id for row in student_search["citations"])
            ok(call(base, f"{rule}/{rule_id}", bearer=manager, method="DELETE"))
            assert call(base, file_path, bearer=auditor, method="GET")["code"] == 404
            assert all(doc["id"] != rule_id for doc in ok(call(base, rule, bearer=auditor, method="GET")))
            rule_id = None
            print("PASS internal PDF ingestion, vector citation, student isolation and withdrawal", flush=True)
        finally:
            if rule_id:
                call(base, f"{route}/rules/{rule_id}", bearer=manager, method="DELETE")
            if rag and admins:
                for doc in (call(base, route + "/rules", bearer=manager, method="GET").get("data") or []):
                    if marker in doc["fileName"]:
                        call(base, f'{route}/rules/{doc["id"]}', bearer=manager, method="DELETE")
            cur.execute("SHOW TABLES LIKE 'admin_assistant_notice_draft'")
            has_drafts = cur.fetchone() is not None
            for uid in admins:
                if has_drafts:
                    cur.execute("DELETE FROM admin_assistant_notice_draft WHERE admin_id=%s", (uid,))
                cur.execute("DELETE FROM notice WHERE admin_id=%s", (uid,))
                cur.execute("DELETE FROM admin WHERE id=%s", (uid,))
            if student_id:
                from verify_live import cleanup_student
                cleanup_student(cur, student_id)
            db.commit()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--rag", action="store_true")
    parser.add_argument("--browser", action="store_true")
    args = parser.parse_args()
    if urlsplit(args.base).hostname not in ("127.0.0.1", "localhost", "::1"):
        raise SystemExit("Loopback backends only")
    config = local_config()
    with pymysql.connect(host="127.0.0.1", port=int(config.get("MYSQL_PORT", 3306)),
                         user=config.get("MYSQL_USER", "root"), password=config["MYSQL_PASSWORD"],
                         database=config.get("MYSQL_DB", "ai_campus_platform"), charset="utf8mb4") as connection:
        verify(args.base.rstrip("/"), config, connection, args.rag, args.browser)
