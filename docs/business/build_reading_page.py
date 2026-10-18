"""Render the local business reading page from Markdown (requires markdown-it-py)."""
from pathlib import Path
import html
import re
from markdown_it import MarkdownIt

ROOT = Path(__file__).resolve().parent
parser = MarkdownIt('commonmark', {'html': False}).enable('table')
body = parser.render((ROOT / 'README.md').read_text())
nav = []
def section(match):
    i = len(nav) + 1
    title = match.group(1)
    nav.append(f'<a href="#section-{i}">{title}</a>')
    return f'<h2 id="section-{i}">{title}</h2>'
body = re.sub(r'<h2>(.*?)</h2>', section, body)
body = body.replace('href="API-INDEX.md"', 'href="#api-index"')
body = body.replace('href="DATABASE-ER.md"', 'href="#database-er"')
body = body.replace('href="business-overview.html"', 'href="#top"')
api = parser.render((ROOT / 'API-INDEX.md').read_text())
api = api.replace('<h1>当前后端接口索引</h1>', '<h2 id="api-index">附录 / 39 个接口索引</h2>')
api = api.replace('href="README.md"', 'href="#top"')
body += api
er = parser.render(re.sub(r'<!--.*?-->', '', (ROOT / 'DATABASE-ER.md').read_text(), flags=re.S))
er = er.replace('<h1>数据库 ER 图与关系说明</h1>', '<h2 id="database-er">附录 / 数据库 ER 图与关系说明</h2>')
er = re.sub(r'<h2>(.*?)</h2>', r'<h3>\1</h3>', er)
er = re.sub(r'<h3>(7\.[12].*?)</h3>', r'<h4>\1</h4>', er)
body += er
body = re.sub(r'<p><img src="([^"]+)" alt="([^"]+)" /></p>',
              lambda m: f'<figure><button class="diagram" data-src="{m[1]}" data-caption="{m[2]}" aria-label="放大{m[2]}">'
                        f'<img src="{m[1]}" alt="{m[2]}" /></button>'
                        f'<figcaption>{m[2]} · 点击放大，可切换原尺寸查看</figcaption></figure>', body)
