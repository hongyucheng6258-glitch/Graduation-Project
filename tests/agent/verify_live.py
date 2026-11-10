"""Opt-in Agent smoke test against a loopback backend, real MySQL and a configured model."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import base64
import hashlib
import hmac
import json
import os
import subprocess
from datetime import datetime, timedelta
from pathlib import Path
import time
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen
import uuid

import pymysql


ROOT = Path(__file__).resolve().parents[2]


def local_config():
    values = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"').strip("'")
    return {**values, **os.environ}


def token(secret, uid, role="student"):
    def encode(data):
        return base64.urlsafe_b64encode(data).rstrip(b"=")

    now = int(time.time())
    claims = {"uid": uid, "role": role, "iat": now, "exp": now + 600, "iat_ms": now * 1000}
    body = encode(b'{"alg":"HS256","typ":"JWT"}') + b"." + encode(json.dumps(claims).encode())
    return (body + b"." + encode(hmac.digest(secret.encode(), body, hashlib.sha256))).decode()


def call(base, path, body=None, bearer=None, method="POST", content_type="application/json"):
    headers = {"Content-Type": "application/json"}
    if bearer:
        headers["Authorization"] = "Bearer " + bearer
    headers["Content-Type"] = content_type
    payload = body if isinstance(body, bytes) else None if body is None else json.dumps(body).encode()
    req = Request(base + path, payload, headers, method=method)
    try:
        with urlopen(req, timeout=40) as res:
            return json.load(res)
    except HTTPError as exc:
        if exc.code in (401, 403):
            return {"code": exc.code}
        raise


def ask(base, bearer, question, expected_tool, expected_text=None, session_id=None):
    response = call(base, "/api/ai/agent/ask", {"question": question, "sessionId": session_id}, bearer)
    assert response["code"] == 200, (response["code"], response.get("message"))
    data = response["data"]
    assert expected_tool in data["toolsUsed"], (expected_tool, data["toolsUsed"])
    if expected_text:
        assert expected_text in data["answer"], "Answer did not use the expected database value"
    print("PASS live tool", expected_tool, flush=True)
    return data


def verify_read_only(base, cfg, db):
    suffix = uuid.uuid4().hex[:10]
    title = "AgentFixture" + suffix
    location = "Lab" + suffix
    users, activities = [], []
    with db.cursor() as cur:
        try:
            for index in range(2):
                cur.execute("INSERT INTO user(student_no,nickname) VALUES(%s,%s)",
                            (f"AG{suffix}{index}", f"Agent Test {index}"))
                users.append(cur.lastrowid)
            start = datetime.now() + timedelta(days=1)
            cur.execute(
                "INSERT INTO activity(user_id,title,location,start_time,end_time,audit_status,max_members) "
                "VALUES(%s,%s,%s,%s,%s,1,5)",
                (users[1], title, location, start, start + timedelta(hours=1)),
            )
            activity = cur.lastrowid
            activities.append(activity)
            cur.execute("INSERT INTO activity_member(activity_id,user_id,status) VALUES(%s,%s,1)",
                        (activity, users[0]))
            db.commit()

            bearer_a, bearer_b = [token(cfg["JWT_SECRET"], uid) for uid in users]
            assert call(base, "/api/ai/agent/ask", {"question": "我报了什么？"})["code"] == 401
            print("PASS anonymous request rejected", flush=True)
            ask(base, bearer_a, "我报名了哪些活动？请保留活动的原始标题。", "get_my_signups", title)
            other = ask(base, bearer_b, "请查询我的报名记录。", "get_my_signups")
            assert title not in other["answer"], "Another account's signup appeared in the reply"
            ask(base, bearer_a, f"请在校园活动中查找标题含 {title} 的活动，并保留原始标题。",
                "search_activities", title)
            ask(base, bearer_a, f"请查活动 ID {activity} 的详情，给出活动地点原文。",
                "get_activity_detail", location)
        finally:
            for activity in activities:
                cur.execute("DELETE FROM activity_member WHERE activity_id=%s", (activity,))
                cur.execute("DELETE FROM activity WHERE id=%s", (activity,))
            for uid in users:
                cleanup_student(cur, uid)
            db.commit()


def pdf_bytes(code):
    pages = [
        ["General campus information about classrooms and sports grounds."] * 145,
        ["Makerspace handbook.", f"The makerspace access identifier is {code}.",
         "Keep this identifier available at the entrance."],
    ]
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Count 2 /Kids [3 0 R 4 0 R] >>",
    ]
    for contents in (6, 7):
        objects.append(
            f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
            f"/Resources << /Font << /F1 5 0 R >> >> /Contents {contents} 0 R >>".encode()
        )
    objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    for page in pages:
        stream = ("BT /F1 6 Tf 5 TL 10 815 Td\n" + "\n".join(f"({line}) Tj T*" for line in page) + "\nET").encode()
        objects.append(f"<< /Length {len(stream)} >>\nstream\n".encode() + stream + b"\nendstream")
    pdf = bytearray(b"%PDF-1.4\n")
    offsets = [0]
    for number, obj in enumerate(objects, 1):
        offsets.append(len(pdf))
        pdf.extend(f"{number} 0 obj\n".encode() + obj + b"\nendobj\n")
    xref = len(pdf)
    pdf.extend(f"xref\n0 {len(offsets)}\n0000000000 65535 f \n".encode())
    for offset in offsets[1:]:
        pdf.extend(f"{offset:010d} 00000 n \n".encode())
    pdf.extend(f"trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode())
    return bytes(pdf)


def ok(response):
    assert response["code"] == 200, (response["code"], response.get("message"))
    return response["data"]


def cleanup_student(cur, uid):
    cur.execute("DELETE a FROM agent_message_attachment a JOIN ai_message m ON a.message_id=m.id "
                "JOIN ai_session s ON m.session_id=s.id WHERE s.user_id=%s", (uid,))
    cur.execute("DELETE m FROM ai_message m JOIN ai_session s ON m.session_id=s.id WHERE s.user_id=%s", (uid,))
    cur.execute("DELETE FROM ai_session WHERE user_id=%s", (uid,))
    cur.execute("DELETE FROM agent_run WHERE user_id=%s", (uid,))
    cur.execute("DELETE FROM user WHERE id=%s", (uid,))


def verify_rag(base, cfg, db):
    suffix = uuid.uuid4().hex[:10]
    expected = "Makerspace-" + suffix
    users = []
    pdf_id, doc_id = None, None
    with db.cursor() as cur:
        try:
            for index in range(2):
                cur.execute("INSERT INTO user(student_no,nickname) VALUES(%s,%s)",
                            (f"AG{suffix}{index}", f"Agent RAG {index}"))
                users.append(cur.lastrowid)
            db.commit()
            owner, visitor = [token(cfg["JWT_SECRET"], uid) for uid in users]
            boundary = "CampusRag" + suffix
            data = (
                f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="rag-fixture.pdf"\r\n'
                "Content-Type: application/pdf\r\n\r\n"
            ).encode() + pdf_bytes(expected) + f"\r\n--{boundary}--\r\n".encode()
            pdf_id = ok(call(base, "/api/pdf/upload", data, owner, content_type=f"multipart/form-data; boundary={boundary}"))["docId"]
            doc_id = ok(call(base, f"/api/ai/knowledge/pdf/{pdf_id}/index", bearer=owner))["id"]
            route = f"/api/ai/knowledge/{doc_id}"
            question = {"question": "What is the makerspace access identifier? Give the exact identifier."}
            assert call(base, route + "/ask", question, visitor)["code"] == 404
            assert call(base, f"/api/ai/knowledge/pdf/{pdf_id}/index", bearer=visitor)["code"] == 404
            print("PASS cross-account index and retrieval rejected", flush=True)
            for _ in range(90):
                status = ok(call(base, route, bearer=owner, method="GET"))
                if status["state"] == "READY":
                    break
                assert status["state"] != "FAILED", status.get("error")
                time.sleep(1)
            else:
                raise AssertionError("Real indexing did not reach READY")
            cur.execute("SELECT LOCATE(%s,text_content) FROM pdf_document WHERE id=%s", (expected, pdf_id))
            assert cur.fetchone()[0] > 8000
            answer = ok(call(base, route + "/ask", question, owner))
            assert expected in answer["answer"]
            assert any(source["page"] == 2 and source["documentId"] == doc_id for source in answer["citations"])
            download = Request(base + route + "/file", headers={"Authorization": "Bearer " + owner})
            with urlopen(download, timeout=10) as file:
                assert file.headers.get_content_type() == "application/pdf" and file.read(5) == b"%PDF-"
            print("PASS real PDF answer beyond old cutoff with page 2 citation", flush=True)
            missing = ok(call(base, route + "/ask", {"question": "What is the annual cafeteria profit in euros?"}, owner))
            assert missing["citations"] == [] and "未查到" in missing["answer"]
            print("PASS unsupported question returned no citation", flush=True)
            hidden = ask(base, visitor, "请在知识库查 makerspace access identifier，有资料就给出原文。",
                         "search_knowledge")
            assert expected not in hidden["answer"] and not any(c["documentId"] == doc_id for c in hidden["citations"])
            print("PASS private PDF cannot appear in another user's Agent search", flush=True)
            cur.execute("SELECT active_version FROM kb_document WHERE id=%s", (doc_id,))
            prior = cur.fetchone()[0]
            db.commit()
            replacement = ok(call(base, f"/api/ai/knowledge/pdf/{pdf_id}/index?rebuild=true", bearer=owner))
            assert replacement["id"] == doc_id
            for _ in range(30):
                latest = ok(call(base, route, bearer=owner, method="GET"))
                if latest["state"] == "READY":
                    break
                assert latest["state"] != "FAILED"
                time.sleep(1)
            else:
                raise AssertionError("Reindexing did not finish")
            cur.execute("SELECT active_version FROM kb_document WHERE id=%s", (doc_id,))
            assert cur.fetchone()[0] != prior
            db.commit()
            print("PASS reindexing publishes a new version of the same document", flush=True)
            ok(call(base, route, bearer=owner, method="DELETE"))
            assert call(base, route + "/ask", question, owner)["code"] == 404
            print("PASS removed document is inaccessible", flush=True)
            doc_id = None
        finally:
            if doc_id is not None:
                call(base, f"/api/ai/knowledge/{doc_id}", bearer=owner, method="DELETE")
            if pdf_id is not None:
                cur.execute("DELETE FROM pdf_document WHERE id=%s", (pdf_id,))
            for uid in users:
                cleanup_student(cur, uid)
            db.commit()


def verify_combined(base, cfg, db):
    suffix = uuid.uuid4().hex[:10]
    title, code, location = "AgentFixture" + suffix, "Makerspace-" + suffix, "Lab" + suffix
    student = visitor = admin = doc = pdf = activity = draft = None
    current_prompt = 0
    root = "/api/admin/ai/agent"
    prompt = root + "/prompts/campus_agent"
    with db.cursor() as cur:
        try:
            cur.execute("SELECT version_id FROM agent_prompt_active WHERE scene='campus_agent'")
            row = cur.fetchone()
            current_prompt = row[0] if row else 0
            cur.execute("INSERT INTO user(student_no,nickname) VALUES(%s,'Agent Combo')", ("AG" + suffix,))
            student = cur.lastrowid
            cur.execute("INSERT INTO user(student_no,nickname) VALUES(%s,'Agent Other')", ("AV" + suffix,))
            visitor = cur.lastrowid
            cur.execute("INSERT INTO admin(username,password,role) VALUES(%s,'unused','super')", ("agent-" + suffix,))
            admin = cur.lastrowid
            start = datetime.now() + timedelta(days=1)
            cur.execute("""
                INSERT INTO activity(user_id,title,location,start_time,end_time,audit_status)
                VALUES(%s,%s,%s,%s,%s,1)
                """, (visitor, title, location, start, start + timedelta(hours=1)))
            activity = cur.lastrowid
            db.commit()
            bearer = token(cfg["JWT_SECRET"], student)
            admin_bearer = token(cfg["JWT_SECRET"], admin, "admin")
            boundary = "AgentPublic" + suffix
            data = (
                f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="campus-handbook.pdf"\r\n'
                "Content-Type: application/pdf\r\n\r\n"
            ).encode() + pdf_bytes(code) + f"\r\n--{boundary}--\r\n".encode()
            status = ok(call(base, root + "/knowledge", data, admin_bearer,
                             content_type=f"multipart/form-data; boundary={boundary}"))
            doc, pdf = status["id"], status["pdfId"]
            for _ in range(60):
                docs = ok(call(base, root + "/knowledge", bearer=admin_bearer, method="GET"))
                state = next(d["state"] for d in docs if d["id"] == doc)
                if state == "READY":
                    break
                assert state != "FAILED"
                time.sleep(1)
            else:
                raise AssertionError("Public document never reached READY")
            draft = ok(call(base, prompt, {"content": "验收版本。请使用简洁中文并保留原始活动标题和编号。"}, admin_bearer))
            versions = ok(call(base, prompt, bearer=admin_bearer, method="GET"))
            assert not next(v["active"] for v in versions if v["id"] == draft)
            ok(call(base, f"{prompt}/{draft}", bearer=admin_bearer, method="PUT"))
            result = ask(base, bearer,
                         f"查公共手册中的 makerspace access identifier 原文，再搜索活动 {title}，请分别回答。",
                         "search_knowledge", code)
            assert "search_activities" in result["toolsUsed"] and title in result["answer"]
            assert any(c["documentId"] == doc and c["page"] == 2 for c in result["citations"])
            assert result["promptVersion"] == draft
            print("PASS combined live SQL and vector retrieval with public citation and active prompt version", flush=True)
            session = result["sessionId"]
            updated_location = "UpdatedLab" + suffix
            cur.execute("UPDATE activity SET location=%s WHERE id=%s", (updated_location, activity))
            db.commit()
            ask(base, bearer, "请重新查询刚才找到的那场活动，最新地点是什么？请原样回复地点。",
                "get_activity_detail", updated_location, session)
            foreign = call(base, "/api/ai/agent/ask", {"question": "继续", "sessionId": session},
                           token(cfg["JWT_SECRET"], visitor))
            assert foreign["code"] == 404
            print("PASS follow-up with persisted activity ID and private session", flush=True)
            ok(call(base, f"{prompt}/{current_prompt}", bearer=admin_bearer, method="PUT"))
            versions = ok(call(base, prompt, bearer=admin_bearer, method="GET"))
            assert not next(v["active"] for v in versions if v["id"] == draft)
            print("PASS prompt version rollback", flush=True)
        finally:
            if draft:
                call(base, f"{prompt}/{current_prompt}", bearer=admin_bearer, method="PUT")
                cur.execute("DELETE FROM agent_prompt_version WHERE id=%s", (draft,))
            if doc:
                call(base, f"{root}/knowledge/{doc}", bearer=admin_bearer, method="DELETE")
            if pdf:
                cur.execute("DELETE FROM pdf_document WHERE id=%s", (pdf,))
            if activity:
                cur.execute("DELETE FROM activity WHERE id=%s", (activity,))
            if lost:
                cur.execute("DELETE FROM lost_found_claim WHERE lost_found_id=%s", (lost,))
                cur.execute("DELETE FROM message WHERE biz_type='lostfound' AND biz_id=%s", (lost,))
                cur.execute("DELETE FROM lost_found WHERE id=%s", (lost,))
            if partner:
                for table in ("chat_message", "chat_conversation_member"):
                    cur.execute(f"DELETE m FROM {table} m JOIN chat_conversation c ON c.id=m.conversation_id "
                                "WHERE c.user1_id=%s OR c.user2_id=%s", (student, student))
                cur.execute("DELETE FROM chat_conversation WHERE user1_id=%s OR user2_id=%s", (student, student))
                cur.execute("DELETE FROM study_partner WHERE id=%s", (partner,))
            if post:
                cur.execute("DELETE FROM post_like WHERE post_id=%s", (post,))
                cur.execute("DELETE FROM post_comment WHERE post_id=%s", (post,))
                cur.execute("DELETE FROM favorite WHERE target_type='post' AND target_id=%s", (post,))
                cur.execute("DELETE FROM message WHERE biz_type='post' AND (user_id=%s OR user_id=%s)", (student, visitor))
                cur.execute("DELETE FROM post WHERE user_id=%s OR user_id=%s", (student, visitor))
            if question:
                cur.execute("DELETE FROM campus_answer WHERE question_id=%s", (question,))
                cur.execute("DELETE FROM campus_question WHERE id=%s", (question,))
            if notice:
                cur.execute("DELETE FROM notice WHERE id=%s", (notice,))
            for uid in (student, visitor, spare):
                if uid:
                    cleanup_student(cur, uid)
            if admin:
                cur.execute("DELETE FROM admin WHERE id=%s", (admin,))
            db.commit()


def verify_actions(base, cfg, db):
    suffix = uuid.uuid4().hex[:10]
    title = "SignupFixture" + suffix
    student = other = activity = None
    with db.cursor() as cur:
        try:
            cur.execute("INSERT INTO user(student_no,nickname) VALUES(%s,'Signup Actor')", ("SA" + suffix,))
            student = cur.lastrowid
            cur.execute("INSERT INTO user(student_no,nickname) VALUES(%s,'Signup Owner')", ("SO" + suffix,))
            other = cur.lastrowid
            start = datetime.now() + timedelta(days=1)
            cur.execute("""
                INSERT INTO activity(user_id,title,location,start_time,end_time,audit_status,max_members)
                VALUES(%s,%s,'Test room',%s,%s,1,3)
                """, (other, title, start, start + timedelta(hours=1)))
            activity = cur.lastrowid
            db.commit()
            bearer = token(cfg["JWT_SECRET"], student)
            result = ask(base, bearer, f"我要报名活动 ID {activity}，请生成报名确认卡。", "prepare_activity_signup")
            card = result["actions"][0]
            assert card["activityId"] == activity and card["activityTitle"] == title
            cur.execute("SELECT COUNT(*) FROM activity_member WHERE activity_id=%s AND user_id=%s", (activity, student))
            assert cur.fetchone()[0] == 0
            db.commit()
            print("PASS model prepared card without writing enrollment", flush=True)
            route = f'/api/ai/agent/actions/{card["id"]}/confirm'
            assert call(base, route, bearer=token(cfg["JWT_SECRET"], other))["code"] == 404
            assert call(base, route)["code"] == 401
            cur.execute("UPDATE agent_action SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=%s", (card["id"],))
            db.commit()
            assert call(base, route, bearer=bearer)["code"] == 400
            cur.execute("UPDATE agent_action SET expires_at=DATE_ADD(NOW(),INTERVAL 5 MINUTE) WHERE id=%s", (card["id"],))
            cur.execute("UPDATE activity SET status=3 WHERE id=%s", (activity,))
            db.commit()
            assert call(base, route, bearer=bearer)["code"] != 200
            cur.execute("SELECT COUNT(*) FROM activity_member WHERE activity_id=%s AND user_id=%s", (activity, student))
            assert cur.fetchone()[0] == 0
            cur.execute("UPDATE activity SET status=0 WHERE id=%s", (activity,))
            db.commit()
            print("PASS expired cards and unavailable activities do not write enrollment", flush=True)
            with ThreadPoolExecutor(max_workers=2) as pool:
                a, b = list(pool.map(lambda _: ok(call(base, route, bearer=bearer)), range(2)))
            assert a == b and a["status"] == "SUBMITTED"
            cur.execute("SELECT status FROM activity_member WHERE activity_id=%s AND user_id=%s", (activity, student))
            assert cur.fetchall() == ((0,),)
            print("PASS concurrent confirmation wrote exactly one pending enrollment", flush=True)
        finally:
            if activity:
                cur.execute("DELETE FROM activity_member WHERE activity_id=%s", (activity,))
                cur.execute("DELETE FROM message WHERE biz_type='activity' AND biz_id=%s", (activity,))
                cur.execute("DELETE FROM activity WHERE id=%s", (activity,))
                cur.execute("""
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema=DATABASE() AND table_name='agent_action'
                    """)
                if cur.fetchone()[0]:
                    cur.execute("DELETE FROM agent_action WHERE activity_id=%s", (activity,))
            for uid in (student, other):
                if uid:
                    cleanup_student(cur, uid)
            db.commit()


def verify_browser(base, cfg, db, script):
    suffix = uuid.uuid4().hex[:10]
    student = visitor = spare = admin = activity = idle = lost = partner = post = question = notice = None
    title = "BrowserActivity" + suffix
    identifier = "Makerspace-" + suffix
    idle_title = "BrowserIdle" + suffix
    lost_title = "BrowserLost" + suffix
    subject = "高等数学 " + suffix
    post_text = "图书馆午后 #校园 " + suffix
    question_title = "如何预约图书馆研讨室 " + suffix
    notice_title = "图书馆开放时间调整 " + suffix
    with db.cursor() as cur:
        try:
            for code, nickname in (("BS", "Browser Student"), ("BV", "Browser Visitor")):
                cur.execute("INSERT INTO user(student_no,nickname) VALUES(%s,%s)", (code + suffix, nickname))
                if code == "BS":
                    student = cur.lastrowid
                else:
                    visitor = cur.lastrowid
            cur.execute("INSERT INTO admin(username,password,role) VALUES(%s,'unused','super')", ("browser-" + suffix,))
            admin = cur.lastrowid
            cur.execute("""
                INSERT INTO activity(user_id,title,location,start_time,end_time,audit_status,max_members)
                VALUES(%s,%s,'Test Room',DATE_ADD(NOW(),INTERVAL 1 DAY),DATE_ADD(NOW(),INTERVAL 25 HOUR),1,3)
                """, (visitor, title))
            activity = cur.lastrowid
            if script == "verify_prototype_student.cjs":
                cur.execute("INSERT INTO idle_item(user_id,title,audit_status,status) VALUES(%s,%s,1,0)",
                            (visitor, idle_title))
                idle = cur.lastrowid
            if script == "verify_prototype_community.cjs":
                cur.execute("INSERT INTO user(student_no,nickname) VALUES(%s,'备用同学')", ("BC" + suffix,))
                spare = cur.lastrowid
                cur.execute("INSERT INTO lost_found(user_id,type,title,location,audit_status) VALUES(%s,1,%s,'图书馆',1)",
                            (visitor, lost_title))
                lost = cur.lastrowid
                cur.execute("INSERT INTO study_partner(user_id,subject,goal,schedule,audit_status) "
                            "VALUES(%s,%s,'一起复习微积分','周末上午',1)", (visitor, subject))
                partner = cur.lastrowid
                cur.execute("INSERT INTO post(user_id,content,images,audit_status) VALUES(%s,%s,%s,1)",
                            (visitor, post_text, json.dumps(["/images/home-campus.webp"])))
                post = cur.lastrowid
                cur.execute("INSERT INTO campus_question(user_id,title,content,category) VALUES(%s,%s,'期末小组讨论需要预约','生活')",
                            (visitor, question_title))
                question = cur.lastrowid
                cur.execute("INSERT INTO notice(admin_id,title,content,status,publish_time) VALUES(%s,%s,'周末开放时间为 9:00 至 17:00',1,NOW())",
                            (admin, notice_title))
                notice = cur.lastrowid
            db.commit()
            fixture = {
                "studentToken": token(cfg["JWT_SECRET"], student),
                "visitorToken": token(cfg["JWT_SECRET"], visitor),
                "adminToken": token(cfg["JWT_SECRET"], admin, "admin"),
                "studentId": student, "visitorId": visitor, "activityId": activity, "activityTitle": title, "identifier": identifier,
                "pdf": base64.b64encode(pdf_bytes(identifier)).decode(),
            }
            if idle:
                fixture.update(idleId=idle, idleTitle=idle_title)
            if lost:
                fixture.update(lostId=lost, lostTitle=lost_title, partnerSubject=subject, postId=post, postText=post_text,
                               questionId=question, questionTitle=question_title, noticeId=notice, noticeTitle=notice_title, spareId=spare)
            subprocess.run(["node", str(ROOT / "tests/agent" / script)],
                           env={**os.environ, "CAMPUS_BROWSER_FIXTURE": json.dumps(fixture)},
                           timeout=165, check=True)
        finally:
            if student:
                cur.execute("SELECT id FROM kb_document WHERE owner_id=%s AND state NOT IN ('DELETED','PURGED')", (student,))
                for (doc_id,) in cur.fetchall():
                    call(base, f"/api/ai/knowledge/{doc_id}", bearer=token(cfg["JWT_SECRET"], student), method="DELETE")
                cur.execute("DELETE FROM pdf_document WHERE user_id=%s", (student,))
            if activity:
                cur.execute("DELETE FROM activity_member WHERE activity_id=%s", (activity,))
                cur.execute("DELETE FROM message WHERE biz_type='activity' AND biz_id=%s", (activity,))
                cur.execute("DELETE FROM agent_action WHERE activity_id=%s", (activity,))
                cur.execute("DELETE FROM activity WHERE id=%s", (activity,))
            if idle:
                cur.execute("DELETE FROM idle_item WHERE id=%s", (idle,))
            if lost:
                cur.execute("DELETE FROM lost_found_claim WHERE lost_found_id=%s", (lost,))
                cur.execute("DELETE FROM message WHERE biz_type='lostfound' AND biz_id=%s", (lost,))
                cur.execute("DELETE FROM lost_found WHERE id=%s", (lost,))
            if partner:
                cur.execute("DELETE m FROM chat_conversation_member m JOIN chat_conversation c ON c.id=m.conversation_id "
                            "WHERE c.user1_id=LEAST(%s,%s) AND c.user2_id=GREATEST(%s,%s)",
                            (student, visitor, student, visitor))
                cur.execute("DELETE FROM chat_conversation WHERE user1_id=LEAST(%s,%s) AND user2_id=GREATEST(%s,%s)",
                            (student, visitor, student, visitor))
                cur.execute("DELETE FROM study_partner WHERE id=%s", (partner,))
            if post:
                cur.execute("DELETE FROM post_like WHERE post_id=%s", (post,))
                cur.execute("DELETE FROM post_comment WHERE post_id=%s", (post,))
                cur.execute("DELETE FROM favorite WHERE target_type='post' AND target_id=%s", (post,))
                cur.execute("DELETE FROM message WHERE biz_type='post' AND (user_id=%s OR user_id=%s)", (student, visitor))
                cur.execute("DELETE FROM post WHERE user_id=%s OR user_id=%s", (student, visitor))
            if question:
                cur.execute("DELETE FROM campus_answer WHERE question_id=%s", (question,))
                cur.execute("DELETE FROM campus_question WHERE id=%s", (question,))
            if notice:
                cur.execute("DELETE FROM notice WHERE id=%s", (notice,))
            for uid in (student, visitor, spare):
                if uid:
                    cleanup_student(cur, uid)
            if admin:
                cur.execute("DELETE FROM admin WHERE id=%s", (admin,))
            db.commit()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:18088")
    browser_scripts = {"browser": "verify_browser.cjs", "layout": "verify_assistant_layout.cjs",
                       "prototype": "verify_prototype_student.cjs", "game": "verify_prototype_game.cjs",
                       "community": "verify_prototype_community.cjs"}
    parser.add_argument("--stage", choices=("tools", "rag", "combined", "actions", *browser_scripts), default="tools")
    args = parser.parse_args()
    base = args.base.rstrip("/")
    assert urlsplit(base).hostname in ("127.0.0.1", "localhost", "::1"), "Loopback backend only"
    cfg = local_config()
    db = pymysql.connect(host="127.0.0.1", port=int(cfg.get("MYSQL_PORT", 3306)),
                         user=cfg.get("MYSQL_USER", "root"), password=cfg["MYSQL_PASSWORD"],
                         database=cfg.get("MYSQL_DB", "ai_campus_platform"), charset="utf8mb4")
    with db:
        if args.stage in browser_scripts:
            verify_browser(base, cfg, db, browser_scripts[args.stage])
        else:
            {"tools": verify_read_only, "rag": verify_rag, "combined": verify_combined,
             "actions": verify_actions}[args.stage](base, cfg, db)


if __name__ == "__main__":
    main()
