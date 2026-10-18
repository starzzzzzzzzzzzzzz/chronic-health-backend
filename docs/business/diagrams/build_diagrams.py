"""Build the three editable business diagrams (stdlib only).

Run from any directory: python3 docs/business/diagrams/build_diagrams.py
PNG previews are exported with the draw.io desktop CLI; see ../README.md.
"""
from pathlib import Path
import html
import xml.etree.ElementTree as ET

OUT = Path(__file__).resolve().parent
INK, MUTED, LINE = '#172B45', '#52647B', '#72869D'
BLUE, TEAL, GOLD, GREY = '#EAF1FF', '#E5F4EF', '#FFF3DB', '#F1F3F6'
FONT = 'PingFang SC'


class Diagram:
    def __init__(self, slug, title, subtitle, height):
        self.slug, self.height = slug, height
        self.file = ET.Element('mxfile', host='app.diagrams.net', type='device')
        page = ET.SubElement(self.file, 'diagram', id=slug, name=title)
        model = ET.SubElement(page, 'mxGraphModel', dx='1800', dy=str(height), grid='1', gridSize='10',
                              page='1', pageScale='1', pageWidth='1800', pageHeight=str(height),
                              background='#FFFFFF', adaptiveColors='auto')
        self.root = ET.SubElement(model, 'root')
        ET.SubElement(self.root, 'mxCell', id='0')
        ET.SubElement(self.root, 'mxCell', id='1', parent='0')
        self.edge_no = 0
        self.text('eyebrow', '慢病健康管理 · 当前后端业务说明 / 2026-10-18', 60, 26, 1680, 36, 18, MUTED)
        self.text('title', title, 60, 70, 1680, 65, 38, INK, bold=True)
        self.text('subtitle', subtitle, 60, 140, 1680, 40, 19, MUTED)
        self.text('footer', '当前代码快照 · local + DEMO · 39 个接口操作 · 详见同目录业务文档',
                  60, height-45, 1680, 28, 16, MUTED)

    def cell(self, id, label, x, y, w, h, style):
        c = ET.SubElement(self.root, 'mxCell', id=id, value=label,
                          style=f'html=1;fontFamily={FONT};fontColor={INK};whiteSpace=wrap;{style}', vertex='1', parent='1')
        ET.SubElement(c, 'mxGeometry', x=str(x), y=str(y), width=str(w), height=str(h), attrib={'as': 'geometry'})
        return id

    def text(self, id, label, x, y, w, h, size=22, color=INK, bold=False):
        return self.cell(id, html.escape(label), x, y, w, h,
                         f'text;align=left;verticalAlign=middle;spacing=0;fontSize={size};fontColor={color};fontStyle={1 if bold else 0};')

    def box(self, id, title, lines, x, y, w=300, h=145, color=BLUE, dashed=False, external=False):
        font = '#FFFFFF' if external else INK
        label = f'<div style="line-height:1.45;color:{font}"><b style="font-size:23px">{html.escape(title)}</b>'
        label += '<div style="font-size:18px;margin-top:9px">' + '<br>'.join(html.escape(s) for s in lines) + '</div></div>'
        return self.cell(id, label, x, y, w, h,
                         f'rounded=1;arcSize=8;align=left;verticalAlign=middle;spacing=20;fillColor={color};'
                         f'strokeColor={LINE};strokeWidth=1.2;fontSize=18;dashed={1 if dashed else 0};')

    def store(self, id, title, lines, x, y, w=500, h=155):
        label = '<b style="font-size:22px">'+html.escape(title)+'</b><br><br>'+'<br>'.join(html.escape(s) for s in lines)
        return self.cell(id, label, x, y, w, h,
                         f'shape=partialRectangle;left=1;right=0;top=1;bottom=1;align=left;spacing=18;'
                         f'fillColor={TEAL};strokeColor=#4D8F80;strokeWidth=2;fontSize=18;')

    def edge(self, source, target, label='', two_way=False, color=LINE):
        self.edge_no += 1
        c = ET.SubElement(self.root, 'mxCell', id=f'edge-{self.edge_no}', value=html.escape(label),
                          style=f'edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;fontFamily={FONT};fontSize=16;'
                          f'fontColor={INK};labelBackgroundColor=#FFFFFF;strokeColor={color};strokeWidth=2;'
                          f'endArrow=block;endFill=1;startArrow={"block" if two_way else "none"};startFill=1;',
                          edge='1', source=source, target=target, parent='1')
        ET.SubElement(c, 'mxGeometry', relative='1', attrib={'as': 'geometry'})

    def write(self):
        p = OUT / (self.slug+'.drawio')
        ET.indent(self.file)
        p.write_bytes(ET.tostring(self.file, encoding='utf-8', xml_declaration=True))
        parsed=ET.parse(p)
        cells=parsed.findall('.//mxCell')
        ids=[c.get('id') for c in cells]
        assert len(ids)==len(set(ids))
        for c in cells:
            if c.get('edge')=='1':
                assert c.get('source') in ids and c.get('target') in ids and c.find('mxGeometry') is not None
        print(p.name, len(cells), 'cells')


