"""Verify the student activity draft and cross-role review on a disposable Docker stack."""

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
import json
import os
import subprocess
import uuid
from urllib.parse import urlsplit

import pymysql

from verify_live import ROOT, ask, call, local_config, ok, token


def verify(base, cfg, db, review, browser):
    marker = 'ActivityLoop' + uuid.uuid4().hex[:8]
    with db.cursor() as cur:
        students = []
        for suffix in ('Owner', 'Other'):
            cur.execute('INSERT INTO user(student_no,nickname) VALUES(%s,%s)', (marker[-8:] + suffix, suffix))
            students.append(cur.lastrowid)
        db.commit()
        student, other = [token(cfg['JWT_SECRET'], uid) for uid in students]
        no_details = ok(call(base, '/api/ai/agent/ask', {'question': '帮我发布一个校园活动。'}, student))
        assert not no_details.get('activityDrafts'), 'A vague request created a draft'
        print('PASS incomplete request does not create an invented activity draft', flush=True)
        form = {
            'title': f'{marker} 阅读交流会', 'description': '校园读书交流，校外领取方式待核对',
            'category': '学习交流', 'location': '图书馆二楼', 'maxMembers': 20,
            'images': [],
        }
        start = (datetime.now() + timedelta(days=7)).replace(hour=15, minute=0, second=0, microsecond=0)
        form['startTime'] = start.strftime('%Y-%m-%d %H:%M:%S')
        form['endTime'] = (start + timedelta(hours=2)).strftime('%Y-%m-%d %H:%M:%S')
        question = (f'帮我发布活动，标题为「{form["title"]}」，{form["startTime"]}开始，'
                    f'{form["endTime"]}结束，地点{form["location"]}，限20人，分类学习交流。请生成可编辑草稿。')
        if browser:
            fixture = {**form, 'studentToken': student, 'question': question, 'originalTitle': form['title'],
                       'editedTitle': form['title'] + ' 浏览器核对'}
            if review:
                cur.execute("INSERT INTO admin(username,password,role) VALUES(%s,'unused','audit')", (marker,))
                fixture['adminToken'] = token(cfg['JWT_SECRET'], cur.lastrowid, 'admin')
                db.commit()
            subprocess.run(['node', str(ROOT / 'tests/agent/verify_activity_loop.cjs')],
                           env={**os.environ, 'CAMPUS_ACTIVITY_FIXTURE': json.dumps(fixture)}, check=True, timeout=130)
            cur.execute('SELECT id,title,audit_status FROM activity WHERE user_id=%s', (students[0],))
            row, = cur.fetchall()
            assert row[1] == fixture['editedTitle']
            if review:
                assert row[2] == 2
                cur.execute("SELECT COUNT(*) FROM message WHERE user_id=%s AND biz_type='activity' AND biz_id=%s",
                            (students[0], row[0]))
                assert cur.fetchone()[0] == 1
            return
        partial = ask(base, student, f'活动标题是「{marker} 读书活动」，先给我活动草稿。',
                      'prepare_activity_publish', session_id=no_details['sessionId'])
        partial_draft, = partial['activityDrafts']
        assert all(partial_draft.get(field) is None for field in
                   ('location', 'description', 'category', 'startTime', 'endTime', 'signupDeadline', 'maxMembers'))
        print('PASS follow-up creates an editable draft with unknown facts blank', flush=True)
        natural_date = f'{start.year}年{start.month}月{start.day}日15点到17点'
        natural = ask(base, student, f'帮我发布活动，标题「{marker} 自然时间」，时间{natural_date}，地点图书馆。',
                      'prepare_activity_publish')
        natural_draft, = natural['activityDrafts']
        assert natural_draft['startTime'].replace('T', ' ') == form['startTime']
        assert natural_draft['endTime'].replace('T', ' ') == form['endTime']
        assert natural_draft.get('signupDeadline') is None
        print('PASS campus-style time range prefills both times without inventing a signup deadline', flush=True)
        result = ask(base, student, question, 'prepare_activity_publish')
        draft, = result['activityDrafts']
        assert draft['title'] == form['title'] and draft['state'] == 'DRAFT'
        assert draft['location'] == form['location'] and draft['maxMembers'] == 20
        assert draft['startTime'].replace('T', ' ')[:19] == form['startTime']
        assert not draft.get('signupDeadline'), 'The model invented a deadline'
        cur.execute('SELECT COUNT(*) FROM activity WHERE user_id=%s', (students[0],))
        assert cur.fetchone()[0] == 0
        route = f'/api/ai/agent/activity-drafts/{draft["id"]}'
        confirm = route + '/confirm'
        assert call(base, route, bearer=other, method='GET')['code'] == 404
        assert call(base, confirm, form, other)['code'] == 404
        assert call(base, confirm, {**form, 'endTime': '2020-01-01 00:00:00'}, student)['code'] != 200
        form['title'] += ' 已核对'
        with ThreadPoolExecutor(max_workers=2) as pool:
            a, b = pool.map(lambda _: ok(call(base, confirm, form, student)), range(2))
        assert a == b and a['state'] == 'SUBMITTED' and a['title'] == form['title']
        assert call(base, confirm, {**form, 'title': form['title'] + ' 另一版本'}, student)['code'] != 200
        activity_id = str(a['activityId'])
        db.commit()
        cur.execute('SELECT id,title,audit_status FROM activity WHERE user_id=%s', (students[0],))
        rows = cur.fetchall()
        assert len(rows) == 1 and str(rows[0][0]) == activity_id and rows[0][1] == form['title']
        history_path = f'/api/ai/session/{result["sessionId"]}/messages'
        history = ok(call(base, history_path, bearer=student, method='GET'))['list']
        assert any(card['id'] == draft['id'] and card['state'] == 'SUBMITTED'
                   for message in history for card in (message.get('attachments') or {}).get('activityDrafts', []))
        print('PASS owned draft, edited form, invalid date rejection, one activity on concurrent confirmation, and restored receipt', flush=True)
        if review:
            verify_review(base, cfg, db, marker, students[0], student, activity_id, form, history_path)