body = body.replace('<table>', '<div class="table-wrap"><table>').replace('</table>', '</table></div>')
nav += ['<a href="#api-index">附录 / 接口索引</a>', '<a href="#database-er">附录 / ER 分域详图</a>']
page = '''<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>慢病健康管理 · 后端业务说明</title><style>
:root{color-scheme:light;--ink:#172b45;--muted:#60718a;--line:#dbe3ec;--accent:#255d9c}
*{box-sizing:border-box}html{scroll-behavior:smooth;scroll-padding-top:84px}body{margin:0;background:#f5f7fa;color:var(--ink);font:16px/1.85 -apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif}
header{position:sticky;top:0;z-index:2;height:64px;display:flex;align-items:center;justify-content:space-between;padding:0 32px;background:#172b45;color:white;border-bottom:1px solid #314761}
header small{margin-left:15px;color:#bbcadb}button{cursor:pointer;font:inherit}header button{background:transparent;color:white;border:1px solid #74869b;padding:4px 15px;border-radius:5px}
.shell{display:grid;grid-template-columns:224px minmax(0,1200px);gap:32px;max-width:1500px;margin:auto;padding:40px 28px}
aside{position:sticky;top:96px;align-self:start;font-size:14px}aside strong{display:block;font-size:12px;letter-spacing:2px;color:var(--muted);margin-bottom:16px}
aside a{display:block;padding:9px 12px;border-left:2px solid transparent;text-decoration:none;color:var(--muted);line-height:1.65}aside a:hover{color:var(--accent);background:#eaf0f8;border-color:var(--accent)}
.status{margin-top:26px;padding:16px;background:#e8f3ee;border:1px solid #c5ded2;border-radius:8px;font-size:13px;color:#2d6450}.status b{display:block}
main{background:white;border:1px solid var(--line);border-radius:12px;padding:42px 50px;min-width:0;box-shadow:0 8px 30px #182e4910}h1{font-size:34px;line-height:1.4;margin:0 0 14px;letter-spacing:-.6px}h2{font-size:25px;line-height:1.5;margin:54px 0 20px;border-top:1px solid var(--line);padding-top:30px}h3{font-size:19px;margin:32px 0 14px}p{margin:14px 0}a{color:var(--accent);text-underline-offset:3px}strong{font-weight:650}code{font:13px/1.6 ui-monospace,SFMono-Regular,Consolas,monospace;background:#edf1f6;border-radius:3px;padding:2px 4px;overflow-wrap:anywhere}li{margin:8px 0}ul,ol{padding-left:25px}
.table-wrap{overflow:auto;border:1px solid var(--line);border-radius:7px;margin:20px 0}table{width:100%;border-collapse:collapse;font-size:14px;line-height:1.8}th{background:#edf2f8;text-align:left;font-weight:650;white-space:nowrap}td,th{padding:12px 14px;vertical-align:top;border-bottom:1px solid var(--line)}tr:last-child td{border-bottom:0}td code{font-size:12px}tr:nth-child(even) td{background:#fafbfd}
figure{margin:24px 0;background:#fff;border:1px solid var(--line);border-radius:8px;overflow:hidden}.diagram{width:100%;padding:10px;border:0;background:white;display:block;cursor:zoom-in}.diagram img{display:block;width:100%;height:auto}figcaption{background:#f5f7fa;color:var(--muted);font-size:13px;text-align:center;padding:10px}
dialog{width:96vw;max-width:none;height:94vh;max-height:none;border:0;border-radius:10px;padding:0;background:#e9eef4;box-shadow:0 20px 90px #0005}dialog::backdrop{background:#13243cc9}.viewer-bar{height:62px;display:flex;align-items:center;gap:12px;justify-content:flex-end;padding:10px 18px;background:white;border-bottom:1px solid var(--line)}.viewer-bar strong{margin-right:auto;font-size:16px}.viewer-bar button{padding:3px 15px;background:#edf2f8;color:var(--ink);border:1px solid var(--line);border-radius:5px}.canvas{height:calc(100% - 62px);overflow:auto;padding:20px}.canvas img{display:block;max-width:100%;height:auto;margin:auto;background:white}.canvas.original img{max-width:none;margin:0}
@media(max-width:1000px){.shell{display:block;padding:20px}aside{position:static;margin-bottom:22px}aside a{display:inline-block}aside .status{display:none}main{padding:28px}header{padding:0 20px}header small{display:none}}
@media print{header,aside,dialog,figcaption{display:none!important}.shell{display:block;margin:0;padding:0}body{background:white;font-size:11pt}main{border:0;box-shadow:none;padding:0}h1{font-size:24pt}h2{font-size:17pt;break-after:avoid}h3{break-after:avoid}figure{break-inside:avoid}table{font-size:9pt}.table-wrap{overflow:visible}a{color:inherit;text-decoration:none}.diagram{padding:0}figure img{max-height:24cm;object-fit:contain}td,th{padding:7px}code{font-size:8pt}}
</style></head><body id="top"><header><div><b>HEALTH / 后端业务说明</b><small>实现快照 · 2026-10-18</small></div><button id="print">打印 / 保存 PDF</button></header>
<div class="shell"><aside><strong>阅读导航</strong>__NAV__<div class="status"><b>当前范围 · DEMO</b>41 个接口操作<br>5 张主图 + 4 张 ER 详图<br>33 张表 · 62 条外键<br>旧前端全链路仍待适配</div></aside><main>__BODY__</main></div>
<dialog id="viewer"><div class="viewer-bar"><strong id="caption"></strong><button id="size">原尺寸</button><button id="close">关闭 ×</button></div><div class="canvas"><img id="large" alt=""></div></dialog>
<script>
const viewer=document.querySelector('#viewer'),canvas=document.querySelector('.canvas'),large=document.querySelector('#large'),size=document.querySelector('#size');
document.querySelector('#print').addEventListener('click',()=>window.print());
document.querySelectorAll('.diagram').forEach(b=>b.addEventListener('click',()=>{large.src=b.dataset.src;large.alt=b.dataset.caption;document.querySelector('#caption').textContent=b.dataset.caption;canvas.classList.remove('original');size.textContent='原尺寸';viewer.showModal();canvas.scrollTo(0,0)}));
document.querySelector('#close').addEventListener('click',()=>viewer.close());
size.addEventListener('click',()=>{const original=canvas.classList.toggle('original');size.textContent=original?'适应宽度':'原尺寸';canvas.scrollTo(0,0)});
viewer.addEventListener('click',e=>{if(e.target===viewer)viewer.close()});
</script></body></html>'''
page = page.replace('__NAV__', ''.join(nav)).replace('__BODY__', body)
(ROOT / 'business-overview.html').write_text(page)
print('Rendered business-overview.html with', len(nav), 'navigation entries and', page.count('class="diagram"'), 'diagrams')
