"""Generate schema-backed native ER diagrams and a UML use-case diagram.

Input: ../database-schema.snapshot.json (metadata only).
Requires Graphviz dot for ER layout; PNG export uses the draw.io desktop CLI.
"""
import html
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
from build_diagrams import Diagram, BLUE, TEAL, GOLD, GREY, INK, LINE, MUTED

OUT = Path(__file__).resolve().parent
SCHEMA = json.loads((OUT.parent / 'database-schema.snapshot.json').read_text())
TABLES = SCHEMA['tables']
NAMES = {
 'patient':'患者', 'patient_access_grant':'患者能力授权', 'health_fact':'患者事实',
 'health_input_snapshot':'输入快照', 'legacy_archive_mapping':'旧档案编号映射',
 'care_plan':'方案家族', 'care_plan_version':'方案版本', 'care_plan_module':'方案模块',
 'plan_management_focus':'管理侧重', 'care_plan_dimension':'七维决策', 'care_plan_item':'方案条目',
 'plan_item_basis':'条目依据', 'condition_confirmation':'执行条件确认',
 'review_submission':'送审记录', 'review_decision':'审核决定', 'task_template':'任务模板',
 'task_bundle':'任务包版本', 'task_group':'任务组', 'task_group_variant':'任务变体',
 'task_group_source':'任务来源', 'schedule_preview':'排程预览',
 'publication_preview':'发布预览', 'publication':'发布记录', 'publication_request':'发布幂等记录',
 'execution_task':'正式日任务', 'outbox_event':'待通知事件', 'task_feedback':'反馈历史',
 'task_result':'当前反馈结果', 'task_safety_event':'安全事件', 'plan_generation_job':'AI 生成作业',
 'legacy_recommendation_request':'旧建议幂等记录', 'audit_log':'审计日志', 'app_metadata':'应用元数据',
}
DOMAINS = [
 ('er-01-patients-plans','资料与方案', ['patient','patient_access_grant','health_fact','health_input_snapshot','legacy_archive_mapping',
  'care_plan','care_plan_version','care_plan_module','plan_management_focus','care_plan_dimension','care_plan_item','plan_item_basis']),
 ('er-02-reviews-tasks','确认、审核与任务', ['condition_confirmation','review_submission','review_decision','task_template',
  'task_bundle','task_group','task_group_variant','task_group_source','schedule_preview']),
 ('er-03-publication-feedback','发布与反馈', ['publication_preview','publication','publication_request','execution_task',
  'outbox_event','task_feedback','task_result','task_safety_event']),
 ('er-04-ai-support','AI 与支撑', ['plan_generation_job','legacy_recommendation_request','audit_log','app_metadata']),
]
BUSINESS = {
 'patient':['name','profile_revision'], 'patient_access_grant':['subject_id','capability'],
 'health_fact':['axis_code','confirmation_status'], 'health_input_snapshot':['stage','profile_revision'],
 'care_plan':['plan_no','status'], 'care_plan_version':['version_no','status'],
 'care_plan_module':['module_code'], 'plan_management_focus':['primary_subtype_code'],
 'care_plan_dimension':['dimension_code','decision'], 'care_plan_item':['execution_goal','is_selected'],
 'condition_confirmation':['condition_key','status'], 'review_submission':['stage','status'],
 'review_decision':['decision'], 'task_template':['template_code','review_status'],
 'task_bundle':['version_no','status'], 'task_group':['dimension_code','task_class'],
 'task_group_variant':['variant_key'], 'task_group_source':['module_code'],
 'schedule_preview':['bundle_revision','preview_hash'], 'publication_preview':['bundle_revision','preview_hash'],
 'publication':['effective_at','status'], 'publication_request':['idempotency_key'],
 'execution_task':['execution_date','occurrence_key'], 'outbox_event':['event_type','status'],
 'task_feedback':['previous_feedback_id','version_no'], 'task_result':['assessment_json'],
 'task_safety_event':['status'], 'plan_generation_job':['status','retry_of_job_id'],
 'legacy_recommendation_request':['request_key'], 'audit_log':['target_type','target_id'],
 'app_metadata':['metadata_value'],
}