def business_flow():
    d=Diagram('backend-business-flow', '01 / 后端业务流程图',
              '沿箭头阅读：第一行从左向右，第二行从右向左，第三行从左向右。节点内标明操作角色。', 1480)
    d.text('phase1', 'A  形成方案：资料与依据先固定，AI 候选由人工采用', 60, 195, 1660, 32, 22, bold=True)
    cards=[
        ('patient','01 选择患者 / 读资料',['编辑者 · 授权范围内','六方面画像、来源与缺项'],60,225),
        ('draft','02 建立周期草稿',['编辑者 · 目标 / 周期','冻结 PLAN 输入快照'],400,225),
        ('ai','03 形成候选内容',['AI 作业生成 → 校验候选','也可直接手工编写草稿'],740,225),
        ('edit','04 采用 / 编辑方案',['编辑者 · AI 成功不自动采用','主目标、模块、七维建议'],1080,225),
        ('condition','05 确认执行条件',['核实主体、环境和必要来源','改正文后须重新确认'],1420,225),
        ('planreview','06 方案一审',['独立审核者 · 禁止自审','冻结方案和条件确认'],1420,555),
        ('generate','07 派生七维任务包',['后端 · 确定性规则拆解','仅从已审、选中的执行条目'],1080,555),
        ('schedule','08 确认任务 / 排程',['编辑者 · 主体 / 时段 / 负担','生成无阻断的排程预览'],740,555),
        ('taskreview','09 任务二审',['独立审核者 · 禁止自审','冻结任务包与排程预览'],400,555),
        ('pubpreview','10 核对发布衔接',['发布者 · 新建 / 保留 / 替代','NONE 或 ALL_FUTURE'],60,555),
        ('publish','11 事务发布',['后端 · 复制已审排程实例','按幂等键去重，失败回滚'],60,885),
        ('today','12 查询日任务',['患者身份 · 仅已生效任务','保留原任务的冻结内容'],400,885),
        ('feedback','13 打卡 / 补记 / 更正',['患者身份 · 一键完成或反馈','历史追加，遵守执行窗口'],740,885),
        ('assess','14 保存并评估反馈',['后端 · 完成 / 时效 / 安全','异常或停止执行建立事件'],1080,885),
        ('result','15 展示当前结果',['完成、时效和安全分开呈现','计完成模式，无质量分数'],1420,885),
    ]
    for id,title,lines,x,y in cards:
        d.box(id,title,lines,x,y,color=TEAL if id in {'planreview','taskreview','publish','result'} else BLUE)
    seq=['patient','draft','ai','edit','condition','planreview','generate','schedule','taskreview','pubpreview','publish','today','feedback','assess','result']
    for a,b in zip(seq,seq[1:]):
        d.edge(a,b, '通过' if a in {'planreview','taskreview'} else '')
    d.text('phase2','B  批准与编排：先审方案，再生成任务；二审通过后才能发布',60,510,1660,32,22,bold=True)
    d.text('phase3','C  发布后执行：正式任务实例承载打卡，反馈不反写已审方案',400,840,1320,32,22,bold=True)
    d.box('review-return','退回 / 撤回与后续修改',[
        '一审退回或撤回 → 方案草稿；二审 → 任务草稿',
        '重新修改、核实、预览并另建送审记录',
        '已批准内容需建新版本，不继承审核结论'],60,1125,520,175,GOLD)
    d.box('seven-dimensions','七维按需启用',[
        '用药 / 饮食 / 运动 / 起居 / 监测 / 中医 / 其他',
        '不强求七维都有任务；AI 成功仍需人工核对',
        '缺必要依据、条件或模板时阻断后续操作'],620,1125,520,175,GOLD)
    d.box('safety','独立保留安全事件',[
        '异常或 STOPPED → OPEN 安全事件',
        '后续改报完成也不会自动清除该事件',
        '处置 / 关闭流程尚未接入'],1180,1125,540,175,GOLD)
    d.box('boundary','当前交付边界',[
        '上述为原生后端主线；旧前端仅接入档案列表、患者输入、AI 建议三个兼容入口。真实登录、消息送达、质量评分及完整旧工作流待接入。'],
        60,1340,1660,80,GREY,True)
    d.write()


