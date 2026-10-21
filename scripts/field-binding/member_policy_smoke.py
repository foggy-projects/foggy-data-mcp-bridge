"""Verify member permissions, Viewer bindings and detached identities on an isolated Launcher."""

from pathlib import Path
import argparse, hashlib, json, os, socket, sqlite3, subprocess, sys, time, urllib.request, urllib.error, urllib.parse


def main():
    if not __debug__:
        raise RuntimeError("Artifact smoke requires enabled assertions")
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--cli-source", type=Path, help="Optional CLI src directory for end-to-end members list")
    args = parser.parse_args()
    jar = args.jar.resolve()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    models = out / "models"
    (models / "model").mkdir(parents=True)
    (models / "query").mkdir()
    db = out / "data.sqlite"
    with sqlite3.connect(db) as c:
        c.execute(
            "CREATE TABLE binding_smoke(id INTEGER PRIMARY KEY,category TEXT,amount INTEGER,secret INTEGER)"
        )
        c.executemany(
            "INSERT INTO binding_smoke VALUES(?,?,?,?)",
            [(1, "A", 10, 300), (2, "A", 20, 100), (3, "B", 40, 200)],
        )
        oracle = dict(
            c.execute(
                "SELECT category,SUM(amount) FROM binding_smoke GROUP BY category"
            )
        )
        average_oracle = c.execute("SELECT AVG(amount) FROM binding_smoke").fetchone()[
            0
        ]
        c.executescript("""
            CREATE TABLE member_artist(id INTEGER PRIMARY KEY, name TEXT);
            CREATE TABLE member_album(id INTEGER PRIMARY KEY, artist_id INTEGER, title TEXT);
            CREATE TABLE member_line(id INTEGER PRIMARY KEY, album_id INTEGER);
            INSERT INTO member_artist VALUES(1,'First artist'),(2,'Second artist'),(3,'Unreferenced artist');
            INSERT INTO member_album VALUES(11,1,'First album'),(12,2,'Second album');
            INSERT INTO member_line VALUES(1,11);
        """)
    (models / "model/Smoke.tm").write_text(
        "export const model={name:'Smoke',tableName:'binding_smoke',idColumn:'id',properties:[{name:'id',column:'id',type:'INTEGER'},{name:'category',column:'category',type:'STRING'},{name:'secret',column:'secret',type:'INTEGER'}],measures:[{name:'amount',column:'amount',type:'NUMBER',aggregation:'sum'},{name:'meanAmount',column:'amount',type:'NUMBER',aggregation:'avg'}]};"
    )

    def qm(name, items, extra=""):
        (models / "query" / (name + ".qm")).write_text(
            "const m=loadTableModel('Smoke');export const queryModel={name:'"
            + name
            + "',loader:'v2',model:m,columnGroups:[{caption:'Fields',items:["
            + items
            + "]}],accesses:[]"
            + extra
            + "};"
        )

    base = "{ref:m.id},{ref:m.category},{ref:m.secret},{ref:m.amount}"
    qm("Base", base)
    qm(
        "Split",
        "{ref:m.category,name:'groupKey',alias:'groupOut'},{ref:m.amount,name:'revenue',alias:'revOut'}",
    )
    qm(
        "Average",
        "{ref:m.category,name:'groupKey',alias:'groupOut'},{ref:m.meanAmount,name:'meanSales',alias:'avgOut'}",
    )
    qm(
        "Formula",
        base + ",{name:'calc',alias:'calcOut',formula:'amount * 2',type:'NUMBER'}",
    )
    qm(
        "Window",
        base
        + ",{name:'ranked',alias:'rankOut',formula:'ROW_NUMBER()',type:'INTEGER',windowOrderBy:[{field:'secret',dir:'ASC'}]}",
    )
    qm(
        "Restricted",
        base
        + ",{name:'ranked',formula:'ROW_NUMBER()',type:'INTEGER',windowOrderBy:[{field:'secret',dir:'ASC'}]}",
        ",fieldPermissions:{hiddenFields:[{fields:['secret']}]}",
    )
    qm(
        "Chain",
        base
        + ",{name:'firstCalc',formula:'amount * 2',type:'NUMBER'},{name:'secondCalc',formula:'firstCalc + 1',type:'NUMBER'}",
    )
    (models / "model/MemberSmoke.tm").write_text("""
        export const model={name:'MemberSmoke',tableName:'member_line',idColumn:'id',
        properties:[{name:'lineId',column:'id',type:'INTEGER'}],dimensions:[
          {name:'album',tableName:'member_album',foreignKey:'album_id',primaryKey:'id',captionColumn:'title',dimensions:[
            {name:'artist',tableName:'member_artist',foreignKey:'artist_id',primaryKey:'id',captionColumn:'name'}]}],measures:[]};
    """)
    for name, extra in [("Members", ""), ("HiddenMembers", ",fieldPermissions:{hiddenFields:[{fields:['artistName']}]}")]:
        (models / "query" / (name + ".qm")).write_text(
            "const m=loadTableModel('MemberSmoke');export const queryModel={name:'" + name
            + "',loader:'v2',model:m,columnGroups:[{caption:'Fields',items:["
            + "{ref:m.album.artist$id,name:'artistKey',alias:'idOut'},"
            + "{ref:m.album.artist$caption,name:'artistName',alias:'nameOut'}]}],accesses:[]" + extra + "};"
        )
    def member_qm(name, extra="", tm="MemberSmoke"):
        text="const m=loadTableModel('"+tm+"');export const queryModel={name:'"+name+"',loader:'v2',model:m,columnGroups:[{caption:'Fields',items:[{ref:m.album.artist$id,name:'artistKey'},{ref:m.album.artist$caption,name:'artistName'}]}],accesses:[]"+extra+"};"
        (models/'query'/(name+'.qm')).write_text(text)
    member_qm('InvalidVisible',",memberPermissions:[{dimension:'artistKey',patch:{visibleColumns:['noSuchField']}}]")
    member_qm('OnlyId',",memberPermissions:[{dimension:'artistKey',patch:{visibleColumns:['id']}}]")
    member_qm('ChildPatch',",memberPermissions:[{dimension:'artistKey',patch:{forcedSlice:[{field:'id',op:'!=',value:2}]}}]")
    member_qm('AuthMember',",modelPermissions:{mode:'resolver',resolver:(ctx)=>{return {allow:ctx.authorization != null};}}")
    with sqlite3.connect(db) as c:
        c.execute('ALTER TABLE member_artist ADD COLUMN parent_id INTEGER')
        c.execute('UPDATE member_artist SET parent_id=1 WHERE id=2')
        c.executescript('CREATE TABLE artist_closure(parent_id INTEGER,artist_id INTEGER,distance INTEGER); INSERT INTO artist_closure VALUES(1,1,0),(2,2,0),(3,3,0),(1,2,1);')
    tm=(models/'model/MemberSmoke.tm').read_text().replace("name:'MemberSmoke'","name:'HierModel'").replace("captionColumn:'name'","captionColumn:'name',closureTableName:'artist_closure',parentKey:'parent_id',childKey:'artist_id'")
    (models/'model/HierModel.tm').write_text(tm)
    member_qm('NoHierarchy',",memberPermissions:[{dimension:'artistKey',patch:{hierarchyEnabled:false}}]",'HierModel')
    member_qm('HierarchyAllowed','', 'HierModel')
    collision_live=out/'collision-live';collision_candidate=out/'collision-candidate'
    for directory in [collision_live,collision_candidate]:
        (directory/'query').mkdir(parents=True)
        (directory/'query/Collision.qm').write_text("const m=loadTableModel('MemberSmoke');export const queryModel={name:'Collision',loader:'v2',model:m,columnGroups:[{items:[{ref:m.lineId}]}],accesses:[]};")
    tm_candidate=out/'tm-candidate';(tm_candidate/'model').mkdir(parents=True)
    (tm_candidate/'model/MemberSmoke.tm').write_text((models/'model/MemberSmoke.tm').read_text())
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    config = {
        "server": {"address": "127.0.0.1", "port": port},
        "spring": {
            "profiles": {"active": "lite"},
            "datasource": {"url": "jdbc:sqlite:" + str(db)},
            "sql": {"init": {"mode": "never"}},
        },
        "foggy": {
            "auth": {"mode": "DISABLED"},
            "demo": {"enabled": False},
            "bundle": {
                "external": {
                    "enabled": True,
                    "bundles": [
                        {
                            "name": "field-binding-smoke",
                            "namespace": "binding-smoke",
                            "path": models.as_uri(),
                            "watch": False,
                        }
                    ],
                }
            },
            "runtime-api": {
                "enabled": True,
                "security-mode": "none-dev-test-only",
                "bundle-registry": {"path": str(out / "bundles.json")},
                "datasource-registry": {"path": str(out / "datasources.json")},
                "authoring-workspaces": {
                    "path": str(out / "workspaces"),
                    "published-bundles-path": str(out / "published"),
                },
            },
            "mcp": {"audit": {"enabled": False}},
            "data-viewer": {
                "cache": {
                    "storage": "sqlite",
                    "sqlite-path": str(out / "viewer.sqlite"),
                },
                "list-preset": {
                    "storage": "file",
                    "file-path": str(out / "presets.json"),
                },
            },
        },
    }
    env = os.environ.copy()
    env["SPRING_APPLICATION_JSON"] = json.dumps(config)
    env.pop("OPENAI_API_KEY", None)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    records = {}
    checks = []

    def req(path, body=None, method=None, authorization=None):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(
            "http://127.0.0.1:" + str(port) + path,
            data=data,
            headers={"Content-Type": "application/json", "X-NS": "binding-smoke", **({"Authorization": authorization} if authorization else {})},
            method=method,
        )
        try:
            with opener.open(request, timeout=30) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            return json.loads(e.read())

    def query(name, payload, mode="execute", success=True):
        result = req(
            "/api/v1/query/" + name + "/" + mode,
            {"payload": payload, "includeSql": True} if mode == "explain" else payload,
        )
        records[name + "-" + mode + "-" + str(len(records))] = result
        assert result.get("success") == success, result
        return result.get("data")

    def pivot(metric, top=False):
        return {
            "pivot": {
                "rows": [
                    dict(
                        field="groupKey",
                        **({"limit": 1, "orderBy": ["-" + metric]} if top else {})
                    )
                ],
                "metrics": [metric],
                "outputFormat": "grid",
                "options": {"grandTotal": False},
            }
        }

    with (out / "launcher.log").open("wb") as log:
        proc = subprocess.Popen(
            ["java", "-Dfile.encoding=UTF-8", "-jar", str(jar)],
            env=env,
            stdout=log,
            stderr=log,
        )
        try:
            for _ in range(120):
                if proc.poll() is not None:
                    raise RuntimeError("launcher exited")
                try:
                    req("/readyz")
                    break
                except Exception:
                    time.sleep(1)
            else:
                raise RuntimeError("launcher readiness timeout")
            source = {
                "name": "smoke-sqlite",
                "type": "sqlite",
                "jdbcUrl": "jdbc:sqlite:" + str(db),
                "replace": True,
            }
            assert req("/api/v1/datasources", source, "POST")["success"]
            assert req(
                "/api/v1/namespaces/binding-smoke/datasource",
                {"dataSource": "smoke-sqlite"},
                "PUT",
            )["success"]
            def probe(key,path,payload=None,authorization=None,method=None):
                result=req(path,payload,method,authorization)
                records[key]=result
                print(key, "success=" + str(result.get("success", result.get("code"))), flush=True)
                return result
            def execute(key,name,payload):
                return probe(key,'/api/v1/query/'+urllib.parse.quote(name,safe='')+'/execute',payload)
            for name in ['InvalidVisible','OnlyId','ChildPatch']:
                probe(name,'/api/v1/members/'+name+'/artistKey',{})
            execute('child-via-parent','ChildPatch#album',{'columns':['id','caption','artist$id','artist$caption']})
            hierarchy={'field':'id','op':'selfAndDescendantsOf','value':1}
            execute('hierarchy-allowed','HierarchyAllowed#album$artist',{'columns':['id','caption'],'slice':[hierarchy]})
            execute('hierarchy-snake','NoHierarchy#album$artist',{'columns':['id','caption'],'slice':[dict(hierarchy,op='self_and_descendants_of')]})
            execute('hierarchy-top','NoHierarchy#album$artist',{'columns':['id','caption'],'slice':[hierarchy]})
            execute('hierarchy-and','NoHierarchy#album$artist',{'columns':['id','caption'],'slice':[{'$and':[hierarchy]}]})
            execute('hierarchy-external','NoHierarchy#album$artist',{'columns':['id','caption'],'extData':{'forcedSlice':[hierarchy]}})
            for field in ['album$artist$id','artistKey']:
                probe('viewer-'+field,'/data-viewer/api/members/query',{'qmModel':'Members','fieldName':field})
            probe('auth-runtime','/api/v1/members/AuthMember/artistKey',{},'Bearer audit-only')
            probe('auth-viewer','/data-viewer/api/members/query',{'qmModel':'AuthMember','fieldName':'artistKey','selectedValues':[2]},'Bearer audit-only')
            probe('tm-collision','/api/v1/models/validate',{'path':str(tm_candidate),'includeStackTrace':True})
            probe('register-tm-collision','/api/v1/bundles',{'name':'tm-collision','namespace':'binding-smoke','path':str(tm_candidate),'watch':False},method='POST')
            probe('query-subdirectory','/api/v1/models/validate',{'path':str(models/'query')})
            probe('register-reserved','/api/v1/bundles',{'name':'runtime-validation-binding-smoke','namespace':'binding-smoke','path':str(collision_live),'watch':False},method='POST')
            probe('collision-reserved-name','/api/v1/models/validate',{'path':str(collision_candidate),'includeStackTrace':True})
            probe('remove-reserved','/api/v1/bundles/runtime-validation-binding-smoke',method='DELETE')
            probe('register-normal','/api/v1/bundles',{'name':'normal-collision','namespace':'binding-smoke','path':str(collision_live),'watch':False},method='POST')
            probe('collision-normal-name','/api/v1/models/validate',{'path':str(collision_candidate),'includeStackTrace':True})
            assert not records['InvalidVisible']['success']
            assert records['OnlyId']['success']
            assert all(set(row)=={'id'} for row in records['OnlyId']['data']['items'])
            assert {row['id'] for row in records['child-via-parent']['data']['items']}=={11}
            for key in ['hierarchy-snake','hierarchy-top','hierarchy-and','hierarchy-external','tm-collision',
                        'register-tm-collision','collision-reserved-name','collision-normal-name']:
                assert records[key]['success'] is False, key
            assert records['hierarchy-allowed']['success']
            assert records['query-subdirectory']['success']
            assert records['auth-runtime']['success']
            assert records['auth-viewer']['code']==200
            assert [row['value'] for row in records['auth-viewer']['data']['selectedItems']]==[2]
            for field in ['album$artist$id','artistKey']:
                viewer=records['viewer-'+field]
                assert viewer['code']==200
                assert viewer['data']['selectionFieldName']=='artistKey'
                assert viewer['data']['displayFieldName']=='artistName'
                assert {row['value'] for row in viewer['data']['items']}=={1,2,3}
            assert execute('viewer-selection-roundtrip','Members',{'columns':['artistKey'],
                'slice':[{'field':records['viewer-artistKey']['data']['selectionFieldName'],'op':'=','value':1}]})['success']
            assert probe('viewer-anonymous-denied','/data-viewer/api/members/query',{'qmModel':'AuthMember','fieldName':'artistKey'})['code']!=200
            for label,condition in [('expr',{'$expr':"caption == 'First artist'"}),
                                    ('field',{'field':'id','op':'!=','value':{'$field':'caption'}})]:
                assert execute('hidden-'+label,'HiddenMembers#album$artist',{'columns':['id'],'slice':[condition]})['success'] is False
            assert execute('hidden-external','HiddenMembers#album$artist',{'columns':['id'],
                'extData':{'forcedSlice':[{'field':'caption','op':'=','value':'First artist'}]}})['success'] is False
            for op,value,expected in [('childrenOf',1,{2}),('descendantsOf',1,{2}),('ancestorsOf',2,{1}),
                                      ('selfAndDescendantsOf',1,{1,2}),('selfAndAncestorsOf',2,{1,2})]:
                result=execute('member-'+op,'HierarchyAllowed#album$artist',{'columns':['id','caption'],
                    'slice':[{'field':'id','op':op,'value':value}]})
                assert result['success'] and {row['id'] for row in result['data']['items']}==expected, op
            # A runtime-managed bundle replacement publishes a new generation. The same Viewer
            # service instance must observe renamed query fields and changed hierarchy policy.
            refresh=out/'refresh';refresh.mkdir()
            refresh_qm=refresh/'ViewerRefresh.qm'
            old=(models/'query/NoHierarchy.qm').read_text().replace("name:'NoHierarchy'","name:'ViewerRefresh'")
            refresh_qm.write_text(old)
            assert probe('register-refresh','/api/v1/bundles',{'name':'viewer-refresh','namespace':'binding-smoke',
                'path':str(refresh),'watch':False},method='POST')['success']
            before=probe('viewer-before-refresh','/data-viewer/api/members/query',{'qmModel':'ViewerRefresh','fieldName':'album$artist$id'})
            assert before['code']==200 and not before['data'].get('hierarchical',False)
            refresh_qm.write_text(old.replace('artistKey','freshKey').replace('artistName','freshName').replace('hierarchyEnabled:false','hierarchyEnabled:true'))
            assert probe('replace-refresh','/api/v1/bundles/viewer-refresh',{'name':'viewer-refresh','namespace':'binding-smoke',
                'path':str(refresh),'watch':False},method='PUT')['success']
            after=probe('viewer-after-refresh','/data-viewer/api/members/query',{'qmModel':'ViewerRefresh','fieldName':'album$artist$id'})
            assert after['code']==200 and after['data']['hierarchical'] is True
            assert after['data']['selectionFieldName']=='freshKey' and after['data']['displayFieldName']=='freshName'
            checks.extend(['invalid-visible-fail-closed','child-policy-inheritance','hierarchy-final-guard',
                'viewer-nested-renamed-binding','viewer-auth-main-and-selected','viewer-selection-roundtrip',
                'viewer-current-generation-refresh','nested-hierarchy-five-operators','detached-tm-conflict',
                'detached-name-collision','detached-subdirectory-identity'])
        finally:
            (out / "records.json").write_text(
                json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8"
            )
            proc.terminate()
            try:
                proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait()
    (out / "records.json").write_text(
        json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    result = {
        "status": "passed",
        "checks": checks,
        "jarSha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "processStopped": True,
    }
    (out / "summary.json").write_text(json.dumps(result, indent=2))
    print(json.dumps(result))


if __name__ == "__main__":
    main()