def relations(owned):
    return [(child, name, fk) for child in owned for name, fk in TABLES[child]['foreignKeys'].items()]


def cardinality(child, fk):
    cols = {c['name']: c for c in TABLES[child]['columns']}
    optional = any(cols[c]['nullable'] for c in fk['columns'])
    # V4's CHECK narrows this nullable column to a mandatory PLAN target.
    if child == 'condition_confirmation' and 'plan_version_id' in fk['columns']:
        optional = False
    unique = any(set(key).issubset(fk['columns']) for key in TABLES[child]['uniqueKeys'].values())
    return ('|o' if optional else '||'), ('o|' if unique else 'o{')


def er_source(slug, title, owned, selected=None, compact=False):
    links = selected if selected is not None else relations(owned)
    referenced = sorted({fk['parent'] for _,_,fk in links} - set(owned))
    native_er(slug, title, owned, referenced, links, compact)
    return {'slug': slug, 'owned': owned, 'references': referenced, 'foreignKeys': [key for _,key,_ in links]}


def native_er(slug, title, owned, referenced, links, compact):
    """Use Graphviz's layout/routing output, then write editable native ER cells.

    This avoids the Mermaid importer routing connectors through entity tables.
    Coordinates and edge paths are computed by dot, not hand-written waypoints.
    """
    node_data = {}
    graph = ['digraph ER {', 'graph [rankdir=TB, nodesep=0.65, ranksep=0.8, splines=polyline, pad=0.1];',
             'node [shape=box, fixedsize=true, label=""];',
             'edge [fontname="Arial", fontsize=12, arrowsize=0.9];']
    for name in owned + referenced:
        table = TABLES[name]
        is_ref = name in referenced
        pk = table['uniqueKeys']['PRIMARY']
        used_fks = [fk for child,_,fk in links if child == name] if compact else list(table['foreignKeys'].values())
        fk_cols = {col for fk in used_fks for col in fk['columns']}
        wanted = set(pk) if is_ref else set(pk) | fk_cols | set(BUSINESS.get(name, []))
        if not is_ref and any(c['name']=='org_id' for c in table['columns']): wanted.add('org_id')
        if compact: wanted = set(pk) | (fk_cols-{'org_id'}) | set(BUSINESS.get(name, [])[:1])
        rows=[]
        for col in table['columns']:
            if col['name'] not in wanted: continue
            tags = (['PK'] if col['name'] in pk else []) + (['FK'] if col['name'] in fk_cols and not is_ref else [])
            nullable = bool(col['nullable']) and not (name=='condition_confirmation' and col['name']=='plan_version_id')
            label = col['name'] + (' ?' if nullable else '')
            rows.append((label, '/'.join(tags), col['type'].split(' ')[0]))
        width = max(350, len(name)*8.5+35, max(len(a)*8.2+len(b)*8+len(c)*7.8+50 for a,b,c in rows))
        height = 100 + 30*len(rows)
        node_data[name] = (width,height,rows,is_ref)
        graph.append(f'"{name}" [width={width/72:.4f}, height={height/72:.4f}];')
    for i,(child,key,fk) in enumerate(links):
        label = ','.join(c for c in fk['columns'] if c!='org_id')
        graph.append(f'"{fk["parent"]}" -> "{child}" [id="rel-{i}", label="{label}"];')
    graph.append('}')
    layout=json.loads(subprocess.run(['dot','-Tjson'],input='\n'.join(graph),text=True,capture_output=True,check=True).stdout)
    bounds=[float(v) for v in layout['bb'].split(',')]
    width,height=max(1500,bounds[2]+120),bounds[3]+330
    d=Diagram(slug, title, '物理 ER · 乌鸦脚表示基数 · 非标识外键以虚线连接 · 每张表仅展示关系字段与部分业务字段', height)
    model=d.file.find('.//mxGraphModel')
    model.set('pageWidth',str(width));model.set('dx',str(width))
    for c in d.root.findall('mxCell'):
        if c.get('id') in ('eyebrow','title','subtitle','footer'):
            c.find('mxGeometry').set('width',str(width-120))
    d.root.find("mxCell[@id='title']").set('style',d.root.find("mxCell[@id='title']").get('style').replace('fontSize=38','fontSize=30'))
    by_id={}
    for obj in layout['objects']:
        name=obj['name'];by_id[obj['_gvid']]=name
        w,h,rows,is_ref=node_data[name]
        x,y=[float(v) for v in obj['pos'].split(',')]
        label=f'<div style="font-family:PingFang SC,Arial;line-height:1.3"><div style="font-size:18px;margin-bottom:3px"><b>{"引用 · " if is_ref else ""}{NAMES[name]}</b></div><div style="font-size:15px;margin-bottom:12px">{name}</div>'
        label+='<table style="border-collapse:collapse;width:100%;font-size:14px;line-height:26px">'
        for field,tags,typ in rows:
            label+=f'<tr><td style="text-align:left;border-top:1px solid #D5DFEA;padding-right:8px">{html.escape(field)}</td><td style="border-top:1px solid #D5DFEA;padding-right:10px;color:#255D9C"><b>{tags}</b></td><td style="border-top:1px solid #D5DFEA;text-align:right;color:#52647B">{typ}</td></tr>'
        label+='</table></div>'
        d.cell(name,label,x-w/2+60,bounds[3]-y-h/2+210,w,h,
               f'rounded=0;fillColor={GREY if is_ref else BLUE};strokeColor=#72869D;strokeWidth=1.3;spacing=14;align=left;verticalAlign=top;fontSize=14;')
    for edge in layout.get('edges',[]):
        i=int(edge['id'].split('-')[-1]);child,key,fk=links[i]
        left,right=cardinality(child,fk)
        c=ET.SubElement(d.root,'mxCell',id=f'relation-{i}',parent='1',edge='1',source=fk['parent'],target=child,
                        style=f'html=1;rounded=0;dashed=1;dashPattern=5 4;startArrow={"ERzeroToOne" if left=="|o" else "ERmandOne"};'
                              f'endArrow={"ERzeroToOne" if right=="o|" else "ERzeroToMany"};startSize=14;endSize=14;strokeColor=#72869D;strokeWidth=1.5;')
        geo=ET.SubElement(c,'mxGeometry',relative='1',attrib={'as':'geometry'})
        path=next(op['points'] for op in edge['_draw_'] if op['op'] in ('b','B','L'))
        # Dot already performed obstacle-aware routing. Preserve its computed bends.
        coords=[]
        for x,y in path[1:-1]:
            pt=(round(x+60,2),round(bounds[3]-y+210,2))
            if not coords or coords[-1]!=pt: coords.append(pt)
        if coords:
            arr=ET.SubElement(geo,'Array',attrib={'as':'points'})
            for x,y in coords: ET.SubElement(arr,'mxPoint',x=str(x),y=str(y))
        lx,ly=[float(v) for v in edge['lp'].split(',')]
        edge_label=','.join(c for c in fk['columns'] if c!='org_id')
        lw=next(op['width'] for op in edge['_ldraw_'] if op['op']=='T')+8
        d.cell(f'label-{i}',html.escape(edge_label),lx-lw/2+60,bounds[3]-ly+197,lw,26,
               'text;align=center;verticalAlign=middle;fontSize=12;fontFamily=Arial;fillColor=none;labelBackgroundColor=#FFFFFF;strokeColor=none;')
    d.text('legend','PK = 主键；FK = 外键成员；? = 允许 NULL；|| = 恰好 1；○| = 0 或 1；○< = 0 到多。',60,height-105,width-120,28,17,MUTED)
    d.text('scope','复合外键还包含 org_id；引用框为其他分域的同一张表。虚线均为实际外键，业务引用不画成外键。',60,height-72,width-120,28,17,MUTED)
    d.write()


