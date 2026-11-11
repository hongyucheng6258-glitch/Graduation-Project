# 校园演示数据

数据是可操作的虚构校园场景。发布者昵称带「·演示」，公告带「演示｜」。活动和交接时间按导入时的上海时区日期计算。物品、活动、动态和画作所用图片是生成的示意素材。

从项目根目录运行。需启动 Docker Compose，Python 环境需安装 bcrypt。

```powershell
$demoDir = Join-Path $env:LOCALAPPDATA 'Ai-campus'
New-Item -ItemType Directory -Path $demoDir -Force | Out-Null
python -X utf8 docker/scripts/seed_campus_demo.py --project ai-campus-docker --password-file (Join-Path $demoDir 'demo-student-password.txt')
python -X utf8 docker/scripts/seed_campus_demo.py --project ai-campus-docker --verify-only
```

学生演示账号为 `CAMPUSDEMO01`。首次导入生成的密码存入上面指定的本机文件。其余演示人物用于提供内容与互动关系。

脚本按学号 `CAMPUSDEMO01` 识别批次，重复执行保留现有演示记录。它会给指定的旧演示记录补齐缺失图片，判断条件包含旧演示账号、记录编号和标题。活动与报名、签到，闲置与预约、互评，失物与认领，问答与采纳均有关联。你画我猜包含历史对局及作品，实时房间由玩家创建。
