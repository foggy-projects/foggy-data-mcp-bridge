"""Verify Compose binding and stage-local pagination through an isolated Runtime Launcher."""

from pathlib import Path
import argparse, hashlib, json, os, socket, sqlite3, subprocess, sys, time, urllib.request, urllib.error, urllib.parse


def main():
    if not __debug__:
        raise RuntimeError("Artifact smoke requires enabled assertions")
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
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
    (models/'model/Smoke.tm').write_text("export const model={name:'Smoke',tableName:'binding_smoke',idColumn:'id',properties:[{name:'id',column:'id',type:'INTEGER'},{name:'category',column:'category',type:'STRING'},{name:'amount',column:'amount',type:'INTEGER'}]};")
    (models/'query/Base.qm').write_text("const m=loadTableModel('Smoke');export const queryModel={name:'Base',loader:'v2',model:m,columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.id,name:'queryId',alias:'resultId'},{ref:m.category},{ref:m.amount}]}],accesses:[]};")
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

    def req(path, body=None, method=None):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(
            "http://127.0.0.1:" + str(port) + path,
            data=data,
            headers={"Content-Type": "application/json", "X-NS": "binding-smoke"},
            method=method,
        )
        try:
            with opener.open(request, timeout=30) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            return json.loads(e.read())

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


            describe=req('/api/v1/models/Base/describe',{'format':'json'})
            assert describe['success'],describe
            records['describe']=describe
            def compose(label,script,mode,code=None):
                (out/(label+'.fsscript')).write_text(script,encoding='utf-8')
                result=req('/api/v1/compose/'+mode,{'script':script})
                records[label+'-'+mode]=result
                if code:
                    assert result['success'] is False,result
                    assert result['diagnostics']['attributes']['composeErrorCode']==code,result
                    assert 'on[0]' in result['error']['message'],result
                    return result
                assert result['success'] and result['data']['valid'],result
                return result['data']['value']['plans']
            def script(base_extra='',derived='',key='right_id',left_key='left_id',final='100'):
                return "const a=dsl({model:'Base',columns:['id as left_id','category as left_label']"+base_extra+"})"+derived+";const b=dsl({model:'Base',columns:['id as right_id','amount as right_amount']});const j=a.join(b,'left',[{left:'"+left_key+"',op:'=',right:'"+key+"'}]);return {plans:j.query({columns:['left_id','left_label','right_amount'],orderBy:['left_id'],limit:"+final+"})};"
            for label,s,code in [('missing-right',script(key='missing_id'),'compose-schema-error/join/on-right-unknown-field'),('missing-left',script(left_key='missing_id'),'compose-schema-error/join/on-left-unknown-field'),('unprojected-source-name',script(key='id'),'compose-schema-error/join/on-right-unknown-field')]:
                for mode in ['validate','preview','execute']:compose(label,s,mode,code)
                checks.append(label)
            cases=[('qm-output-alias',"const a=dsl({model:'Base',columns:['queryId'],orderBy:['queryId'],limit:2});const b=dsl({model:'Base',columns:['id']});return {plans:a.join(b,'inner',[{left:'resultId',op:'=',right:'id'}]).query({columns:['id'],orderBy:['id'],limit:100})};",[1,2]),('base-limit',script(',orderBy:[\'left_id\'],limit:1'),[1]),
                   ('base-offset',script(',orderBy:[\'left_id\'],start:1,limit:1'),[2]),
                   ('base-zero',script(',orderBy:[\'left_id\'],limit:0'),[]),
                   ('base-offset-only',script(',orderBy:[\'left_id\'],start:1'),[2,3]),
                   ('unbounded',script(),[1,2,3]),
                   ('derived-limit',script(derived=".query({orderBy:['left_id'],limit:1})"),[1]),
                   ('final-limit',script(final='1'),[1]),
                   ('root-base-limit',"return {plans:dsl({model:'Base',columns:['id'],orderBy:['id'],limit:1})};",[1])]
            for label,s,expected in cases:
                validated=compose(label,s,'validate');preview=compose(label,s,'preview')
                assert validated==preview,label
                rows=compose(label,s,'execute')
                assert sorted(row.get('left_id',row.get('id')) for row in rows)==expected,(label,rows)
                checks.append(label)
            normal=req('/api/v1/query/Base/execute',{'columns':['id'],'orderBy':[{'field':'id','dir':'asc'}],'start':1,'limit':1})
            records['ordinary-query']=normal
            assert normal['success'] and len(normal['data']['items'])==1,normal
            assert normal['data']['items'][0]['id']==2,normal
            checks.append('ordinary-query-pagination-unchanged')
        finally:
            proc.terminate()
            try:proc.wait(timeout=20)
            except subprocess.TimeoutExpired:proc.kill();proc.wait(timeout=10)
            (out/'responses.json').write_text(json.dumps(records,indent=2,ensure_ascii=False),encoding='utf-8')
    result={'status':'passed','launcherSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'processStopped':proc.poll() is not None,'checks':checks}
    (out/'summary.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))

if __name__=='__main__':main()
