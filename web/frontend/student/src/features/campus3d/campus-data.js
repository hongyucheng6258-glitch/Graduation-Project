export const GROUPS = [
 {id:'hub',name:'校园总览',color:'#2864ea',description:'一站连接你的校园生活'},
 {id:'service',name:'校园服务',color:'#2864ea',description:'生活中的小事，在这里解决'},
 {id:'social',name:'社交信息',color:'#1b9e95',description:'发现同频的人与新鲜事'},
 {id:'ai',name:'AI 学习',color:'#8470cf',description:'陪你走好每一步学习旅程'}
];
export const SCENE_THEMES = {
 hub: { accent: '#2864ea', soft: '#dbeaff', label: '梧桐中庭', number: '00', caption: '数字校园 · 从这里开始' },
 service: { accent: '#2864ea', soft: '#dbeaff', label: '校园服务', number: '01', caption: '生活服务 · 活动与办事' },
 social: { accent: '#1b9e95', soft: '#d9f3ee', label: '社交信息', number: '02', caption: '校园连接 · 分享与互助' },
 ai: { accent: '#8470cf', soft: '#e9e3ff', label: 'AI 学习', number: '03', caption: '智能学习 · 答疑与成长' }
};
// 第9项修复：合并重复服务图片映射（qa/square/message 共用 social，aichat/code 共用 ai），
// 并统一改用 WebP（体积下降 90%+，原 PNG 保留未删除）。
const MAP_SERVICE_IMAGE = Object.freeze({
 portal: '/images/campus-v2.webp',
 activity: '/images/generated-activity-campus.webp',
 idle: '/images/generated-idle-items.webp',
 partner: '/images/generated-study-partner.webp',
 lost: '/images/generated-lost-found.webp',
 social: '/images/generated-social-campus.webp',
 drawgame: '/images/generated-social-campus.webp',
 notice: '/images/study-banner.jpg',
 ai: '/images/generated-ai-study.webp',
 wrong: '/images/wrong-bg.jpg'
});
export const SERVICES = [
 ['portal','hub','综合门户','校园动态，一屏纵览','home','中庭','首页 大厅 总览'],
 ['activity','service','校园活动','发现精彩，参与热爱','calendar','A101','报名 签到 社团 运动 志愿'],
 ['idle','service','闲置互换','让闲置，遇见新主人','bag','A102','二手 商品 买卖 收藏'],
 ['partner','service','学习搭子','找个同伴，一起进步','users','A103','自习 考研 组队 匹配'],
 ['lost','service','失物招领','每一份遗失，都有回应','search','A104','寻物 认领 拾物 校园卡'],
 ['qa','social','互助问答','你的问题，总有人懂','help','B101','提问 回答 采纳'],
 ['square','social','动态广场','分享校园里的小美好','chat','B102','帖子 评论 点赞 朋友圈'],
 ['drawgame','social','你画我猜','用画笔和灵感连起校园同伴','spark','B105','你画我猜 绘画 猜词 游戏 房间'],
 ['notice','social','校园公告','重要消息，不再错过','megaphone','B103','通知 奖学金 教务'],
 ['message','social','消息中心','让每一份联系，及时抵达','bell','B104','私信 通知 聊天 未读'],
 ['aichat','ai','AI 答疑','把不懂，变成恍然大悟','spark','C101','助手 聊天 人工智能 学习'],
 ['code','ai','代码纠错','读懂问题，写出好代码','code','C102','编程 修复 debug 程序'],
 ['wrong','ai','错题本','让每一次出错，都有收获','book','C103','复习 高数 概率 掌握 题目']
].map(([id,group,name,desc,icon,room,keywords])=>({id,group,name,desc,icon,room,keywords,image:MAP_SERVICE_IMAGE[id] || '',color:GROUPS.find(g=>g.id===group).color}));

