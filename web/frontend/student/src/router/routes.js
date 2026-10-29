export const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/login/Register.vue'),
    meta: { public: true }
  },
  {
    path: '/maintenance',
    name: 'Maintenance',
    component: () => import('../views/Maintenance.vue'),
    meta: { public: true }
  },
  {
    path: '/campus-3d',
    name: 'Campus3D',
    component: () => import('../views/campus3d/Campus3D.vue'),
    meta: { public: true, title: '3D校园' }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    children: [
      { path: '', name: 'Home', component: () => import('../views/home/Home.vue'), meta: { public: true } },
      { path: 'search', name: 'SearchResults', component: () => import('../views/search/SearchResults.vue'), meta: { public: true } },
      // AI 学习中心
      { path: 'ai/chat', name: 'AiChat', component: () => import('../views/ai/ChatView.vue') },
      { path: 'ai/code', name: 'CodeFix', component: () => import('../views/ai/CodeFix.vue') },
      { path: 'ai/wrong', name: 'WrongBook', component: () => import('../views/ai/WrongBook.vue') },
      // 闲置互换
      { path: 'idle', name: 'IdleList', component: () => import('../views/idle/List.vue'), meta: { public: true } },
      { path: 'idle/publish', name: 'IdlePublish', component: () => import('../views/idle/Publish.vue') },
      { path: 'idle/detail/:id', name: 'IdleDetail', component: () => import('../views/idle/Detail.vue'), meta: { public: true } },
      { path: 'idle/appointments', name: 'MyAppointments', component: () => import('../views/idle/MyAppointments.vue') },
      // 活动组队
      { path: 'activity', name: 'ActivityList', component: () => import('../views/activity/List.vue'), meta: { public: true } },
      { path: 'activity/publish', name: 'ActivityPublish', component: () => import('../views/activity/Publish.vue') },
      { path: 'activity/detail/:id', name: 'ActivityDetail', component: () => import('../views/activity/Detail.vue'), meta: { public: true } },
      { path: 'activity/my-signup', name: 'MySignup', component: () => import('../views/activity/MySignup.vue') },
      // 失物招领
      { path: 'lostfound', name: 'LostFoundList', component: () => import('../views/lostfound/List.vue'), meta: { public: true } },
      { path: 'lostfound/publish', name: 'LostFoundPublish', component: () => import('../views/lostfound/Publish.vue') },
      { path: 'lostfound/detail/:id', name: 'LostFoundDetail', component: () => import('../views/lostfound/Detail.vue'), meta: { public: true } },
      // 学习搭子
      { path: 'partner', name: 'PartnerList', component: () => import('../views/partner/List.vue'), meta: { public: true } },
      { path: 'partner/publish', name: 'PartnerPublish', component: () => import('../views/partner/Publish.vue') },
      // 校园互助问答
      { path: 'qa', name: 'QaList', component: () => import('../views/qa/List.vue'), meta: { public: true } },
      { path: 'qa/publish', name: 'QaPublish', component: () => import('../views/qa/Publish.vue') },
      { path: 'qa/my', name: 'QaMy', component: () => import('../views/qa/My.vue') },
      { path: 'qa/detail/:id', name: 'QaDetail', component: () => import('../views/qa/Detail.vue'), meta: { public: true } },
      // 动态广场
      { path: 'social', name: 'PostSquare', component: () => import('../views/social/PostSquare.vue'), meta: { public: true } },
      // 实时多人游戏（继续使用校园账号鉴权）
      { path: 'draw-guess', name: 'DrawGuessLobby', component: () => import('../views/drawGuess/Lobby.vue') },
      { path: 'draw-guess/room/:roomId', name: 'DrawGuessRoom', component: () => import('../views/drawGuess/Room.vue') },
      // 公告
      { path: 'notice', name: 'NoticeList', component: () => import('../views/notice/List.vue'), meta: { public: true } },
      { path: 'notice/detail/:id', name: 'NoticeDetail', component: () => import('../views/notice/Detail.vue'), meta: { public: true } },
      // 消息与个人中心
      { path: 'message', name: 'MessageCenter', component: () => import('../views/message/MessageCenter.vue') },
      { path: 'chat', name: 'ConversationList', component: () => import('../views/chat/ConversationList.vue') },
      { path: 'chat/:conversationId', name: 'ChatRoom', component: () => import('../views/chat/ChatRoom.vue') },
      { path: 'user/:id', name: 'UserHome', component: () => import('../views/user/UserHome.vue') },
      { path: 'profile', name: 'Profile', component: () => import('../views/profile/Profile.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]
