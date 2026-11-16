import os, json, subprocess, shutil
from pathlib import Path
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel
app=FastAPI(title="Rui Cloud APK Worker")
ROOT=Path(os.getenv("JOB_DATA","/data/jobs")); ROOT.mkdir(parents=True,exist_ok=True)
SECRET=os.getenv("WORKER_SECRET","change-this-worker-secret")
class Build(BaseModel):
    job_id:str
    files:dict[str,str]
    owner:str=""
@app.get("/health")
def health(): return {"status":"ok","worker":"android-gradle"}
@app.post("/build")
def build(body:Build, x_worker_secret:str|None=Header(default=None)):
    if x_worker_secret!=SECRET: raise HTTPException(403,"forbidden")
    if not body.job_id.isalnum() or len(body.job_id)>32: raise HTTPException(400,"bad job id")
    job=ROOT/body.job_id
    if job.exists(): shutil.rmtree(job)
    project=job/"project"; project.mkdir(parents=True)
    (job/"status.json").write_text(json.dumps({"status":"building","stage":"preparing"}))
    for name,content in body.files.items():
        p=Path(name)
        if p.is_absolute() or ".." in p.parts or len(content)>500000: continue
        target=project/p
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text(content,encoding="utf-8")
    required=["settings.gradle.kts","build.gradle.kts","app/build.gradle.kts","app/src/main/AndroidManifest.xml"]
    if not all((project/x).is_file() for x in required):
        (job/"status.json").write_text(json.dumps({"status":"failed","error":"缺少必要 Gradle 檔案"}))
        return {"status":"failed"}
    env=os.environ.copy(); env["ANDROID_HOME"]=env.get("ANDROID_HOME","/opt/android-sdk")
    (project/"local.properties").write_text(f"sdk.dir={env['ANDROID_HOME']}\n")
    cmd=["gradle","assembleDebug","--no-daemon","--stacktrace"]
    try:
        result=subprocess.run(cmd,cwd=project,env=env,text=True,capture_output=True,timeout=900)
        if result.returncode!=0:
            (job/"status.json").write_text(json.dumps({"status":"failed","stage":"gradle","error":(result.stderr+"\n"+result.stdout)[-12000:]}))
            return {"status":"failed"}
        candidates=list(project.glob("**/outputs/apk/debug/*.apk"))
        if not candidates: raise RuntimeError("Gradle 成功但沒有找到 debug APK")
        shutil.copy2(candidates[0],job/"app-debug.apk")
        (job/"status.json").write_text(json.dumps({"status":"success","stage":"complete","apk":"app-debug.apk","size":(job/"app-debug.apk").stat().st_size}))
        return {"status":"success","size":(job/"app-debug.apk").stat().st_size}
    except subprocess.TimeoutExpired:
        (job/"status.json").write_text(json.dumps({"status":"failed","error":"Gradle 編譯逾時（15 分鐘）"}))
        return {"status":"failed"}
    except Exception as exc:
        (job/"status.json").write_text(json.dumps({"status":"failed","error":str(exc)}))
        return {"status":"failed"}
