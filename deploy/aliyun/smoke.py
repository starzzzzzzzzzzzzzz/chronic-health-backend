#!/usr/bin/env python3
"""Cloud DEMO acceptance: creates fictional records; does not delete existing data.

Usage: python3 deploy/aliyun/smoke.py --env .dev/aliyun/backend.env --ai
Requires an SSH tunnel at http://127.0.0.1:19089/ncd-ai by default.
"""
import argparse
import datetime
import json
import os
import pathlib
import time
import urllib.error
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--env', required=True)
parser.add_argument('--base', default='http://127.0.0.1:19089/ncd-ai')
parser.add_argument('--report', default='.dev/aliyun/smoke-report.json')
parser.add_argument('--ai', action='store_true', help='Makes one paid AI generation using fictional fixture data')
parser.add_argument('--public', action='store_true', help='Use the public gateway; omit internal Actuator check')
args = parser.parse_args()
os.umask(0o077)
env = dict(line.split('=', 1) for line in pathlib.Path(args.env).read_text().splitlines()
           if '=' in line and not line.startswith('#'))
steps = []
report = {'mode': 'FICTIONAL_DEMO', 'base': args.base, 'steps': steps, 'passed': False}
keys = {'editor': 'HEALTH_DEMO_API_TOKEN', 'reviewer': 'HEALTH_DEMO_REVIEWER_TOKEN',
        'patient': 'HEALTH_DEMO_PATIENT_TOKEN'}


def api(method, path, body=None, role='editor', status=200, key=None):
    headers = {'Content-Type': 'application/json'}
    if role:
        headers['Authorization'] = env[keys[role]]
    if key:
        headers['Idempotency-Key'] = key
    request = urllib.request.Request(args.base + path, method=method, headers=headers,
                                    data=None if body is None else json.dumps(body).encode())
    try:
        response = urllib.request.urlopen(request, timeout=150)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        code = response.code
        data = json.load(response)
    steps.append({'method': method, 'path': path, 'status': code, 'expected': status})
    pathlib.Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2))
    assert code == status, f'{method} {path}: expected {status}, got {code}'
    return data.get('data', data)


assert api('GET', '/health', role=None)['status'] == 'UP'
if not args.public:
    assert api('GET', '/actuator/health', role=None)['status'] == 'UP'
api('GET', '/patients', role=None, status=401)
patients = api('GET', '/patients')['items']
patient = next(p for p in patients if p['id'] == '00000000-0000-4000-8000-000000000001')
summary = api('GET', f"/patients/{patient['id']}/health-summary")
assert len(summary['axes']) == 6
archives = api('GET', '/store/care/grant-administration/archives')
assert archives['items']
schema = api('GET', '/v3/api-docs/health', role=None)
report['openapiOperations'] = sum(m in {'get', 'post', 'patch', 'put', 'delete'}
                                  for path in schema['paths'].values() for m in path)
today = str(datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).date())
run = str(uuid.uuid4())


def draft(title):
    return api('POST', '/care-plans', {'patientId': patient['id'],
        'expectedProfileRevision': summary['profileRevision'], 'title': title,
        'startDate': today, 'endDate': today, 'zoneId': 'Asia/Shanghai'}, status=201)


plan = draft('云端部署验收-虚构-' + run)
v = plan['versionId']
report['planVersionId'] = v
plan = api('GET', f'/plan-versions/{v}')
fact = next(f['id'] for f in plan['inputSnapshot']['facts'] if f['axisCode'] == 'B')
enabled = ['diet', 'exercise', 'routine', 'monitoring', 'other']
plan = api('PATCH', f'/plan-versions/{v}', {'expectedRevision': plan['rowVersion'],
    'primaryGoal': '虚构软件验收：记录日常执行情况', 'goalBasis': {'factIds': [fact]},
    'modules': [{'moduleCode': 'B0', 'selectionReason': '仅验证软件业务流程', 'factIds': [fact]}],
    'dimensions': [{'dimensionCode': d, 'decision': 'ENABLED' if d in enabled else 'CLOSED',
        'decisionReason': None if d in enabled else '缺少医嘱或专业审核模板'}
        for d in ['medication', *enabled, 'tcm']],
    'items': [{'dimensionCode': d, 'title': '虚构测试-' + d,
        'content': '仅用于软件测试的记录，不是医疗建议', 'actorType': 'PATIENT',
        'executionGoal': True, 'isSelected': True, 'sortOrder': i,
        'basis': [{'moduleCode': 'B0', 'factId': fact}]} for i, d in enumerate(enabled)]})
api('PATCH', f'/plan-versions/{v}', {'expectedRevision': '0', 'primaryGoal': '旧版本'}, status=409)
conditions = api('GET', f'/plan-versions/{v}/condition-confirmations')
api('POST', f'/plan-versions/{v}/condition-confirmations', {
    'expectedRevision': conditions['rowVersion'], 'entries': [{
        'conditionKey': c['conditionKey'], 'status': 'CONFIRMED',
        'value': {'actorType': 'PATIENT', 'available': True, 'environmentConfirmed': True},
        'confirmationNote': '仅虚构软件测试确认'} for c in conditions['conditions']]}, status=201)