def use_cases():
    d = Diagram('backend-use-cases', '05 / 后端用例图',
                '椭圆表示用户目标；实线表示参与关系。参与者是当前业务职责，不代表已建成账号与角色管理系统。', 1690)
    d.cell('system', '', 345, 215, 1090, 1260,
           'rounded=1;arcSize=3;fillColor=#FFFFFF;strokeColor=#72869D;strokeWidth=2;')
    d.text('system-name', '慢病健康管理后端 · 当前实现范围', 380, 232, 1000, 40, 24, bold=True)
    for id, label, x,y in [('editor','方案编辑者 / 管理人员',95,645),
                            ('reviewer','独立审核者',1565,715),
                            ('patient-actor','患者',1565,1140),
                            ('deepseek','«external system»\nDeepSeek API',1565,460)]:
        d.cell(id, html.escape(label).replace('\n','<br>'), x,y,80,120,
               'shape=umlActor;portConstraint=eastwest;verticalLabelPosition=bottom;verticalAlign=top;align=center;'
               'fillColor=#FFFFFF;strokeColor=#52647B;strokeWidth=2;fontSize=19;labelWidth=260;')
    # Cluster related goals vertically; no process arrows between the use cases.
    cases = [
      ('read','查询授权患者与资料',425,310,BLUE),
      ('draft','建立 / 编辑方案草稿\n建立新版本',425,435,BLUE),
      ('generate','请求 AI 候选\n查询结果 / 明确重试',425,560,TEAL),
      ('adopt','人工采用 AI 候选',425,685,TEAL),
      ('confirm','确认执行条件与来源',425,810,GOLD),
      ('submit-plan','提交 / 撤回方案一审',425,935,GOLD),
      ('task-edit','派生任务包 / 编辑任务\n生成排程预览',425,1060,BLUE),
      ('submit-task','提交 / 撤回任务二审',425,1180,GOLD),
      ('publish','预览衔接 / 发布任务\n查询发布结果',425,1325,TEAL),
      ('review-plan','查看方案送审快照\n一审通过 / 退回',990,670,GOLD),
      ('review-task','查看任务与排程快照\n二审通过 / 退回',990,850,GOLD),
      ('daily','查询本人日任务与详情',990,1060,BLUE),
      ('feedback','提交打卡 / 补记\n追加更正 / 查询反馈',990,1260,TEAL),
    ]
    for id,label,x,y,color in cases:
        d.cell(id, html.escape(label).replace('\n','<br>'), x,y,355,100,
               f'ellipse;portConstraint=eastwest;fillColor={color};strokeColor={LINE};strokeWidth=1.5;fontSize=21;align=center;')
    associations = [('editor', id) for id in ['read','draft','generate','adopt','confirm','submit-plan','task-edit','submit-task','publish']]
    associations += [('reviewer','review-plan'),('reviewer','review-task'),('patient-actor','daily'),('patient-actor','feedback'),('deepseek','generate')]
    # Actor associations are deliberately plain, undirected UML lines.
    for i,(actor,target) in enumerate(associations):
        c=ET.SubElement(d.root,'mxCell',id=f'association-{i}',parent='1',edge='1',source=actor,target=target,
                        style='edgeStyle=orthogonalEdgeStyle;endArrow=none;startArrow=none;html=1;strokeColor=#72869D;strokeWidth=1.5;')
        ET.SubElement(c,'mxGeometry',relative='1',attrib={'as':'geometry'})
    d.box('audit-note','审核约束',['提交人不能审核自己的提交','一审检查方案；二审检查任务与排程'],895,330,480,115,GREY)
    d.text('note1','授权边界：操作受 patient_access_grant 能力限制；专业来源确认还需 PLAN_CONFIRM_PROFESSIONAL。',60,1510,1680,34,18,MUTED)
    d.text('note2','AI 只提供候选，采用、送审、批准和发布均为独立动作。正式登录、管理员后台及照护者客户端未纳入。',60,1555,1680,34,18,MUTED)
    d.write()


