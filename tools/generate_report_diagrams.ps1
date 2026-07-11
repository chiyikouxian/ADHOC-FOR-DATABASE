$ErrorActionPreference = 'Stop'

$outDir = 'C:\Users\ideapad15s\Desktop\adhoc\image'
$tmpDir = Join-Path $outDir '_render'
$edge = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'

New-Item -ItemType Directory -Path $outDir -Force | Out-Null
New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null

function Escape-Xml([string]$value) {
    return [System.Security.SecurityElement]::Escape($value)
}

function Svg-Start([string]$title, [string]$subtitle) {
    $safeTitle = Escape-Xml $title
    $safeSubtitle = Escape-Xml $subtitle
    return @"
<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="900" viewBox="0 0 1600 900">
  <defs>
    <marker id="arrow" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto" markerUnits="strokeWidth"><path d="M0,0 L0,6 L9,3 z" fill="#475569"/></marker>
    <marker id="arrowBlue" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto" markerUnits="strokeWidth"><path d="M0,0 L0,6 L9,3 z" fill="#2563eb"/></marker>
    <style>
      text { font-family: "Microsoft YaHei", "Noto Sans CJK SC", sans-serif; fill: #172033; }
      .title { font-size: 38px; font-weight: 700; }
      .subtitle { font-size: 19px; fill: #64748b; }
      .box-title { font-size: 23px; font-weight: 700; }
      .box-line { font-size: 17px; fill: #475569; }
      .small { font-size: 15px; fill: #64748b; }
      .label { font-size: 17px; font-weight: 600; }
      .mono { font-family: Consolas, monospace; font-size: 16px; }
    </style>
  </defs>
  <rect width="1600" height="900" fill="#f8fafc"/>
  <rect x="0" y="0" width="1600" height="112" fill="#ffffff"/>
  <line x1="60" y1="112" x2="1540" y2="112" stroke="#dbe4ee" stroke-width="2"/>
  <text x="70" y="58" class="title">$safeTitle</text>
  <text x="72" y="91" class="subtitle">$safeSubtitle</text>
"@
}

function Svg-End([string]$note = 'FANET Mission & Telemetry Platform · 数据库课程项目') {
    $safeNote = Escape-Xml $note
    return @"
  <line x1="60" y1="850" x2="1540" y2="850" stroke="#dbe4ee" stroke-width="2"/>
  <text x="70" y="880" class="small">$safeNote</text>
</svg>
"@
}

function Box([int]$x,[int]$y,[int]$w,[int]$h,[string]$title,[string[]]$lines,[string]$fill='#ffffff',[string]$stroke='#94a3b8') {
    $safeTitle = Escape-Xml $title
    $lineSvg = ''
    $lineY = $y + 70
    foreach ($line in $lines) {
        $safeLine = Escape-Xml $line
        $lineSvg += "<text x='$($x+22)' y='$lineY' class='box-line'>$safeLine</text>"
        $lineY += 27
    }
    return @"
<g><rect x="$x" y="$y" width="$w" height="$h" rx="12" fill="$fill" stroke="$stroke" stroke-width="2"/>
<rect x="$x" y="$y" width="8" height="$h" rx="4" fill="$stroke"/>
<text x="$($x+22)" y="$($y+39)" class="box-title">$safeTitle</text>$lineSvg</g>
"@
}

function Pill([int]$x,[int]$y,[int]$w,[string]$text,[string]$fill='#e2e8f0',[string]$color='#334155') {
    $safe = Escape-Xml $text
    return "<g><rect x='$x' y='$y' width='$w' height='38' rx='19' fill='$fill'/><text x='$($x+$w/2)' y='$($y+25)' text-anchor='middle' class='label' fill='$color'>$safe</text></g>"
}

function Arrow([int]$x1,[int]$y1,[int]$x2,[int]$y2,[string]$label='',[string]$color='#475569',[bool]$dashed=$false) {
    $dash = if ($dashed) { 'stroke-dasharray="10 7"' } else { '' }
    $marker = if ($color -eq '#2563eb') { 'arrowBlue' } else { 'arrow' }
    $svg = "<line x1='$x1' y1='$y1' x2='$x2' y2='$y2' stroke='$color' stroke-width='3' $dash marker-end='url(#$marker)'/>"
    if ($label) {
        $safe = Escape-Xml $label
        $mx = [int](($x1+$x2)/2)
        $my = [int](($y1+$y2)/2)-10
        $svg += "<rect x='$($mx-72)' y='$($my-20)' width='144' height='28' rx='8' fill='#f8fafc'/><text x='$mx' y='$my' text-anchor='middle' class='small'>$safe</text>"
    }
    return $svg
}

function Save-Diagram([string]$id,[string]$fileName,[string]$title,[string]$subtitle,[string]$body) {
    if ($env:REPORT_IMAGE_ONLY) {
        $selected = $env:REPORT_IMAGE_ONLY.Split(',')
        if ($selected -notcontains $id) { return }
    }
    $htmlPath = Join-Path $tmpDir "$id.html"
    $shotPath = Join-Path $tmpDir "$id.png"
    $finalPath = Join-Path $outDir $fileName
    $svg = (Svg-Start $title $subtitle) + $body + (Svg-End)
    $html = "<!doctype html><html><head><meta charset='utf-8'><style>html,body{margin:0;width:1600px;height:900px;overflow:hidden;background:#f8fafc}</style></head><body>$svg</body></html>"
    [System.IO.File]::WriteAllText($htmlPath, $html, (New-Object System.Text.UTF8Encoding($false)))
    $uri = ([System.Uri]$htmlPath).AbsoluteUri
    $args = @('--headless','--disable-gpu','--hide-scrollbars','--allow-file-access-from-files',"--screenshot=$shotPath",'--window-size=1600,900',$uri)
    $process = Start-Process -FilePath $edge -ArgumentList $args -Wait -PassThru -WindowStyle Hidden
    if ($process.ExitCode -ne 0 -or -not (Test-Path $shotPath)) { throw "Rendering failed: $id" }
    Move-Item -LiteralPath $shotPath -Destination $finalPath -Force
}

# 01 FANET application scenario
$b = @"
<path d="M0 690 C220 560 390 640 560 560 C760 470 930 590 1110 520 C1320 435 1450 520 1600 450 L1600 850 L0 850 Z" fill="#dcefe4"/>
<path d="M0 760 C250 650 420 740 650 650 C900 560 1130 700 1600 570 L1600 850 L0 850 Z" fill="#c7dfd0"/>
<g transform="translate(1320,610)"><rect x="-48" y="30" width="96" height="90" rx="8" fill="#ffffff" stroke="#475569" stroke-width="3"/><line x1="0" y1="30" x2="0" y2="-55" stroke="#475569" stroke-width="5"/><path d="M-28 -36 Q0 -62 28 -36" fill="none" stroke="#2563eb" stroke-width="4"/><path d="M-43 -17 Q0 -58 43 -17" fill="none" stroke="#2563eb" stroke-width="3"/><text x="0" y="78" text-anchor="middle" class="label">地面站</text></g>
"@
$dronePoints = @(@(260,260),@(560,190),@(880,270),@(1160,190),@(420,460),@(820,480))
for($i=0;$i -lt $dronePoints.Count;$i++){
    $x=$dronePoints[$i][0];$y=$dronePoints[$i][1]
    $b += "<g transform='translate($x,$y)'><circle r='35' fill='#ffffff' stroke='#2563eb' stroke-width='4'/><rect x='-25' y='-9' width='50' height='18' rx='7' fill='#2563eb'/><line x1='-62' y1='-26' x2='62' y2='26' stroke='#334155' stroke-width='5'/><line x1='-62' y1='26' x2='62' y2='-26' stroke='#334155' stroke-width='5'/><circle cx='-62' cy='-26' r='18' fill='none' stroke='#0891b2' stroke-width='4'/><circle cx='62' cy='26' r='18' fill='none' stroke='#0891b2' stroke-width='4'/><circle cx='-62' cy='26' r='18' fill='none' stroke='#0891b2' stroke-width='4'/><circle cx='62' cy='-26' r='18' fill='none' stroke='#0891b2' stroke-width='4'/><text x='0' y='70' text-anchor='middle' class='label'>节点$($i+1)</text></g>"
}
$b += Arrow 300 280 520 210 '' '#2563eb' $true
$b += Arrow 600 210 840 270 '' '#2563eb' $true
$b += Arrow 920 270 1120 205 '' '#2563eb' $true
$b += Arrow 465 435 775 460 '' '#2563eb' $true
$b += Arrow 850 455 1285 615 '多跳回传' '#2563eb' $true
$b += Arrow 1185 225 1320 560 '直连地面站' '#2563eb' $true
$b += "<text x='90' y='780' class='box-title'>动态拓扑 · 协同任务 · 遥测回传 · 多跳中继</text>"
Save-Diagram '01_fanet_scene' '01_fanet应用场景图.png' '无人机自组网应用场景' '飞行节点通过动态链路协同通信，并经多跳路径连接地面站' $b

# 02 core capabilities
$b = "<circle cx='800' cy='465' r='132' fill='#e8f0ff' stroke='#2563eb' stroke-width='4'/><text x='800' y='450' text-anchor='middle' class='box-title'>FANET平台</text><text x='800' y='486' text-anchor='middle' class='box-line'>地面指挥与数据分析</text>"
$caps = @(
    @(100,175,'无人机管理',@('型号与节点','最新状态'),'#eff6ff','#2563eb'),
    @(100,545,'任务调度',@('任务分配','航点与排名'),'#f0fdf4','#16a34a'),
    @(470,650,'告警处置',@('异常检测','确认与审计'),'#fff7ed','#d97706'),
    @(940,650,'智能分析',@('NL2SQL','续航与复盘'),'#faf5ff','#7c3aed'),
    @(1190,545,'仿真工作台',@('节点与链路','启停与热更新'),'#fef2f2','#dc2626'),
    @(1190,175,'遥测与拓扑',@('时序曲线','实时链路'),'#ecfeff','#0891b2')
)
foreach($c in $caps){$b += Box $c[0] $c[1] 310 140 $c[2] $c[3] $c[4] $c[5]}
$b += Arrow 410 245 680 395 '' '#475569' $false
$b += Arrow 410 610 690 530 '' '#475569' $false
$b += Arrow 660 650 750 590 '' '#475569' $false
$b += Arrow 940 650 850 590 '' '#475569' $false
$b += Arrow 1190 610 910 530 '' '#475569' $false
$b += Arrow 1190 245 920 395 '' '#475569' $false
Save-Diagram '02_capabilities' '02_平台核心能力总览.png' '平台核心能力总览' '围绕无人机集群任务、遥测、链路和智能分析形成完整业务闭环' $b

# 03 roles and use cases
$b = Box 70 190 260 150 '管理员' @('仿真与系统配置','压测与执行计划') '#eff6ff' '#2563eb'
$b += Box 70 520 260 150 '操作员' @('任务与告警','遥测与拓扑') '#f0fdf4' '#16a34a'
$b += Box 1260 350 260 150 '无人机/模拟器' @('遥测上报','链路状态上报') '#fff7ed' '#d97706'
$useCases=@(@(520,150,'身份认证'),@(830,150,'无人机状态'),@(520,300,'任务调度'),@(830,300,'遥测分析'),@(520,450,'拓扑查询'),@(830,450,'告警处置'),@(520,600,'仿真控制'),@(830,600,'AI分析'))
foreach($u in $useCases){$b += "<ellipse cx='$($u[0])' cy='$($u[1])' rx='130' ry='55' fill='#ffffff' stroke='#64748b' stroke-width='2'/><text x='$($u[0])' y='$($u[1]+7)' text-anchor='middle' class='label'>$($u[2])</text>"}
$b += Arrow 330 260 500 180 '' '#475569' $false; $b += Arrow 330 260 500 330 '' '#475569' $false; $b += Arrow 330 590 500 470 '' '#475569' $false; $b += Arrow 330 590 500 630 '' '#475569' $false
$b += Arrow 1260 425 970 330 '' '#475569' $false; $b += Arrow 1260 425 970 480 '' '#475569' $false
Save-Diagram '03_use_case' '03_系统角色与业务用例图.png' '系统角色与业务用例' '管理员、操作员和数据源围绕八类核心用例协同工作' $b

# 04 functional structure
$b = Box 610 135 380 100 '无人机自组网平台' @('任务管理与遥测分析统一入口') '#e8f0ff' '#2563eb'
$modules=@(
    @(60,350,'认证权限',@('登录/JWT','角色授权')),
    @(310,350,'无人机管理',@('机型/节点','最新状态')),
    @(560,350,'任务管理',@('分配/航点','路径/排名')),
    @(810,350,'数据分析',@('遥测曲线','拓扑链路')),
    @(1060,350,'运维告警',@('告警处置','审计日志')),
    @(1310,350,'扩展能力',@('仿真工作台','AI分析'))
)
foreach($m in $modules){$b += Box $m[0] $m[1] 210 175 $m[2] $m[3] '#ffffff' '#64748b'; $b += Arrow 800 235 ($m[0]+105) 350 '' '#475569' $false}
$b += Pill 190 650 210 'PostgreSQL' '#dbeafe' '#1d4ed8'; $b += Pill 515 650 210 'TDengine' '#cffafe' '#0e7490'; $b += Pill 840 650 210 'Redis' '#fee2e2' '#b91c1c'; $b += Pill 1165 650 210 'MQTT / WebSocket' '#dcfce7' '#15803d'
Save-Diagram '04_function_tree' '04_系统功能结构图.png' '系统功能结构' '业务模块与底层数据能力的对应关系' $b

# 05 data classification
$b = Box 80 170 350 180 '事务型关系数据' @('用户、无人机、任务','航点、告警、审计','强约束与事务一致性') '#eff6ff' '#2563eb'
$b += Box 80 430 350 180 '高频时序数据' @('位置、电量、RSSI','源节点与目标节点链路','持续追加与时间窗口查询') '#ecfeff' '#0891b2'
$b += Box 80 690 350 120 '热点实时数据' @('最新状态、链路排名','低延迟读取与推送') '#fef2f2' '#dc2626'
$b += Box 1040 170 430 180 'PostgreSQL 16' @('外键、事务、行锁','递归CTE、窗口函数','视图、索引、触发器') '#eff6ff' '#2563eb'
$b += Box 1040 430 430 180 'TDengine 3.x' @('超级表与设备标签','INTERVAL时间窗口','DURATION 10 / KEEP 365') '#ecfeff' '#0891b2'
$b += Box 1040 690 430 120 'Redis' @('缓存、ZSet排名','Pub/Sub辅助实时更新') '#fef2f2' '#dc2626'
$b += Arrow 430 260 1040 260 '关系与事务' '#2563eb' $false; $b += Arrow 430 520 1040 520 '时序历史' '#2563eb' $false; $b += Arrow 430 750 1040 750 '热点缓存' '#2563eb' $false
Save-Diagram '05_data_storage' '05_数据分类与存储去向图.png' '数据分类与存储去向' '依据访问模式和一致性要求选择数据库，而不是使用单一存储' $b

# 06 overall architecture
$b = Box 65 170 260 140 '数据源层' @('真实无人机','Node.js仿真器') '#f0fdf4' '#16a34a'
$b += Box 420 170 260 140 '接入层' @('EMQX / MQTT','HTTP遥测接口') '#fff7ed' '#d97706'
$b += Box 770 145 360 190 'Spring Boot服务层' @('JWT与RBAC','任务/遥测/拓扑/AI服务','MyBatis手写SQL') '#eff6ff' '#2563eb'
$b += Box 1230 170 300 140 '表现层' @('Vue 3','ECharts / WebSocket') '#faf5ff' '#7c3aed'
$b += Box 230 540 310 150 'PostgreSQL 16' @('事务关系数据','最新状态与链路快照') '#eff6ff' '#2563eb'
$b += Box 645 540 310 150 'TDengine 3.x' @('telemetry','network_links') '#ecfeff' '#0891b2'
$b += Box 1060 540 310 150 'Redis' @('最新状态缓存','链路排名与发布订阅') '#fef2f2' '#dc2626'
$b += Arrow 325 240 420 240 'MQTT/HTTP' '#2563eb' $false; $b += Arrow 680 240 770 240 '消息消费' '#2563eb' $false; $b += Arrow 1130 240 1230 240 'REST/WS' '#2563eb' $false
$b += Arrow 860 335 385 540 '事务与快照' '#475569' $false; $b += Arrow 925 335 800 540 '时序写入' '#475569' $false; $b += Arrow 1010 335 1215 540 '缓存更新' '#475569' $false
Save-Diagram '06_architecture' '06_系统总体架构图.png' '系统总体架构' '设备接入、业务服务、混合存储与可视化展示协同工作' $b

# 07 backend layers
$b = Box 120 150 1360 105 '接口与通信层' @('REST Controller · WebSocket Handler · MQTT Consumer') '#f8fafc' '#64748b'
$b += Box 120 300 1360 135 '业务服务层' @('AuthService · MissionService · TelemetryService · TopologyService · AlertService · AIService') '#eff6ff' '#2563eb'
$b += Box 120 485 650 140 'PostgreSQL数据访问' @('PostgreSQL Mapper XML','事务管理器与HikariCP连接池') '#eff6ff' '#2563eb'
$b += Box 830 485 650 140 'TDengine数据访问' @('TDengine Mapper XML','独立连接池与时序SQL') '#ecfeff' '#0891b2'
$b += Box 120 690 650 105 'PostgreSQL 16' @('业务表、视图、索引、触发器') '#ffffff' '#2563eb'
$b += Box 830 690 650 105 'TDengine 3.x' @('遥测与网络链路超级表') '#ffffff' '#0891b2'
$b += Arrow 800 255 800 300 '认证与业务编排' '#2563eb' $false; $b += Arrow 500 435 450 485 '事务查询' '#475569' $false; $b += Arrow 1100 435 1150 485 '时序查询' '#475569' $false; $b += Arrow 450 625 450 690 '' '#475569' $false; $b += Arrow 1150 625 1150 690 '' '#475569' $false
Save-Diagram '07_backend_layers' '07_后端分层与双数据源结构图.png' '后端分层与双数据源结构' '关系SQL与时序SQL在配置、连接池和Mapper层保持独立' $b

# 08 dual database distribution
$b = Box 560 140 480 110 'Spring Boot应用编排' @('按数据特征路由查询与写入') '#ffffff' '#475569'
$b += Box 120 350 580 330 'PostgreSQL 16' @('users / drone_models / drones','missions / mission_assignments / waypoints','alerts / audit_log','drone_latest / network_links_snapshot','sim_scenarios / sim_scenario_*') '#eff6ff' '#2563eb'
$b += Box 900 350 580 330 'TDengine 3.x' @('telemetry超级表','标签：drone_id、model_id','network_links超级表','标签：src_drone_id、dst_drone_id','时间分区与保留策略') '#ecfeff' '#0891b2'
$b += Arrow 690 250 500 350 '事务/关系/快照' '#2563eb' $false; $b += Arrow 910 250 1100 350 '高频时序历史' '#2563eb' $false
$b += Arrow 900 700 700 700 'LAST链路快照同步' '#475569' $true
Save-Diagram '08_dual_db' '08_双数据库数据分布图.png' '双数据库数据分布' 'PostgreSQL负责强关系事务，TDengine负责高频遥测与链路历史' $b

# 09 snapshot collaboration
$b = ''
$steps=@(
    @(70,300,250,'1. 消息进入',@('MQTT / HTTP','校验时间戳与节点')),
    @(370,300,250,'2. 时序落库',@('TDengine telemetry','network_links')),
    @(670,300,250,'3. 最新值提取',@('LAST / 最近时间窗','按节点或链路分组')),
    @(970,300,250,'4. 快照上移',@('drone_latest','network_links_snapshot')),
    @(1270,300,250,'5. 实时消费',@('Redis / WebSocket','拓扑与大屏'))
)
foreach($s in $steps){$b += Box $s[0] $s[1] $s[2] 200 $s[3] $s[4] '#ffffff' '#2563eb'}
for($i=0;$i -lt 4;$i++){$b += Arrow ($steps[$i][0]+250) 400 $steps[$i+1][0] 400 '' '#2563eb' $false}
$b += "<rect x='430' y='610' width='740' height='100' rx='12' fill='#fff7ed' stroke='#d97706' stroke-width='2'/><text x='800' y='650' text-anchor='middle' class='box-title'>一致性边界</text><text x='800' y='684' text-anchor='middle' class='box-line'>任务分配保持PostgreSQL强事务；遥测快照采用最终一致性，不参与跨库分布式事务</text>"
Save-Diagram '09_snapshot' '09_跨库快照协作图.png' '跨库快照协作流程' '时序历史保留在TDengine，最新状态同步为PostgreSQL读模型' $b

# 10 realtime telemetry flow
$b = Box 65 230 230 170 '无人机/模拟器' @('位置、电量、RSSI','链路质量与状态') '#f0fdf4' '#16a34a'
$b += Box 365 230 230 170 'EMQX' @('fanet/telemetry','fanet/network_links') '#fff7ed' '#d97706'
$b += Box 665 205 270 220 '后端消费者' @('消息校验','TDengine写入','快照与告警','Redis更新') '#eff6ff' '#2563eb'
$b += Box 1005 150 250 140 '历史分析' @('TDengine','INTERVAL聚合') '#ecfeff' '#0891b2'
$b += Box 1005 360 250 140 '实时状态' @('PostgreSQL快照','Redis缓存') '#fef2f2' '#dc2626'
$b += Box 1325 230 210 170 'Vue前端' @('REST首次加载','WebSocket增量更新') '#faf5ff' '#7c3aed'
$b += Arrow 295 315 365 315 'MQTT' '#2563eb' $false; $b += Arrow 595 315 665 315 '订阅' '#2563eb' $false; $b += Arrow 935 265 1005 220 '历史写入' '#475569' $false; $b += Arrow 935 365 1005 430 '快照更新' '#475569' $false; $b += Arrow 1255 220 1325 285 'REST' '#2563eb' $false; $b += Arrow 1255 430 1325 350 'WebSocket' '#2563eb' $false
$b += "<rect x='360' y='610' width='880' height='110' rx='12' fill='#ffffff' stroke='#94a3b8' stroke-width='2'/><text x='800' y='652' text-anchor='middle' class='box-title'>先全量、后增量</text><text x='800' y='688' text-anchor='middle' class='box-line'>页面进入时读取完整快照，连接建立后仅接收状态变化，降低轮询与数据库压力</text>"
Save-Diagram '10_realtime' '10_实时遥测数据流图.png' '实时遥测数据流' 'MQTT负责设备接入，WebSocket负责浏览器增量更新' $b

# 11 JWT flow
$b = Box 70 170 240 130 '用户' @('管理员 / 操作员','提交账号与密码') '#ffffff' '#64748b'
$b += Box 390 170 260 130 '登录接口' @('校验用户与密码哈希','签发JWT') '#eff6ff' '#2563eb'
$b += Box 750 170 260 130 '客户端会话' @('保存令牌','请求携带Authorization') '#f0fdf4' '#16a34a'
$b += Box 1110 170 310 130 'JWT过滤器' @('验签、有效期、角色','建立安全上下文') '#fff7ed' '#d97706'
$b += Arrow 310 235 390 235 '登录' '#2563eb' $false; $b += Arrow 650 235 750 235 'JWT' '#2563eb' $false; $b += Arrow 1010 235 1110 235 'Bearer Token' '#2563eb' $false
$b += Box 330 500 330 170 '操作员接口' @('平台读取','任务、告警、AI分析','允许：OPERATOR / ADMIN') '#f0fdf4' '#16a34a'
$b += Box 940 500 330 170 '管理员接口' @('仿真控制、压测','执行计划、遥测写入','仅允许：ADMIN') '#fef2f2' '#dc2626'
$b += Arrow 1260 300 560 500 '角色匹配' '#475569' $false; $b += Arrow 1270 300 1100 500 '管理员权限' '#475569' $false
$b += Pill 620 745 360 'WebSocket握手同样校验JWT' '#dbeafe' '#1d4ed8'
Save-Diagram '11_jwt' '11_JWT认证与权限流程图.png' 'JWT认证与角色授权流程' '业务接口与WebSocket共享认证边界，管理员操作执行最小权限控制' $b

# 12 PostgreSQL ER
$b = Box 45 145 250 120 'users' @('PK id','username · role') '#eff6ff' '#2563eb'
$b += Box 345 145 270 135 'missions' @('PK id','FK creator_id','status · start_time') '#eff6ff' '#2563eb'
$b += Box 665 145 300 150 'mission_assignments' @('PK id','FK mission_id','FK drone_id · status') '#fff7ed' '#d97706'
$b += Box 1015 145 260 135 'drones' @('PK id','FK model_id','serial_no · status') '#f0fdf4' '#16a34a'
$b += Box 1325 145 230 120 'drone_models' @('PK id','name · max_payload') '#f0fdf4' '#16a34a'
$b += Box 345 400 270 130 'waypoints' @('PK id','FK mission_id','seq · lat · lon · alt') '#faf5ff' '#7c3aed'
$b += Box 45 400 250 130 'audit_log' @('PK id','table_name · action','changed_at') '#f8fafc' '#64748b'
$b += Box 1015 400 260 130 'alerts' @('PK id','FK drone_id','level · resolved') '#fef2f2' '#dc2626'
$b += Box 1325 400 230 130 'drone_latest' @('PK/FK drone_id','ts · battery_pct','rssi · position') '#ecfeff' '#0891b2'
$b += Box 665 650 300 140 'network_links_snapshot' @('src_drone_id','dst_drone_id','link_quality · is_active') '#ecfeff' '#0891b2'
$b += Arrow 295 205 345 205 '1:N' '#475569' $false; $b += Arrow 615 215 665 215 '1:N' '#475569' $false; $b += Arrow 965 215 1015 215 'N:1' '#475569' $false; $b += Arrow 1275 205 1325 205 'N:1' '#475569' $false
$b += Arrow 480 280 480 400 '1:N' '#475569' $false; $b += Arrow 1145 280 1145 400 '1:N' '#475569' $false; $b += Arrow 1230 280 1440 400 '1:1 最新状态' '#475569' $false
$b += "<polyline points='1015,230 990,230 990,610 965,680' fill='none' stroke='#475569' stroke-width='3' stroke-dasharray='10 7' marker-end='url(#arrow)'/><rect x='910' y='555' width='160' height='30' rx='8' fill='#f8fafc'/><text x='990' y='576' text-anchor='middle' class='small'>源/目标逻辑节点</text>"
$b += Arrow 380 280 250 400 '任务变更审计' '#475569' $true
Save-Diagram '12_er' '12_PostgreSQL核心ER图.png' 'PostgreSQL核心业务E-R图' '任务、无人机、遥测快照、告警与审计实体关系' $b

# 13 simulation ER
$b = Box 550 160 500 180 'sim_scenarios' @('PK id','FK created_by → users.id','name · status · publish_interval_ms','motion_mode · updated_at') '#eff6ff' '#2563eb'
$b += Box 160 500 520 200 'sim_scenario_drones' @('PK id','FK scenario_id','drone_no（从1开始）','model_id · initial position · parameters') '#f0fdf4' '#16a34a'
$b += Box 920 500 520 200 'sim_scenario_links' @('PK id','FK scenario_id','src_drone_id · dst_drone_id','目标0表示地面站 · quality · enabled') '#fff7ed' '#d97706'
$b += Arrow 670 340 470 500 '1:N 节点' '#475569' $false; $b += Arrow 930 340 1130 500 '1:N 链路' '#475569' $false
$b += Pill 565 760 470 '场景删除时级联清理节点与链路' '#dbeafe' '#1d4ed8'
Save-Diagram '13_sim_er' '13_仿真场景表关系图.png' '仿真场景数据模型' '场景主表统一管理节点配置和链路配置，地面站使用特殊目标0' $b

# 14 concurrency workflow
$b = Box 60 190 280 120 '请求A' @('任务1分配无人机1','几乎同时到达') '#eff6ff' '#2563eb'
$b += Box 60 520 280 120 '请求B' @('任务2分配无人机1','与请求A竞争') '#fef2f2' '#dc2626'
$b += Box 470 310 330 190 'PostgreSQL事务' @('BEGIN','SELECT ... FOR UPDATE','锁定 drones(id=1)','检查未结束分配') '#fff7ed' '#d97706'
$b += Box 940 160 300 150 '请求A获得行锁' @('不存在冲突分配','INSERT成功并COMMIT') '#f0fdf4' '#16a34a'
$b += Box 940 520 300 150 '请求B等待后重查' @('发现无人机已被占用','回滚并返回冲突') '#fef2f2' '#dc2626'
$b += Box 1320 330 210 140 '最终结果' @('仅一个请求成功','无重复占用') '#ffffff' '#475569'
$b += Arrow 340 250 470 360 '进入事务' '#2563eb' $false; $b += Arrow 340 580 470 450 '进入事务' '#2563eb' $false; $b += Arrow 800 360 940 235 '先获得锁' '#475569' $false; $b += Arrow 800 450 940 590 '等待并重查' '#475569' $false; $b += Arrow 1240 235 1320 370 '' '#475569' $false; $b += Arrow 1240 590 1320 430 '' '#475569' $false
Save-Diagram '14_concurrency' '14_任务并发分配流程图.png' '任务并发分配控制流程' '使用SELECT FOR UPDATE保证同一无人机不会被并发任务重复占用' $b

function Build-LineChart([double[]]$a,[double[]]$b,[string[]]$labels,[double]$maxY,[string]$unit,[string]$aName,[string]$bName) {
    $left=170;$top=180;$width=1250;$height=540
    $svg="<rect x='$left' y='$top' width='$width' height='$height' fill='#ffffff' stroke='#cbd5e1' stroke-width='2'/>"
    for($i=0;$i -le 5;$i++){ $y=$top+$height-($height*$i/5); $val=[Math]::Round($maxY*$i/5,0); $svg += "<line x1='$left' y1='$y' x2='$($left+$width)' y2='$y' stroke='#e2e8f0' stroke-width='2'/><text x='$($left-25)' y='$($y+6)' text-anchor='end' class='small'>$val</text>" }
    $aPts=@();$bPts=@()
    for($i=0;$i -lt $labels.Count;$i++){ $x=$left+100+$i*([double]($width-200)/($labels.Count-1)); $ya=$top+$height-($a[$i]/$maxY*$height); $yb=$top+$height-($b[$i]/$maxY*$height); $aPts += "$x,$ya"; $bPts += "$x,$yb"; $svg += "<text x='$x' y='$($top+$height+42)' text-anchor='middle' class='label'>$($labels[$i])</text><circle cx='$x' cy='$ya' r='8' fill='#2563eb'/><text x='$x' y='$($ya-18)' text-anchor='middle' class='small'>$($a[$i])</text><circle cx='$x' cy='$yb' r='8' fill='#0891b2'/><text x='$x' y='$($yb+30)' text-anchor='middle' class='small'>$($b[$i])</text>" }
    $svg += "<polyline points='$($aPts -join ' ')' fill='none' stroke='#2563eb' stroke-width='5'/><polyline points='$($bPts -join ' ')' fill='none' stroke='#0891b2' stroke-width='5'/><text x='90' y='450' transform='rotate(-90 90 450)' text-anchor='middle' class='label'>$unit</text>"
    $svg += "<line x1='520' y1='790' x2='590' y2='790' stroke='#2563eb' stroke-width='5'/><text x='610' y='797' class='label'>$aName</text><line x1='850' y1='790' x2='920' y2='790' stroke='#0891b2' stroke-width='5'/><text x='940' y='797' class='label'>$bName</text>"
    return $svg
}

$labels=@('10架','50架','100架','200架','500架')
$b=Build-LineChart @(126.3,173.9,143.8,103.5,52.5) @(160.7,175.1,169.1,151.7,106.4) $labels 200 '吞吐量（TPS）' 'PostgreSQL-only' 'TDengine-only'
Save-Diagram '15_tps' '15_PostgreSQL与TDengine吞吐量对比.png' '遥测写入吞吐量对比' '同一模拟负载、每阶段10秒；TDengine在高负载下保持更高吞吐量' $b

$b=Build-LineChart @(10,7,9,12,24) @(7,7,7,8,12) $labels 30 'P95延迟（ms）' 'PostgreSQL-only' 'TDengine-only'
Save-Diagram '16_p95' '16_PostgreSQL与TDengine_P95延迟对比.png' '遥测写入P95延迟对比' '500架无人机场景下，TDengine P95为12ms，PostgreSQL为24ms' $b

Remove-Item -LiteralPath $tmpDir -Recurse -Force

Add-Type -AssemblyName System.Drawing
$results = foreach($file in Get-ChildItem -LiteralPath $outDir -Filter '*.png' | Sort-Object Name) {
    $img=[System.Drawing.Image]::FromFile($file.FullName)
    $item=[PSCustomObject]@{Name=$file.Name;Width=$img.Width;Height=$img.Height;Bytes=$file.Length}
    $img.Dispose()
    $item
}
$results | Format-Table -AutoSize