def data_flow():
    d=Diagram('backend-data-flow','02 / 后端数据流图',
              '蓝框＝处理过程；左侧开口框＝逻辑数据存储；深色框＝外部参与方；箭头标注传递的数据。',1840)
    d.box('actors','编辑者 / 独立审核者 / 患者', ['各用自己的授权身份；以下是分阶段的数据处理，不表示自动连续执行'],620,200,550,95,'#243F62',external=True)
    processes=[
        ('p1','P1  授权查询与患者资料',['读取当前有效事实与 profileRevision','缺失为未知，保留自述 / 专业确认区别'],310),
        ('p2','P2  草稿、AI 作业与人工采用',['每次生成重新冻结 PLAN 输入','模型调用在事务外；采用时校验修订'],540),
        ('p3','P3  执行条件与方案一审',['冻结方案正文、条件确认、摘要','批准 / 退回 / 撤回均保留历史'],770),
        ('p4','P4  任务派生、排程与二审',['只派生已审执行条目；另读 TASK 资料','保留来源；冻结排程与二审快照'],1000),
        ('p5','P5  发布与患者日任务',['按发布清单复制已审 occurrence','实例、替代、幂等、事件同一事务'],1230),
        ('p6','P6  打卡、补记与更正',['追加反馈历史，更新当前结果','异常事件独立保留，不反写原方案'],1460),
    ]
    for id,title,lines,y in processes:d.box(id,title,lines,620,y,550,145)
    d.edge('actors','p1','分角色请求')
    for a,b,label in [('p1','p2','资料 + 修订'),('p2','p3','已保存方案 + 输入快照'),('p3','p4','一审批准内容 + 来源'),('p4','p5','二审批准 + 排程快照'),('p5','p6','正式实例 + 执行窗口')]:d.edge(a,b,label)
    stores=[
        ('d1','D1  当前患者与事实',['patient / patient_access_grant','health_fact · 当前有效资料','legacy_archive_mapping · 档案编号'],310,'p1','读取'),
        ('d2','D2  方案与 PLAN 输入快照',['health_input_snapshot · stage=PLAN','care_plan / care_plan_version','模块、侧重、维度、条目、依据子表'],540,'p2','读写'),
        ('d3','D3  条件与一审历史',['condition_confirmation','review_submission · stage=PLAN','review_decision'],770,'p3','读写'),
        ('d4','D4  任务包、预览与二审',['task_bundle / group / variant / source','task_template / schedule_preview','TASK 输入快照；stage=TASK 审核记录'],1000,'p4','读写'),
        ('d5','D5  发布与正式任务实例',['publication_preview / publication','publication_request / execution_task','outbox_event · NOT_CONFIGURED'],1230,'p5','读写'),
        ('d6','D6  反馈、结果与安全事件',['task_feedback · 追加历史','task_result · 当前汇总','task_safety_event · OPEN 事件'],1460,'p6','读写'),
    ]
    for id,title,lines,y,p,label in stores:
        d.store(id,title,lines,60,y,500,145)
        d.edge(id,p,label,two_way=label=='读写')
    d.box('model','DeepSeek API',['接收白名单事实投影和生成要求','返回结构化 JSON 候选'],1230,430,510,125,'#243F62',external=True)
    d.edge('p2','model','JSON',True)
    d.store('job','D7  AI 候选与重试关联',['plan_generation_job','状态 / candidate / 规则版本 / 用量','legacy_recommendation_request'],1230,610,510,145)
    d.edge('p2','job','读写',True)
    d.box('snapshot','快照不会随实时资料一起改',['创建草稿、AI 生成、任务派生各留快照','采用 AI 才把候选及其快照写入草稿','一审与二审另保留送审内容快照'],1230,770,510,145,GOLD)
    d.store('taskread','D1（复用）当前患者资料',['任务派生再次读取并形成 TASK 快照','变更事实写入 nextCycleReference','已审目标与来源保持不变'],1230,1000,510,145)
    d.edge('taskread','p4','读取')
    d.box('outbox','发布事件已保存，消息未发送',['outbox_event = NOT_CONFIGURED','没有接入短信、微信或 App 推送','前端读取已生效的正式任务'],1230,1230,510,145,GOLD)
    d.box('feedbacknote','反馈不是新的患者诊断事实',['完成比例、实际时效、安全分别记录','更正追加新版本，不覆盖旧反馈','无自动诊断 / 下周期自动改方案'],1230,1460,510,145,GOLD)
    d.box('shared','贯穿写入链路的控制',[
        '机构与患者授权 + 数据模式隔离；rowVersion 防覆盖，contentRevision / hash 绑定内容；关键业务写入记录 audit_log。',
        '这是逻辑数据流图，D4 中审核表复用 D3 的物理表；完整表名与接口索引见业务文档。密钥留在服务端本地环境配置。'],60,1680,1680,100,GREY)
    d.write()