// 3D room presentation is data-driven so a new service can reuse the same
// spatial components without coupling geometry to service content.
export const ROOM_PROFILES = {
 activity: { type: '活动策划室', prompt: '查看活动日历、报名信息与校园精彩安排', facilities: ['活动海报墙', '日历屏', '报名台'], accent: '#2f6df0', floorTone: '#dce8fc' },
 idle: { type: '校园交换站', prompt: '浏览闲置物品，发布交换需求', facilities: ['展示架', '价格牌', '交换柜'], accent: '#1f9ed0', floorTone: '#d6f0fa' },
 partner: { type: '学习协作室', prompt: '寻找学习搭子，查看协作匹配', facilities: ['学习桌', '座位牌', '搭子信息墙'], accent: '#5b8def', floorTone: '#e2ebfd' },
 lost: { type: '物品服务台', prompt: '登记、寻找并认领校园遗失物品', facilities: ['登记台', '定位屏', '招领柜'], accent: '#e0892d', floorTone: '#fbeedd' },
 qa: { type: '讨论空间', prompt: '提出问题，查看同学们的互助回答', facilities: ['问题墙', '回答台', '便签板'], accent: '#0f9d8c', floorTone: '#d7f1ec' },
 square: { type: '校园媒体墙', prompt: '浏览校园动态，分享你的精彩瞬间', facilities: ['图片墙', '动态屏', '互动展示区'], accent: '#22b07d', floorTone: '#dcf6ec' },
 drawgame: { type: '你画我猜游戏室', prompt: '和校园同伴轮流作画、实时猜词', facilities: ['多人画板', '回合计时屏', '作品墙'], accent: '#4d79d8', floorTone: '#e1ebfb' },
 notice: { type: '信息公告室', prompt: '查看教务、奖学金与校园重要通知', facilities: ['公告栏', '通知屏', '重点灯箱'], accent: '#d39a1e', floorTone: '#faf2dd' },
 message: { type: '校园通信站', prompt: '打开会话列表，处理未读消息', facilities: ['消息终端', '未读灯', '会话屏'], accent: '#1982c4', floorTone: '#dcf0f9' },
 aichat: { type: '智能学习舱', prompt: '向 AI 助手提问，获得学习思路', facilities: ['助手屏', '问题卡', '对话终端'], accent: '#8470cf', floorTone: '#e9e4fc' },
 code: { type: '编程工作台', prompt: '提交代码示例，查看纠错与修复建议', facilities: ['双屏工作站', '代码屏', '结果屏'], accent: '#5b6ee8', floorTone: '#e3e6fc' },
 wrong: { type: '复习工作室', prompt: '整理错题，查看知识点掌握进度', facilities: ['复习桌', '错题卡', '进度板'], accent: '#b06ad4', floorTone: '#f4e4fb' }
};
const DEFAULT_ROOM_PROFILE = { type: '校园服务空间', prompt: '点击服务台打开对应服务内容', facilities: ['服务台', '信息屏', '资料架'] };
export function getRoomProfile(id) { return ROOM_PROFILES[id] || DEFAULT_ROOM_PROFILE; }
const paths={
home:'<path d="m3 10 9-7 9 7v11h-7v-7h-4v7H3Z"/>',
calendar:'<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 3v4m10-4v4M3 11h18m-13 4h2m4 0h2m-8 3h2"/>',
bag:'<path d="M5 7h14l2 14H3L5 7Z"/><path d="M8 8V6a4 4 0 0 1 8 0v2"/>',
users:'<circle cx="9" cy="8" r="3"/><path d="M3 21v-2a6 6 0 0 1 12 0v2m2-16a3 3 0 0 1 0 6m4 10v-2a6 6 0 0 0-3-5"/>',
search:'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
help:'<circle cx="12" cy="12" r="9"/><path d="M9.5 9a2.5 2.5 0 0 1 5 .5c0 1.5-2.5 2-2.5 3.5m0 3h.01"/>',
chat:'<path d="M21 11.5a8.5 8.5 0 0 1-12.5 7.4L3 21l2.1-5.5A8.5 8.5 0 1 1 21 11.5Z"/><path d="M8 11h8m-8 4h5"/>',
megaphone:'<path d="m3 10 18-6v15L3 13v-3Zm5 4 2 7H6l-2-7"/>',
bell:'<path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M10 21h4"/>',
spark:'<path d="m12 3 2.7 6.3L21 12l-6.3 2.7L12 21l-2.7-6.3L3 12l6.3-2.7L12 3Zm7-1v4m-2-2h4"/>',
code:'<path d="m8 6-6 6 6 6m8-12 6 6-6 6m-3-15-2 18"/>',
book:'<path d="M3 19V4h6a3 3 0 0 1 3 3v14a4 4 0 0 0-4-2H3Zm9-12a3 3 0 0 1 3-3h6v15h-5a4 4 0 0 0-4 2"/>',
grid:'<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
map:'<path d="m3 5 6-2 6 2 6-2v16l-6 2-6-2-6 2V5Zm6-2v16m6-14v16"/>',
compass:'<circle cx="12" cy="12" r="9"/><path d="m16 8-2.5 5.5L8 16l2.5-5.5L16 8Z"/>',
arrow:'<path d="M5 12h14m-5-5 5 5-5 5"/>',
chevron:'<path d="m9 5 7 7-7 7"/>',back:'<path d="m10 5-7 7 7 7M3 12h18"/>',
close:'<path d="m6 6 12 12M6 18 18 6"/>',
moon:'<path d="M21 13a9 9 0 1 1-10-10 7 7 0 0 0 10 10Z"/>',
sun:'<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1 1m12 12 1 1M5 19l1-1M18 6l1-1"/>',
leaf:'<path d="M20 3C10 1 2 7 4 15c2 8 17 5 16-12ZM4 21 15 10m-5 5v-5m0 5h5"/>',
check:'<path d="m5 12 4 4L19 6"/>',heart:'<path d="M20.5 5.5a5 5 0 0 0-7 0L12 7l-1.5-1.5a5 5 0 0 0-7 7L12 21l8.5-8.5a5 5 0 0 0 0-7Z"/>',
clock:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',pin:'<path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 0 1 14 0Z"/><circle cx="12" cy="10" r="2"/>',
cube:'<path d="m12 2 10 5-10 5L2 7l10-5ZM2 7v10l10 5 10-5V7M12 12v10"/>',
expand:'<path d="M8 3H3v5m13-5h5v5M3 16v5h5m8 0h5v-5"/>',menu:'<path d="M4 6h16M4 12h16M4 18h16"/>',plus:'<path d="M12 4v16M4 12h16"/>',
shield:'<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z"/><path d="m8 12 3 3 5-6"/>'
};
export function icon(name){return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'+(paths[name]||paths.spark)+'</svg>';}
export function escapeHTML(value){return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