ctx = api('GET', f'/plan-versions/{v}/review-context')
assert not [i for i in ctx['issues'] if i['severity'] == 'BLOCKER']
plan = api('GET', f'/plan-versions/{v}')
review = api('POST', f'/plan-versions/{v}/review-submissions', {'expectedRevision': plan['rowVersion']}, status=201)
decision = {'expectedRevision': review['rowVersion'], 'decision': 'APPROVE', 'comment': '虚构软件验收'}
api('POST', f"/review-submissions/{review['id']}/decisions", decision, status=403)
api('POST', f"/review-submissions/{review['id']}/decisions", decision, role='reviewer', status=201)
plan = api('GET', f'/plan-versions/{v}')
bundle = api('POST', f'/plan-versions/{v}/task-bundles', {'expectedRevision': plan['rowVersion']}, status=201)
b = bundle['id']
assert len(bundle['groups']) == 5 and bundle['sourcePlanVersionId'] == v
bundle = api('PATCH', f'/task-bundles/{b}', {'expectedRevision': bundle['rowVersion'], 'groups': [
    {'id': g['id'], 'executionConfirmed': True, 'confirmationNote': '虚构测试排程', 'schedule': {
        'isoWeekdays': [1, 2, 3, 4, 5, 6, 7], 'rotationPolicy': 'FIXED', 'slots': [{
            'slotKey': 'daily', 'mode': 'FIXED', 'localTime': '00:00',
            'opensOffsetMinutes': 0, 'closesOffsetMinutes': 1439}]}} for g in bundle['groups']]})
schedule = api('POST', f'/task-bundles/{b}/schedule-previews', {'expectedRevision': bundle['rowVersion']}, status=201)
assert not [i for i in schedule['issues'] if i['severity'] == 'BLOCKER']
bundle = api('GET', f'/task-bundles/{b}')
review = api('POST', f'/task-bundles/{b}/review-submissions', {
    'expectedRevision': bundle['rowVersion'], 'schedulePreviewId': schedule['id']}, status=201)
review = api('POST', f"/review-submissions/{review['id']}/decisions", {
    'expectedRevision': review['rowVersion'], 'decision': 'APPROVE', 'comment': '虚构软件二审'},
    role='reviewer', status=201)
bundle = api('GET', f'/task-bundles/{b}')
intent = {'expectedRevision': bundle['rowVersion'], 'reviewSubmissionId': review['id'],
          'schedulePreviewId': schedule['id'], 'effectiveAt': today + 'T00:00:00+08:00', 'replacementScope': 'NONE'}
preview = api('POST', f'/task-bundles/{b}/publication-previews', intent, status=201)
intent['publicationPreviewId'] = preview['id']
publication = api('POST', f'/task-bundles/{b}/publications', intent, key=run + '-pub', status=201)
assert api('POST', f'/task-bundles/{b}/publications', intent, key=run + '-pub')['id'] == publication['id']
tasks = api('GET', f"/patients/{patient['id']}/execution-tasks?date={today}", role='patient')
own = [t for t in tasks if t['publicationId'] == publication['id']]
assert len(own) == 5
t = own[0]['id']
body = {'completion': 'COMPLETED', 'note': '云端虚构验收'}
api('POST', f'/execution-tasks/{t}/check-ins', body, key=run + '-forbid', status=403)
checkin = api('POST', f'/execution-tasks/{t}/check-ins', body, role='patient', key=run + '-check', status=201)
assert checkin['assessment']['completion'] == 'COMPLETED'
assert api('POST', f'/execution-tasks/{t}/check-ins', body, role='patient', key=run + '-check')['id'] == checkin['id']
api('POST', f"/check-ins/{checkin['id']}/amendments", {'expectedRevision': checkin['resultRevision'],
    'completion': 'PARTIAL', 'reason': '虚构验收更正'}, role='patient', key=run + '-amend', status=201)
assert len(api('GET', f'/execution-tasks/{t}/check-ins', role='patient')) == 2
report.update(publicationId=publication['id'], executionTaskId=t)
print('Cloud business workflow passed', flush=True)

if args.ai:
    plan = draft('云端AI验收-虚构-' + run)
    v = plan['versionId']
    job = api('POST', f'/plan-versions/{v}/generation-jobs', {
        'expectedRevision': '0', 'expectedProfileRevision': summary['profileRevision']}, key=run + '-ai', status=202)
    deadline = time.monotonic() + 300
    while job['status'] in ('PENDING', 'RUNNING') and time.monotonic() < deadline:
        time.sleep(3)
        job = api('GET', f"/plan-generation-jobs/{job['id']}")
    report['ai'] = {'jobId': job['id'], 'status': job['status'], 'errorCode': job.get('errorCode')}
    assert job['status'] == 'SUCCEEDED', report['ai']
    adopted = api('POST', f"/plan-generation-jobs/{job['id']}/adoptions", {'expectedRevision': '0'})
    assert adopted['status'] == 'DRAFT'
    assert api('GET', f"/plan-generation-jobs/{job['id']}")['status'] == 'APPLIED'
    report['ai']['adoptedPlanVersionId'] = v
    print('Cloud AI generation and adoption passed', flush=True)

report['passed'] = True
report['completedAt'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
pathlib.Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2))
print(f'PASS: {len(steps)} HTTP requests; OpenAPI operations: {report["openapiOperations"]}')
