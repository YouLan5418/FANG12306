"""Verify one supplemental train through real purchase and cancellation; uses an isolated account."""
import json, uuid, secrets, urllib.request, urllib.parse, subprocess
from datetime import datetime, timezone, timedelta
from pathlib import Path
BASE='http://127.0.0.1:18080'
name='train_check_'+uuid.uuid4().hex[:12]
token=None; order=None; closed=False

def sql(statement):
    result=subprocess.run(['docker','exec','-i','postgres','psql','-U','postgres','-d','index12306','-v','ON_ERROR_STOP=1','-t','-A'],input=statement,encoding='utf-8',capture_output=True,check=True)
    return result.stdout.strip()

def api(path,data=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']=token
    req=urllib.request.Request(BASE+path,data=None if data is None else json.dumps(data,ensure_ascii=False).encode(),headers=headers)
    with urllib.request.urlopen(req,timeout=45) as response: result=json.loads(response.read())
    if str(result.get('code'))!='0': raise RuntimeError(str(result.get('message')))
    return result.get('data')

def query():
    riding=(datetime.now(timezone(timedelta(hours=8))).date()+timedelta(days=1)).isoformat()
    return api('/api/ticket-service/ticket/query?'+urllib.parse.urlencode({'departure':'北京南','arrival':'上海虹桥','departureDate':riding,'current':1,'size':10}))

try:
    before=query(); train=next(t for t in before['trainList'] if t['trainNumber']=='G9801'); train_id=train['trainId']
    previous=next(s['quantity'] for s in train['seatClassList'] if s['seatType']==2)
    random=str(secrets.randbelow(10**8)).zfill(8); password=secrets.token_urlsafe(18)
    api('/api/user-service/register',{'username':name,'password':password,'realName':'车次验证','idType':0,'idCard':'81000119990101'+random[-4:],'phone':'139'+random,'mail':name+'@example.invalid'})
    token=api('/api/user-service/v1/login',{'usernameOrMailOrPhone':name,'password':password})['accessToken']
    api('/api/user-service/passenger/save',{'realName':'车次验证乘客','idType':0,'idCard':'81000219990101'+random[-4:],'discountType':0,'phone':'138'+random})
    passenger=api('/api/user-service/passenger/query')[0]['id']
    purchased=api('/api/ticket-service/ticket/purchase',{'trainId':train_id,'departure':'北京南','arrival':'上海虹桥','passengers':[{'passengerId':passenger,'seatType':2,'ticketType':0}]})
    order=purchased['orderSn']
    after=next(t for t in query()['trainList'] if t['trainId']==train_id)
    sold=next(s['quantity'] for s in after['seatClassList'] if s['seatType']==2)
    assert sold==previous-1,(previous,sold)
    api('/api/ticket-service/ticket/cancel',{'orderSn':order}); closed=True
    restored=next(t for t in query()['trainList'] if t['trainId']==train_id)
    remaining=next(s['quantity'] for s in restored['seatClassList'] if s['seatType']==2)
    assert remaining==previous,(previous,remaining)
    assert sql('SELECT count(*) FROM t_seat WHERE train_id='+train_id+' AND seat_status<>0;').splitlines()[0]=='0'
    detail={'purchase_and_cancel_passed':True,'train_number':train['trainNumber'],'seat_quantity_before':previous,'after_purchase':sold,'after_cancel':remaining,'all_seats_restored':True}
    output=Path(__file__).resolve().parents[1]/'out/demo-trains-purchase-verification.json'
    output.write_text(json.dumps(detail,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(detail))
finally:
    if order and not closed:
        try: api('/api/ticket-service/ticket/cancel',{'orderSn':order}); closed=True
        except Exception: print('Test order retained for diagnosis: '+order)
    if token:
        try: api('/api/user-service/logout?'+urllib.parse.urlencode({'accessToken':token}))
        except Exception: pass
    if not order or closed:
        sql("BEGIN; DELETE FROM t_order_item_passenger WHERE order_sn IN (SELECT order_sn FROM t_order WHERE username='"+name+"'); DELETE FROM t_order_item WHERE username='"+name+"'; DELETE FROM t_order WHERE username='"+name+"'; DELETE FROM t_ticket WHERE username='"+name+"'; DELETE FROM t_passenger WHERE username='"+name+"'; DELETE FROM t_user_phone WHERE username='"+name+"'; DELETE FROM t_user_mail WHERE username='"+name+"'; DELETE FROM t_user WHERE username='"+name+"'; COMMIT;")
        print('Isolated test account and closed order removed')
