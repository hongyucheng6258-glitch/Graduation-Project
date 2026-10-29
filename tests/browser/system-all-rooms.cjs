const path = require('node:path');
const outputDir = process.env.CAMPUS_TEST_OUTPUT || path.join(require('node:os').tmpdir(), 'campus-browser-checks');
require('node:fs').mkdirSync(outputDir, { recursive: true });
const {chromium}=require(process.env.PLAYWRIGHT_PATH || 'playwright');const assert=require('node:assert/strict');
(async()=>{const b=await chromium.launch({channel:'chrome',headless:true,args:['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader']});const p=await b.newPage({viewport:{width:1366,height:900}});const errors=[],requests=[];p.on('pageerror',e=>{errors.push(e.message);console.log('ERROR',e.stack)});await p.addInitScript(()=>{localStorage.setItem('token','test-only');localStorage.setItem('userInfo',JSON.stringify({id:9,nickname:'测试同学'}));localStorage.setItem('wutong-campus-comfort','true')});
await p.route('**/api/**',async r=>{const u=new URL(r.request().url()),path=u.pathname;if(!path.startsWith('/api/'))return r.continue();requests.push(path+u.search);let data={};if(path.endsWith('/list'))data={list:[],total:0};else if(path.endsWith('/sessions')||path.endsWith('/subjects')||path.endsWith('/today')||path.endsWith('/conversations'))data=[];else if(path.endsWith('/weak-points'))data={knowledgePoints:[],errorReasons:[]};else if(path.endsWith('/ws-ticket'))data={ticket:'test'};else if(path.endsWith('/unread-count'))data={count:0};await r.fulfill({json:{code:200,data}})});
await p.goto('http://localhost:5173/campus-3d');await p.getByRole('button',{name:'开始探索'}).click();const completed=[];
for(const [name,selector] of [['闲置互换','.idle-list'],['学习搭子','.partner-list'],['失物招领','.lost-list'],['互助问答','.qa-list'],['动态广场','.square'],['校园公告','.notice-list'],['消息中心','.message-center'],['AI 答疑','.ai-chat'],['代码纠错','.code-page'],['错题本','.wrong-page']]){
 await p.getByRole('button',{name:'校园导览',exact:false}).first().click();await p.locator('.map-zone button').filter({hasText:name}).click();await p.getByRole('button',{name:'打开服务内容'}).click();
 await p.locator('.room-page-host').locator(':scope > *').first().waitFor();await p.waitForTimeout(300);assert.equal(await p.locator('.room-workspace-error').count(),0,name+': '+await p.locator('.room-workspace').innerText());assert.equal(new URL(p.url()).pathname,'/campus-3d');
 if(name==='消息中心'){await p.getByRole('button',{name:'系统通知',exact:true}).click();await p.waitForTimeout(150);assert(requests.some(x=>x.includes('/message/list?type=system')))}
 if(name==='互助问答'){await p.locator('.qa-list .chip').filter({hasText:'课程'}).click();await p.waitForTimeout(150);assert(requests.some(x=>decodeURIComponent(x).includes('category=课程')))}
 console.log('ROOM',name);completed.push(name);await p.getByRole('button',{name:'返回房间',exact:false}).click();await p.getByRole('button',{name:'回到门口',exact:true}).click();
}
console.log(JSON.stringify({completed,errors}));assert.deepEqual(errors,[]);await b.close()})().catch(e=>{console.error(e);process.exit(1)});
