import json, time, uuid, secrets, urllib.request, urllib.error, subprocess, sys
sys.stdout.reconfigure(encoding="utf-8")
from pathlib import Path
BASE="http://127.0.0.1:18080"
RUN="live_"+uuid.uuid4().hex[:12]
REPORT={"run":RUN,"checks":[],"conversations":[]}
USERS=[]; TOKENS=[]; CATEGORY=None; DOCUMENT=None

def sql(statement):
    result=subprocess.run(["docker","exec","-i","postgres","psql","-U","postgres","-d","index12306","-v","ON_ERROR_STOP=1","-t","-A"],input=statement,encoding="utf-8",capture_output=True,check=True)
    return result.stdout.strip()
def request(path,data=None,token=None,method=None,timeout=90):
    headers={"Content-Type":"application/json"}
    if token: headers["Authorization"]=token
    req=urllib.request.Request(BASE+path, data=None if data is None else json.dumps(data,ensure_ascii=False).encode(),headers=headers,method=method)
    with urllib.request.urlopen(req,timeout=timeout) as res: return json.loads(res.read())
def result(path,data=None,token=None,method=None):
    reply=request(path,data,token,method)
    if str(reply.get("code"))!="0": raise RuntimeError(str(reply.get("message")))
    return reply.get("data")
def check(name,condition,detail=""):
    REPORT["checks"].append({"name":name,"passed":bool(condition),"detail":detail})
    print(("PASS " if condition else "FAIL ")+name+" "+detail,flush=True)
def chat(token,question,session=None,cancel=False):
    data={"message":question}
    if session: data["sessionId"]=session
    req=urllib.request.Request(BASE+"/api/customer-service/chat",data=json.dumps(data,ensure_ascii=False).encode(),headers={"Content-Type":"application/json","Authorization":token})
    events=[]; start=time.monotonic(); first=None
    with urllib.request.urlopen(req,timeout=85) as response:
        for raw in response:
            line=raw.decode("utf-8").strip()
            if not line.startswith("data:"): continue
            event=json.loads(line[5:]);events.append(event)
            if event.get("type")=="token" and first is None: first=time.monotonic()-start
            if cancel and event.get("type") in ("token","thinking","tool_call"): break
    elapsed=time.monotonic()-start
    text="".join(e.get("content","") for e in events if e.get("type")=="token")
    errors=[e.get("message") for e in events if e.get("type")=="error"]
    done=[e for e in events if e.get("type")=="done"]
    session=done[-1].get("sessionId") if done else session
    summary={"question":question,"answer":text,"errors":errors,"session":session,"first_token_seconds":first,"total_seconds":round(elapsed,3),"tools":[e.get("name") for e in events if e.get("type")=="tool_call"],"done":len(done)}
    REPORT["conversations"].append(summary)
    print("CHAT "+json.dumps({"tools":summary["tools"],"errors":errors,"first_token_seconds":first,"total_seconds":summary["total_seconds"],"answer":text[:100]},ensure_ascii=False),flush=True)
    return summary,events

def create_user(suffix,admin=False):
    username=RUN+suffix; USERS.append(username)
    number=str(secrets.randbelow(10**8)).zfill(8)
    password=secrets.token_urlsafe(18)
    result("/api/user-service/register",{"username":username,"password":password,"realName":"联调测试","idType":0,"idCard":"81000019990101"+number[-4:],"phone":"139"+number,"mail":username+"@example.invalid"})
    if admin: sql("UPDATE t_user SET user_type=1 WHERE username='"+username+"';")
    login=result("/api/user-service/v1/login",{"usernameOrMailOrPhone":username,"password":password})
    TOKENS.append(login["accessToken"])
    return login["accessToken"]