def schema_index():
    doc=OUT.parent/'DATABASE-ER.md'
    text=doc.read_text()
    rows=['### 7.1 全部表与唯一键', '', '| 表 | 主键 | 外键数 | 主要联合唯一键（省略范围键 org_id + id） |',
          '| --- | --- | --- | --- |']
    for _,_,names in DOMAINS:
        for name in names:
            table=TABLES[name]
            unique=[f'`{", ".join(cols)}`' for key,cols in table['uniqueKeys'].items()
                    if key!='PRIMARY' and cols!=['org_id','id']]
            rows.append(f'| {NAMES[name]} `{name}` | `{", ".join(table["uniqueKeys"]["PRIMARY"])}` | {len(table["foreignKeys"])} | '+('；'.join(unique) or '—')+' |')
    rows += ['', '### 7.2 全部物理外键', '',
             '下表的基数按外键、NULL 约束和联合唯一键计算；审核目标还受第 3 节互斥 CHECK 限制。', '',
             '| 子表与外键名 | 子列 → 父表（列） | 每个子记录对应父记录数 | 每个父记录对应子记录数 |',
             '| --- | --- | --- | --- |']
    for child,key,fk in relations([name for _,_,names in DOMAINS for name in names]):
        left,right=cardinality(child,fk)
        rows.append(f'| `{child}`；`{key}` | `({", ".join(fk["columns"])})` → `{fk["parent"]}({", ".join(fk["parentColumns"])})` | {"0..1" if left=="|o" else "1"} | {"0..1" if right=="o|" else "0..N"} |')
    start='<!-- SCHEMA-INDEX -->';end='<!-- /SCHEMA-INDEX -->'
    a=text.index(start)+len(start);b=text.index(end,a)
    doc.write_text(text[:a]+'\n\n'+'\n'.join(rows)+'\n\n'+text[b:])


