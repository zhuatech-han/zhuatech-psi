#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""真实部署 HTTP 验证：只对显式传入的独立测试地址创建虚构业务。官网与商业咨询见文件头。"""
import argparse,json,uuid,urllib.request,urllib.error,http.cookiejar
from decimal import Decimal
parser=argparse.ArgumentParser();parser.add_argument('--url',required=True);parser.add_argument('--env-file',required=True);parser.add_argument('--output',required=True);args=parser.parse_args()
from pathlib import Path
env=dict(line.split('=',1) for line in Path(args.env_file).read_text().splitlines() if '=' in line and not line.startswith('#'))
assert args.url.startswith(('http://127.0.0.1:','http://localhost:')),'Only isolated localhost tests are supported'
jar=http.cookiejar.CookieJar();opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar));checks=0
csrf=None

def request(path,body=None,expected=200):
    global checks,csrf
    headers={'Content-Type':'application/json'}
    if body is not None:headers[csrf['header']]=csrf['token']
    req=urllib.request.Request(args.url+path,data=None if body is None else json.dumps(body).encode(),headers=headers)
    try:
        with opener.open(req,timeout=30) as res:status=res.status;raw=res.read()
    except urllib.error.HTTPError as e:status=e.code;raw=e.read()
    assert status==expected,(path,status,raw[:150] if expected!=200 else 'Unexpected response')
    checks+=1
    return json.loads(raw) if raw else None
csrf=request('/api/auth/csrf');request('/actuator/health');request('/api/auth/login',{'username':env.get('ADMIN_USERNAME','admin'),'password':env['ADMIN_PASSWORD']});csrf=request('/api/auth/csrf');catalog=request('/api/catalog')
p=catalog['products'][0];w=catalog['warehouses'][0];customer=next(p for p in catalog['parties'] if p['kind']=='CUSTOMER');supplier=next(p for p in catalog['parties'] if p['kind']=='SUPPLIER')

def command(**kwargs):return dict(requestKey=str(uuid.uuid4()),reference='TEST-'+str(uuid.uuid4())[:8],note='Fictional acceptance data',**kwargs)
def order(kind,qty,price):return request('/api/orders',{'kind':kind,'partyId':customer['id'] if kind=='SALES' else supplier['id'],'warehouseId':w['id'],'lines':[{'productId':p['id'],'quantity':qty,'price':price}]})
def action(d,kind,**kwargs):return request('/api/orders/'+str(d['order']['id'])+'/'+kind,command(**kwargs))
def post_goods(d,qty):return action(d,'post',lines=[{'lineId':d['lines'][0]['id'],'quantity':qty}])
def equal(n,value):assert Decimal(str(n))==Decimal(value),(n,value)
buy=action(order('PURCHASE','100','10.00'),'confirm');buy=post_goods(buy,'60');assert buy['order']['status']=='PARTIAL';buy=post_goods(buy,'40');buy=action(buy,'pay',amount='600.00');equal(buy['balance'],'400')
sale=action(order('SALES','30','15.00'),'confirm');sale=post_goods(sale,'20');source=sale['movements'][0]['id'];sale=post_goods(sale,'10');sale=action(sale,'return',sourceId=source,quantity='2');sale=action(sale,'pay',amount='200.00');equal(sale['balance'],'220');equal(sale['grossMargin'],'140');equal(sale['order']['netCost'],'280')
stock=request('/api/lists/stock?size=100')['items'];s=next(s for s in stock if s['productId']==p['id'] and s['warehouseId']==w['id']);equal(s['quantity'],'72');equal(s['value'],'720')
statement=request('/api/statement?partyId='+str(customer['id']));equal(statement['balance'],'220');reports=request('/api/reports');equal(reports['summary']['netSales'],'420');equal(reports['summary']['grossMargin'],'140')
role=request('/api/admin/roles',{'name':'Test operator '+str(uuid.uuid4())[:8],'scope':'DEPARTMENT','permissions':['dashboard','master.read','purchase.read','sales.read','stock.read','stock.write']})
operator_password='Test'+str(uuid.uuid4())+'A9'
operator=request('/api/admin/users',{'username':'operator','displayName':'示例仓管 / Demo operator','password':operator_password,'roleId':role['id'],'departmentId':w['departmentId'],'enabled':True})
assert 'passwordHash' not in operator
request('/api/auth/logout',{});csrf=request('/api/auth/csrf');request('/api/auth/login',{'username':'operator','password':operator_password});csrf=request('/api/auth/csrf');request('/api/orders/'+str(sale['order']['id'])+'/pay',command(amount='1'),403)
request('/api/auth/logout',{});csrf=request('/api/auth/csrf')
output={'checks':checks,'purchaseId':buy['order']['id'],'saleId':sale['order']['id'],'productId':p['id'],'warehouseId':w['id'],'customerId':customer['id'],'supplierId':supplier['id'],'operatorUsername':'operator','operatorPassword':operator_password,'expected':{'stock':'72','stockValue':'720','receivable':'220','payable':'400','grossMargin':'140'}}
path=Path(args.output);path.write_text(json.dumps(output));path.chmod(0o600)
print(f'PASS: {checks} HTTP checks; stock 72 / value 720 / receivable 220 / payable 400 / gross margin 140; unauthorized finance denied')