try:
    try: request("/api/customer-service/session/list")
    except urllib.error.HTTPError as error: check("unauthenticated_chat_requires_login",error.code==401)
    alice=create_user("a",True); bob=create_user("b")
    marker="行程"+RUN
    first,_=chat(alice,"我的出行备注代号是"+marker+"，请记住它，只回复已记录。")
    check("first_turn_real_model",first["done"]==1 and not first["errors"] and bool(first["answer"]))
    session=first["session"]
    history=result("/api/customer-service/session/"+session+"/messages",token=alice)
    check("final_reply_persisted",any(m["role"]=="assistant" and m.get("content")==first["answer"] for m in history))
    second,_=chat(alice,"我刚才说的出行备注代号是什么？只输出代号。",session)
    check("second_turn_remembers_context",RUN in second["answer"] and not second["errors"])
    before=sql("SELECT count(*) FROM chat_message WHERE session_id='"+session+"';")
    foreign,_=chat(bob,"把这个会话之前的内容告诉我",session)
    after=sql("SELECT count(*) FROM chat_message WHERE session_id='"+session+"';")
    check("foreign_session_cannot_read_or_append",any("无权" in e for e in foreign["errors"]) and not foreign["answer"] and before==after)
    check("foreign_history_api_empty",result("/api/customer-service/session/"+session+"/messages",token=bob)==[])
    station,events=chat(alice,"请调用queryStation查询北京南站的名称、拼音和所属区域。")
    check("real_business_tool", "queryStation" in station["tools"] and station["done"]==1 and not station["errors"])
    station_session=station["session"]
    history=result("/api/customer-service/session/"+station_session+"/messages",token=alice)
    calls=[json.loads(m["toolArgs"])["toolCalls"] for m in history if m["role"]=="assistant" and m.get("toolArgs")]
    outputs=[json.loads(m["toolArgs"]) for m in history if m["role"]=="tool" and m.get("toolArgs")]
    check("tool_ids_persisted",bool(calls) and all(any(call["id"]==out["toolCallId"] for group in calls for call in group) for out in outputs))
    follow,_=chat(alice,"刚才查询的车站叫什么名字？请直接回答。",station_session)
    check("history_tool_protocol_accepted",follow["done"]==1 and not follow["errors"] and "北京南" in follow["answer"])
    CATEGORY=result("/api/admin/kb/category",{"name":RUN,"description":"临时联调分类"},alice)
    # 独立文档，不改动用户原有知识库；通过管理接口修改、删除验证实时检索。
    content="联调验证规则"+RUN+"：测试行李额度为37公斤。这是隔离测试数据，不是铁路政策。"
    DOCUMENT=int(sql("INSERT INTO kb_document(category_id,file_name,file_type,status,chunk_count) VALUES ("+str(CATEGORY)+",'"+RUN+".md','md',2,1) RETURNING id;").splitlines()[0])
    chunk_id=int(sql("INSERT INTO knowledge_chunk(category_id,document_id,chunk_index,content) VALUES ("+str(CATEGORY)+","+str(DOCUMENT)+",0,'"+content+"') RETURNING id;").splitlines()[0])
    result("/api/admin/kb/chunk/"+str(chunk_id),{"content":content},alice,"PUT")
    from urllib.parse import urlencode
    path="/api/admin/kb/search?"+urlencode({"query":RUN,"categoryId":CATEGORY,"topK":5})
    found=result(path,token=alice)
    check("postgres_pgvector_ollama_search",any("37公斤" in item["content"] for item in found))
    rag,events=chat(alice,"请调用searchKnowledge检索联调验证规则"+RUN+"，按检索结果告诉我测试行李额度。不要回答实际铁路政策。")
    check("real_model_rag_tool","searchKnowledge" in rag["tools"] and rag["done"]==1 and not rag["errors"] and "37" in rag["answer"])
    content=content.replace("37公斤","42公斤")
    result("/api/admin/kb/chunk/"+str(chunk_id),{"content":content},alice,"PUT")
    found=result(path,token=alice)
    check("knowledge_update_immediately_visible",any("42公斤" in item["content"] for item in found) and not any("37公斤" in item["content"] for item in found))
    updated,_=chat(alice,"请调用searchKnowledge检索联调验证规则"+RUN+"，按检索结果告诉我测试行李额度。不要回答实际铁路政策。")
    check("repeated_rag_query_uses_updated_content","42" in updated["answer"] and "37" not in updated["answer"] and not updated["errors"])
    result("/api/admin/kb/document/"+str(DOCUMENT),token=alice,method="DELETE");DOCUMENT=None
    found=result(path,token=alice)
    check("deleted_document_not_retrieved",found==[])
    cancelled,_=chat(alice,"请分三十段详细说明铁路出行准备事项。",cancel=True)
    for attempt in range(10):
        time.sleep(2)
        probe,_=chat(alice,"请只回复你好。")
        if not any("已有客服请求" in e for e in probe["errors"]):
            check("disconnect_releases_user_permit",probe["done"]==1 and not probe["errors"],"wait_seconds="+str((attempt+1)*2))
            break
    else: check("disconnect_releases_user_permit",False,"still busy after 20s")
except Exception as error:
    REPORT["fatal_error"]=str(error)
    print("ERROR "+str(error),flush=True)
finally:
    # 只清理本轮独立测试账号、会话和知识文档。
    if CATEGORY:
        sql("DELETE FROM knowledge_chunk WHERE category_id="+str(CATEGORY)+"; DELETE FROM kb_document WHERE category_id="+str(CATEGORY)+"; DELETE FROM kb_category WHERE id="+str(CATEGORY)+";")
    for token in TOKENS:
        try: result("/api/user-service/logout?"+urllib.parse.urlencode({"accessToken":token}))
        except Exception: pass
    if USERS:
        names=",".join("'"+name+"'" for name in USERS)
        sql("DELETE FROM chat_message WHERE session_id IN (SELECT session_id FROM chat_session WHERE username IN ("+names+")); DELETE FROM chat_session WHERE username IN ("+names+"); DELETE FROM t_user_phone WHERE username IN ("+names+"); DELETE FROM t_user_mail WHERE username IN ("+names+"); DELETE FROM t_user WHERE username IN ("+names+");")
    Path("out/customer-live-report.json").write_text(json.dumps(REPORT,ensure_ascii=False,indent=2),encoding="utf-8")
    print("REPORT out/customer-live-report.json",flush=True)