if __name__ == '__main__':
    domain_tables = [name for _,_,names in DOMAINS for name in names]
    assert len(domain_tables) == len(set(domain_tables)) == len(TABLES) == 33
    assert set(domain_tables) == set(TABLES)
    manifest = [er_source(slug, title + ' · 物理 ER 详图', names) for slug,title,names in DOMAINS]
    overview_tables = ['patient','care_plan','care_plan_version','care_plan_item','health_input_snapshot',
                       'plan_generation_job','review_submission','review_decision','task_bundle','publication',
                       'execution_task','task_feedback','task_result']
    overview_columns = {
       'care_plan':['patient_id'], 'care_plan_version':['plan_id'], 'care_plan_item':['plan_version_id'],
       'health_input_snapshot':['patient_id'], 'plan_generation_job':['plan_version_id','input_snapshot_id'],
       'review_submission':['plan_version_id','task_bundle_id'], 'review_decision':['submission_id'],
       'task_bundle':['plan_version_id'], 'publication':['task_bundle_id'], 'execution_task':['publication_id'],
       'task_feedback':['execution_task_id'], 'task_result':['execution_task_id','current_feedback_id'],
    }
    chosen = [(child,key,fk) for child,key,fk in relations(overview_tables)
              if any(col in fk['columns'] for col in overview_columns.get(child, []))]
    er_source('backend-database-er', '04 / 数据库核心 ER 总览 · 13 张核心表 / 选取主要物理外键', overview_tables, chosen, compact=True)
    (OUT / 'er-coverage.json').write_text(json.dumps({'tables': len(TABLES), 'foreignKeys': sum(len(t['foreignKeys']) for t in TABLES.values()),
                                                    'domains': manifest},ensure_ascii=False,indent=2)+'\n')
    use_cases()
    schema_index()
    print('Generated 5 native ER diagrams; detailed domains cover all 33 tables and 62 foreign keys.')
