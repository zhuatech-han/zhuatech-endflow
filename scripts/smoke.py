#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Exercise an explicitly disposable local MySQL deployment; never display credentials."""
import argparse,json,urllib.request,urllib.error,http.cookiejar,secrets,uuid,os
from pathlib import Path
from datetime import datetime,timedelta
from zoneinfo import ZoneInfo
from urllib.parse import urlparse
from concurrent.futures import ThreadPoolExecutor
p=argparse.ArgumentParser();p.add_argument('--base',default='http://127.0.0.1:8120');p.add_argument('--env',default='.env');p.add_argument('--allow-test-writes',action='store_true');p.add_argument('--verify',action='store_true');args=p.parse_args()
u=urlparse(args.base);assert u.scheme=='http' and u.hostname in {'127.0.0.1','localhost'} and not u.query and u.path in {'','/'},'Only local verification is supported'
root=Path(__file__).resolve().parents[1];state=root/'.smoke-state.json'
env=dict(line.split('=',1) for line in Path(args.env).read_text().splitlines() if '=' in line and not line.startswith('#'));count=0
class Client:
    """Same-origin test session with CSRF. 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    def __init__(self,name,password):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));self.csrf=None
        self.get_csrf();self.call('/auth/login','POST',{'username':name,'password':password})
    def get_csrf(self): self.csrf=self.call('/auth/csrf')
    def call(self,path,method='GET',data=None,status=200,code=None,csrf=True):
        global count
        headers={'Content-Type':'application/json'}
        if self.csrf and csrf:headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(args.base+'/api'+path,method=method,headers=headers,data=json.dumps(data).encode() if data is not None else None)
        try:
            with self.opener.open(req,timeout=30) as res:actual=res.status;value=json.load(res)
        except urllib.error.HTTPError as e:actual=e.code;value=json.load(e)
        assert actual==status,f'{method} {path}: expected {status}, got {actual}, code={value.get("code") if isinstance(value,dict) else "unknown"}'
        if code:assert value.get('code')==code,f'{path}: wrong error code'
        count+=1;return value

admin=Client('admin',env['ADMIN_PASSWORD'])
def key():return str(uuid.uuid4())
def report():print(json.dumps({'checks':count,'result':'PASS','mode':'persistence' if args.verify else 'fresh-mysql'}))
if args.verify:
 v=json.loads(state.read_text());author=Client(v['users']['author'],v['password']);buyer=Client(v['users']['buyer'],v['password']);reviewer=Client(v['users']['reviewer'],v['password'])
 for cid in v['closed']:
  d=author.call('/cases/'+str(cid));assert d['record']['status']=='CLOSED';assert d['events']
 d=buyer.call('/cases/'+str(v['preview']));assert d['record']['status']=='ORDERED' and d['received']==50;assert len(d['receipts'])==1
 reviewer.call('/cases/'+str(v['preview'])+'/report.json');admin.call('/dashboard');report();raise SystemExit
assert args.allow_test_writes,'Use --allow-test-writes only on a disposable named test deployment'
roles={r['name']:r['id'] for r in admin.call('/admin/roles')};suffix=key()[:8];password='Aa9'+secrets.token_urlsafe(24)
dep=admin.call('/admin/departments','POST',{'name':'TEST 电子料号协作 '+suffix})['id'];d2=admin.call('/admin/departments','POST',{'name':'TEST 部门隔离 '+suffix})['id'];users={};ids={}
for name,role,department in [('author','需求编制员',dep),('buyer','采购执行员',dep),('reviewer','独立复核员',dep),('other','采购执行员',dep),('outside','需求编制员',d2)]:
 users[name]=name+'-'+suffix;ids[name]=admin.call('/admin/users','POST',{'username':users[name],'displayName':{'author':'TEST 需求工程师','buyer':'TEST 采购执行员','reviewer':'TEST 独立复核员','other':'TEST 未指派人员','outside':'TEST 其他部门'}[name],'password':password,'roleId':roles[role],'departmentId':department,'enabled':True})['id']
clients={n:Client(u,password) for n,u in users.items()};author,buyer,reviewer,other,outside=[clients[n] for n in ['author','buyer','reviewer','other','outside']]
today=datetime.now(ZoneInfo('Asia/Shanghai')).date();later=today+timedelta(days=30);ship=today+timedelta(days=90)
def case_input(route='LAST_BUY'):
 return {'requestKey':key(),'code':'TEST-EOL-'+key()[:8].upper(),'title':'TEST 控制板元件停产评审','category':'PDN','manufacturer':'TEST 虚构制造商','partNumber':'TEST-IC-001','sourceRef':'TEST 隔离验收通知，不是商业停产通知','departmentId':dep,'buyerId':ids['buyer'],'reviewerId':ids['reviewer'],'noticeDate':str(today),'lastBuyDate':str(later),'lastShipDate':str(ship),'decision':route,'alternativePart':'TEST-ALT-001' if route=='ALTERNATE' else '', 'validationRef':'TEST 虚构验证报告' if route=='ALTERNATE' else '', 'rationale':'TEST 基于验收需求与独立分配库存，仅验证系统流程','packSize':25,'minimumOrder':100,'decisionQty':125 if route=='LAST_BUY' else 0,'unitPrice':2,'budget':250}
def create(route='LAST_BUY'):
 v=case_input(route);d=author.call('/cases','POST',v);cid=d['record']['id'];assert author.call('/cases','POST',v)['record']['id']==cid
 return cid
cid=create();path='/cases/'+str(cid)
def detail():return author.call(path)
def cmd():return {'requestKey':key(),'version':detail()['record']['version'],'note':'TEST 已实际核对本次验收输入与预期数量，没有真实订单'}
def action(who,action,extra=None,status=200,code=None):return who.call(path+'/commands/'+action,'POST',dict(cmd(),**(extra or {})),status,code)
def line():return {'requestKey':key(),'version':detail()['record']['version'],'productCode':'TEST-CTRL-BOARD','description':'TEST 控制板支持期需求','monthlyDemand':10,'months':12,'serviceReserve':5,'allocatedStock':20,'confirmedInbound':0,'evidence':'TEST 已核对独立分配的模拟库存与需求，未连接真实ERP'}
closed=[]
for route in ['LAST_BUY','ALTERNATE','RETIRE']:
 if route!='LAST_BUY':cid=create(route);path='/cases/'+str(cid)
 author.call(path+'/lines','POST',line());assert detail()['calculation']['shortage']==105 and detail()['calculation']['recommended']==125
 other.call(path,status=403,code='OUT_OF_SCOPE');outside.call(path+'/report.json',status=403,code='OUT_OF_SCOPE');assert other.call('/cases')['total']==0
 action(author,'submit');action(admin,'approve',status=403,code='NOT_ASSIGNED');action(reviewer,'approve');author.call(path+'/lines','POST',line(),409,'INVALID_STATE')
 if route=='LAST_BUY':
  action(buyer,'order',{'reference':'TEST-PO-001','date':str(today),'promisedDate':str(ship)})
  retry=dict(cmd(),reference='TEST-R-001',date=str(today),quantity=50);buyer.call(path+'/commands/receive','POST',retry);buyer.call(path+'/commands/receive','POST',retry);assert detail()['received']==50
  buyer.call(path+'/commands/receive','POST',dict(retry,quantity=60),409,'IDEMPOTENCY_CONFLICT')
  action(buyer,'receive',{'reference':'TEST-R-OVER','date':str(today),'quantity':76},409,'OVER_RECEIPT')
  action(buyer,'receive',{'reference':'TEST-R-002','date':str(today),'quantity':75});assert detail()['record']['status']=='RECEIVED'
  rid=detail()['receipts'][0]['id'];reviewer.call(path+'/receipts/'+str(rid)+'/reverse','POST',cmd());assert detail()['received']==75 and detail()['receipts'][0]['reversedBy']==ids['reviewer']
  action(buyer,'receive',{'reference':'TEST-R-003','date':str(today),'quantity':50})
 else:action(buyer,'implement',{'reference':'TEST 受控工程变更或退役实施凭证'})
 action(buyer,'close',status=403,code='FORBIDDEN');action(reviewer,'close');closed.append(cid);d=author.call(path+'/report.json');assert d['record']['status']=='CLOSED' and d['events'];assert not any(k in json.dumps(d) for k in ['passwordHash','zhuatech2']);action(buyer,'receive',{'reference':'TEST-R-AFTER','date':str(today),'quantity':1},409,'INVALID_STATE')
# Keep a real isolated partially received record for browser verification.
cid=create();path='/cases/'+str(cid);author.call(path+'/lines','POST',line());action(author,'submit');action(reviewer,'approve');action(buyer,'order',{'reference':'TEST-PO-PREVIEW','date':str(today),'promisedDate':str(ship)})
retry=dict(cmd(),reference='TEST-R-PARALLEL',date=str(today),quantity=50)
with ThreadPoolExecutor(max_workers=2) as pool:assert len(list(pool.map(lambda _:buyer.call(path+'/commands/receive','POST',retry),range(2))))==2
assert detail()['received']==50 and len(detail()['receipts'])==1
preview=cid
other.call('/admin/users',status=403,code='FORBIDDEN');author.call('/cases','POST',case_input(),403,'FORBIDDEN',csrf=False)
# Separate discarded draft proves CRUD and protects returned history.
cid=create();path='/cases/'+str(cid);v=line();v['monthlyDemand']=1.5;author.call(path+'/lines','POST',v,400,'INVALID_INPUT');author.call(path+'/lines','POST',line());action(author,'submit');action(reviewer,'return');author.call(path+'?version='+str(detail()['record']['version']),'DELETE',status=409,code='HISTORY_PROTECTED');action(author,'submit');action(reviewer,'cancel')
cid=create();path='/cases/'+str(cid);author.call(path+'?version='+str(detail()['record']['version']),'DELETE');author.call(path,status=404,code='NOT_FOUND')
assert author.call('/cases?status=CLOSED&size=1&sort=deadline')['total']==3;assert author.call('/cases?search=TEST&sort=code')['total']==5
assert buyer.call('/workbench');assert reviewer.call('/workbench');admin.call('/dashboard');admin.call('/audit');admin.call('/admin/settings');admin.call('/admin/menus');admin.call('/admin/permissions');admin.call('/admin/dictionaries')
assert 'passwordHash' not in json.dumps(admin.call('/admin/users'))
# Revoke permissions while keeping a live session and verify immediate denial, then restore.
r=next(x for x in admin.call('/admin/roles') if x['name']=='采购执行员');saved=dict(r);r['permissions']=['case.read'];admin.call('/admin/roles/'+str(r['id']),'PUT',r);path='/cases/'+str(preview);action(buyer,'receive',{'reference':'TEST-DENIED','date':str(today),'quantity':1},403,'FORBIDDEN');admin.call('/admin/roles/'+str(r['id']),'PUT',saved)
private={'password':password,'users':users,'ids':ids,'closed':closed,'preview':preview,'department':dep}
fd=os.open(state,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
with os.fdopen(fd,'w') as f:json.dump(private,f)
report()