def function_map():
    d=Diagram('backend-function-map','03 / 后端功能结构图',
              '以业务职责分组：13 + 15 + 10 + 1 = 39 个接口操作。共用审核操作只计一次；横切能力不重复计数。',1480)
    d.box('root','慢病健康管理后端', ['当前原生业务链路 + 三个旧契约兼容入口'],600,210,600,110,'#243F62',external=True)
    groups=[('a','资料与方案 · 13 个操作',60),('b','审核与任务 · 15 个操作',490),('c','发布与反馈 · 10 个操作',920),('d','基础运行 · 1 个操作',1350)]
    for id,title,x in groups:
        d.cell('column-'+id,'',x-8,375,406,865,'rounded=1;arcSize=3;fillColor=#F8FAFC;strokeColor=#E3E9F0;')
        d.box(id,title,[],x,385,390,75,TEAL)
        d.edge('root',id)
    cards=[
        ('a1','患者资料 · 2',['授权患者列表','六方面摘要 / 缺项 / 冲突'],60,505,BLUE),
        ('a2','方案草稿与版本 · 4',['创建 / 读取 / 编辑 / 新版本','主目标、模块、七维与来源'],60,695,BLUE),
        ('a3','AI 方案候选 · 4',['提交 / 读取 / 历史 / 采用','DeepSeek；仅 DEMO'],60,885,GOLD),
        ('a4','旧契约兼容 · 3',['档案列表 / 患者输入 / 建议生成','动态字段与页面仍待联调'],60,1075,GOLD),
        ('b1','执行条件确认 · 2',['主体与环境、必要来源核实','保留历史，内容变更后失效'],490,505,BLUE),
        ('b2','方案一审 / 共用审核 · 5',['一审上下文与提交；共用读取','决策 / 撤回按 PLAN、TASK 分派'],490,695,BLUE),
        ('b3','七维任务包 · 3',['规则派生 / 读取 / 编排编辑','保留已审来源与执行主体'],490,885,BLUE),
        ('b4','排程与任务二审 · 5',['排程生成 / 读取；二审上下文','二审提交；任务包新版本'],490,1075,BLUE),
        ('c1','发布与版本衔接 · 4',['发布预览 / 读取 / 发布 / 结果','新建、保留、替代；事务幂等'],920,505,BLUE),
        ('c2','日任务 · 2',['患者当日或指定日期任务','任务详情 / 冻结执行窗口'],920,695,BLUE),
        ('c3','打卡与更正 · 4',['提交 / 历史 / 详情 / 追加更正','正常提交、补记、时效与安全'],920,885,BLUE),
        ('c4','执行结果处理',['计完成，不计算质量评分','OPEN 安全事件不可自动清除'],920,1075,GOLD),
        ('d1','服务健康 · 1',['GET /health','本统计不含文档与 Actuator'],1350,505,BLUE),
        ('d2','身份与范围控制',['本地显式 DEMO 身份','患者能力授权 / 禁止提交人自审'],1350,695,GOLD),
        ('d3','一致性与追溯',['快照、修订、摘要、事务与审计','AI / 发布 / 打卡幂等控制'],1350,885,BLUE),
        ('d4','开发与接口协作',['OpenAPI / Knife4j / Apifox','Flyway V1—V11 / 项目脚本'],1350,1075,BLUE),
    ]
    for id,title,lines,x,y,color in cards:
        d.box(id,title,lines,x,y,390,145,color)
    # Hierarchy uses one head-to-first edge per column; card stack denotes its subfunctions.
    for id,_,_ in groups:d.edge(id,id+'1')
    d.box('limits','虚线框：后续能力，未计入已完成范围',[
        '旧工作流与完整审核发布入口适配；管理侧重来源绑定；正式登录与真实患者接入；文献检索和专业模板；',
        '质量评分、语音图片和设备接入；实际消息发送；安全事件处置与护士工单闭环。'],60,1290,1680,125,GREY,True)
    d.write()


if __name__=='__main__':
    business_flow()
    data_flow()
    function_map()