def verify_review(base, cfg, db, marker, student_id, student, activity_id, activity_form, history_path):
    with db.cursor() as cur:
        cur.execute("INSERT INTO admin(username,password,role) VALUES(%s,'unused','audit')", (marker,))
        admin = token(cfg['JWT_SECRET'], cur.lastrowid, 'admin')
        cur.execute('SELECT audit_status FROM activity WHERE id=%s', (activity_id,))
        assert cur.fetchone()[0] == 0, 'Use isolated database with default content rules'
        db.commit()
        route = '/api/admin/assistant'
        turn = ok(call(base, route + '/ask', {'question': f'请查看待审核活动编号 {activity_id}'}, admin))
        evidence, = turn['turn']['records']
        assert evidence['kind'] == 'activity' and evidence['id'] == activity_id
        assert evidence['activity']['location'] == activity_form['location'] and evidence['activity']['maxMembers'] == 20
        keyword = ok(call(base, route + '/ask', {'question': f'找出待审核活动，标题关键词只用 {marker}。'}, admin))
        assert [row['id'] for row in keyword['turn']['records']] == [activity_id]
        detail = f'/api/admin/audit/activity/{activity_id}'
        assert call(base, detail, bearer=student, method='GET')['code'] in (401, 403)
        assert ok(call(base, detail, bearer=admin, method='GET'))['title'] == activity_form['title']
        direct = ok(call(base, route + '/ask', {'question': f'请直接审核通过活动编号 {activity_id}，执行并给我审核回执。'}, admin))
        assert [row['id'] for row in direct['turn']['records']] == [activity_id]
        assert '确认' in direct['turn']['answer']
        padded = ok(call(base, route + '/ask', {'question': f'请查询活动编号 000{activity_id}'}, admin))
        assert [row['id'] for row in padded['turn']['records']] == [activity_id]
        ambiguous = {'question': f'请查看活动编号 {activity_id}，也可能是编号 {int(activity_id) + 1}。'}
        assert call(base, route + '/ask', ambiguous, admin)['code'] != 200
        ambiguous = {'question': f'请查看活动 {activity_id}，活动 {int(activity_id) + 1} 也看看。'}
        assert call(base, route + '/ask', ambiguous, admin)['code'] != 200
        assert ok(call(base, detail, bearer=admin, method='GET'))['auditStatus'] == 0
        form = {'sessionId': turn['sessionId'], 'kind': 'activity', 'itemId': activity_id, 'decision': 'PASS',
                'version': evidence['activity']['version']}
        prepare = route + '/reviews/prepare'
        assert call(base, prepare, form, student)['code'] in (401, 403)
        assert call(base, prepare, {**form, 'kind': 'idle'}, admin)['code'] == 404
        assert call(base, prepare, {**form, 'decision': 'REJECT'}, admin)['code'] != 200
        expired = ok(call(base, prepare, form, admin))
        cur.execute('UPDATE admin_assistant_review SET expires_at=DATE_SUB(NOW(6), INTERVAL 1 MINUTE) WHERE id=%s',
                    (expired['id'],))
        db.commit()
        assert call(base, route + f'/reviews/{expired["id"]}/confirm', bearer=admin)['code'] != 200
        stale = ok(call(base, prepare, form, admin))
        ok(call(base, f'/api/activity/{activity_id}', activity_form, student, method='PUT'))
        assert call(base, route + f'/reviews/{stale["id"]}/confirm', bearer=admin)['code'] != 200
        assert call(base, prepare, form, admin)['code'] != 200
        db.commit()
        refreshed = ok(call(base, route + '/ask', {'question': f'请查看待审核活动编号 {activity_id}',
                                                   'sessionId': turn['sessionId']}, admin))
        form['version'] = refreshed['turn']['records'][0]['activity']['version']
        prepared = ok(call(base, prepare, form, admin))
        assert prepared['state'] == 'PENDING'
        cur.execute('SELECT audit_status FROM activity WHERE id=%s', (activity_id,))
        assert cur.fetchone()[0] == 0
        db.commit()
        confirm = route + f'/reviews/{prepared["id"]}/confirm'
        with ThreadPoolExecutor(max_workers=2) as pool:
            a, b = pool.map(lambda _: ok(call(base, confirm, bearer=admin)), range(2))
        assert a == b and a['state'] == 'APPLIED' and a['auditStatus'] == 1
        assert call(base, f'/api/admin/audit/activity/{activity_id}/reject?pendingOnly=true',
                    {'reason': '另一审核窗口'}, admin, method='PUT')['code'] != 200
        cur.execute("SELECT COUNT(*) FROM message WHERE user_id=%s AND biz_type='activity' AND biz_id=%s", (student_id, activity_id))
        assert cur.fetchone()[0] == 1
        history = ok(call(base, history_path, bearer=student, method='GET'))['list']
        assert any(str(card['activityId']) == activity_id and card['auditStatus'] == 1
                   for message in history for card in (message.get('attachments') or {}).get('activityDrafts', []))
        print('PASS same activity: admin evidence and confirmation, one notification, student receipt shows approval', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', required=True)
    parser.add_argument('--review', action='store_true')
    parser.add_argument('--browser', action='store_true')
    args = parser.parse_args()
    assert urlsplit(args.base).hostname in ('127.0.0.1', 'localhost', '::1'), 'Loopback backend only'
    cfg = local_config()
    assert int(cfg.get('MYSQL_PORT', 3306)) != 3306, 'Use a disposable database on a dedicated port'
    with pymysql.connect(host='127.0.0.1', port=int(cfg['MYSQL_PORT']), user=cfg.get('MYSQL_USER', 'root'),
                         password=cfg['MYSQL_PASSWORD'], database=cfg.get('MYSQL_DB', 'ai_campus_platform'),
                         charset='utf8mb4') as db:
        verify(args.base.rstrip('/'), cfg, db, args.review, args.browser)
