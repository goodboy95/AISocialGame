# 模块 17：全局导航与布局优化

> 优先级：P1 | 类型：前端布局设计 | 依赖：模块 15（设计系统） | 被依赖：无

## 1. 设计目标

优化全局导航结构和页面布局，使其适配新增的功能模块（好友、成就、回放、规则百科），同时保持简洁直觉的导航体验。

核心改动：
- 桌面端 Header 导航重组
- 移动端底部 Tab Bar 精简 + 更多入口
- 页面布局统一规范
- 全局状态栏（在线人数、好友通知、游戏邀请）

## 2. 导航结构

### 2.1 信息架构

```
首页 (/)
├── 游戏大厅 (/games/:gameId)
│   ├── 房间列表 (/games/:gameId/rooms)
│   └── 房间 (/games/:gameId/rooms/:roomId)
├── 快速匹配 (弹窗，无独立路由)
├── 社区 (/community)
├── 排行榜 (/rankings)
├── 好友 (侧边面板，无独立路由)
├── 成就 (/achievements)
├── 对局回放 (/replays)
├── 规则百科 (/guide)
│   └── 游戏规则 (/guide/:gameId)
├── 个人中心 (/profile)
│   ├── 战绩统计
│   ├── 成就展示
│   └── 设置
└── 管理后台 (/admin)
```

### 2.2 导航优先级

| 优先级 | 入口 | 说明 |
|--------|------|------|
| P0 | 首页、游戏大厅 | 核心路径 |
| P1 | 社区、排行榜 | 社交留存 |
| P1 | 好友面板 | 社交核心（侧边常驻） |
| P2 | 成就、回放、百科 | 辅助功能 |
| P3 | 个人中心、管理后台 | 低频入口 |

## 3. 桌面端 Header

### 3.1 布局

```
┌──────────────────────────────────────────────────────────────┐
│ [Logo]  首页  游戏  社区  排行榜  百科    🔍   🔔  👤  💰100 │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 组件结构

```tsx
// components/layout/DesktopHeader.tsx
const DesktopHeader = () => (
  <header className="sticky top-0 z-50 bg-white/80 backdrop-blur-md border-b">
    <div className="max-w-7xl mx-auto flex items-center h-14 px-6">
      {/* 左侧：Logo + 主导航 */}
      <div className="flex items-center gap-6">
        <Link to="/" className="font-bold text-lg">AISocialGame</Link>
        <nav className="flex items-center gap-1">
          <NavLink to="/">首页</NavLink>
          <NavDropdown label="游戏" items={gameList} />
          <NavLink to="/community">社区</NavLink>
          <NavLink to="/rankings">排行榜</NavLink>
          <NavLink to="/guide">百科</NavLink>
        </nav>
      </div>

      {/* 右侧：工具栏 */}
      <div className="ml-auto flex items-center gap-2">
        <QuickMatchButton />
        <NotificationBell />
        <FriendPanelToggle />
        <CoinDisplay />
        <UserDropdown />
      </div>
    </div>
  </header>
);
```

### 3.3 导航链接样式

```tsx
const NavLink = ({ to, children }: Props) => (
  <Link to={to} className={cn(
    "px-3 py-1.5 rounded-md text-sm font-medium transition-colors",
    isActive
      ? "bg-primary/10 text-primary"
      : "text-muted-foreground hover:text-foreground hover:bg-accent"
  )}>
    {children}
  </Link>
);
```

### 3.4 游戏下拉菜单

"游戏"导航项展开为下拉菜单，展示所有游戏类型：

```tsx
const NavDropdown = ({ label, items }: Props) => (
  <DropdownMenu>
    <DropdownMenuTrigger className="px-3 py-1.5 rounded-md text-sm font-medium
      text-muted-foreground hover:text-foreground flex items-center gap-1">
      {label} <ChevronDown className="h-3 w-3" />
    </DropdownMenuTrigger>
    <DropdownMenuContent align="start" className="w-56">
      {items.map(game => (
        <DropdownMenuItem key={game.id} asChild>
          <Link to={`/games/${game.id}/rooms`} className="flex items-center gap-3">
            <GameIcon name={game.iconName} className="h-5 w-5" />
            <div>
              <div className="font-medium text-sm">{game.name}</div>
              <div className="text-xs text-muted-foreground">{game.onlineCount} 人在线</div>
            </div>
          </Link>
        </DropdownMenuItem>
      ))}
      <DropdownMenuSeparator />
      <DropdownMenuItem asChild>
        <Link to="/guide" className="text-sm text-muted-foreground">
          查看全部游戏规则
        </Link>
      </DropdownMenuItem>
    </DropdownMenuContent>
  </DropdownMenu>
);
```

## 4. 移动端底部 Tab Bar

### 4.1 布局

5 个 Tab，覆盖最高频入口：

```
┌─────────────────────────────────────┐
│  🏠首页  🎮游戏  👥社区  🏆排行  👤我的 │
└─────────────────────────────────────┘
```

### 4.2 组件

```tsx
// components/layout/MobileTabBar.tsx
const tabs = [
  { icon: Home, label: "首页", to: "/" },
  { icon: Gamepad2, label: "游戏", to: "/games" },
  { icon: Users, label: "社区", to: "/community" },
  { icon: Trophy, label: "排行", to: "/rankings" },
  { icon: User, label: "我的", to: "/profile" },
];

const MobileTabBar = () => (
  <nav className="fixed bottom-0 inset-x-0 z-50 bg-white/90 backdrop-blur-md
    border-t safe-area-bottom md:hidden">
    <div className="flex items-center justify-around h-14">
      {tabs.map(tab => (
        <Link key={tab.to} to={tab.to}
          className={cn(
            "flex flex-col items-center gap-0.5 px-3 py-1",
            isActive(tab.to)
              ? "text-primary"
              : "text-muted-foreground"
          )}>
          <tab.icon className="h-5 w-5" />
          <span className="text-[10px] font-medium">{tab.label}</span>
          {/* 通知红点 */}
          {tab.to === "/community" && unreadCount > 0 && (
            <div className="absolute top-0.5 right-1 w-2 h-2 bg-red-500 rounded-full" />
          )}
        </Link>
      ))}
    </div>
  </nav>
);
```

### 4.3 游戏内隐藏

进入游戏房间后，底部 Tab Bar 隐藏，让出全部屏幕空间给游戏界面：

```tsx
// MainLayout.tsx
const MainLayout = () => {
  const isInGame = useMatch("/games/:gameId/rooms/:roomId");

  return (
    <>
      {!isInGame && <DesktopHeader />}
      <main className={cn(!isInGame && "pb-16 md:pb-0")}>
        <Outlet />
      </main>
      {!isInGame && <MobileTabBar />}
    </>
  );
};
```

## 5. 好友侧边面板

好友面板作为全局侧边栏，桌面端从右侧滑出，移动端为全屏 Sheet：

```tsx
// components/social/FriendPanel.tsx
const FriendPanel = () => (
  <Sheet>
    <SheetTrigger asChild>
      <Button variant="ghost" size="icon" className="relative">
        <Users className="h-5 w-5" />
        {onlineFriendCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 bg-green-500 text-white
            rounded-full text-[10px] min-w-[16px] h-4 flex items-center justify-center">
            {onlineFriendCount}
          </span>
        )}
      </Button>
    </SheetTrigger>
    <SheetContent side="right" className="w-80 p-0">
      <div className="flex flex-col h-full">
        <div className="p-4 border-b">
          <h3 className="font-semibold">好友</h3>
          <Input placeholder="搜索好友..." className="mt-2" size="sm" />
        </div>

        {/* 在线好友 */}
        <div className="flex-1 overflow-y-auto">
          <div className="px-4 py-2 text-xs text-muted-foreground font-medium">
            在线 ({onlineFriends.length})
          </div>
          {onlineFriends.map(friend => (
            <FriendItem key={friend.id} friend={friend} />
          ))}

          <div className="px-4 py-2 text-xs text-muted-foreground font-medium">
            离线 ({offlineFriends.length})
          </div>
          {offlineFriends.map(friend => (
            <FriendItem key={friend.id} friend={friend} />
          ))}
        </div>

        {/* 好友请求 */}
        {pendingRequests.length > 0 && (
          <div className="border-t p-3">
            <Button variant="outline" size="sm" className="w-full">
              {pendingRequests.length} 个好友请求
            </Button>
          </div>
        )}
      </div>
    </SheetContent>
  </Sheet>
);
```

## 6. 通知中心

### 6.1 通知铃铛

```tsx
const NotificationBell = () => (
  <Popover>
    <PopoverTrigger asChild>
      <Button variant="ghost" size="icon" className="relative">
        <Bell className="h-5 w-5" />
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 bg-red-500 text-white
            rounded-full text-[10px] min-w-[16px] h-4 flex items-center justify-center">
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </Button>
    </PopoverTrigger>
    <PopoverContent align="end" className="w-80 p-0">
      <div className="p-3 border-b font-semibold text-sm">通知</div>
      <div className="max-h-80 overflow-y-auto">
        {notifications.map(n => (
          <NotificationItem key={n.id} notification={n} />
        ))}
      </div>
    </PopoverContent>
  </Popover>
);
```

### 6.2 通知类型

| 类型 | 图标 | 示例 |
|------|------|------|
| 游戏邀请 | `Gamepad2` | "张三 邀请你加入狼人杀房间" |
| 好友请求 | `UserPlus` | "李四 请求添加你为好友" |
| 成就解锁 | `Award` | "恭喜解锁成就「初出茅庐」" |
| 系统公告 | `Info` | "新游戏「谁是卧底」已上线" |

## 7. 页面布局规范

### 7.1 标准页面模板

```tsx
// 标准内容页面（首页、社区、排行榜等）
const StandardPage = ({ title, children }: Props) => (
  <div className="max-w-6xl mx-auto px-4 md:px-6 py-6">
    {title && <h1 className="text-2xl font-bold mb-6">{title}</h1>}
    {children}
  </div>
);
```

### 7.2 各页面布局

| 页面 | 桌面端布局 | 移动端布局 |
|------|-----------|-----------|
| 首页 | Hero + 游戏卡片网格(3列) | Hero + 卡片列表(1列) |
| 房间列表 | 筛选栏 + 房间卡片网格(2列) | 筛选下拉 + 卡片列表 |
| 游戏房间 | 全屏沉浸式（见模块16） | 全屏沉浸式 |
| 社区 | 三栏（侧栏+内容+热门） | 单栏 + Tab切换 |
| 排行榜 | Tab + 排行表格 | Tab + 排行列表 |
| 个人中心 | 左侧信息 + 右侧内容 | 上方信息 + 下方内容 |
| 成就 | 分类网格(3列) | 分类列表(2列) |
| 回放列表 | 卡片网格(3列) | 卡片列表(1列) |
| 规则百科 | 左侧目录 + 右侧内容 | 单栏 + 折叠目录 |

### 7.3 首页改进

```tsx
const Index = () => (
  <div>
    {/* Hero 区域 */}
    <section className="bg-gradient-to-br from-slate-900 to-slate-800 text-white
      py-16 md:py-24 px-4">
      <div className="max-w-4xl mx-auto text-center space-y-4">
        <h1 className="text-3xl md:text-5xl font-bold">AI 社交游戏平台</h1>
        <p className="text-lg text-slate-300">和朋友或 AI 一起玩推理社交游戏</p>
        <div className="flex gap-3 justify-center">
          <QuickMatchButton size="lg" />
          <Button variant="outline" size="lg" asChild>
            <Link to="/guide">了解规则</Link>
          </Button>
        </div>
      </div>
    </section>

    {/* 快速入口 */}
    <section className="max-w-6xl mx-auto px-4 -mt-8">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {games.map(game => (
          <GameCard key={game.id} game={game} />
        ))}
      </div>
    </section>

    {/* 在线动态 */}
    <section className="max-w-6xl mx-auto px-4 py-12">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        <div>
          <h2 className="text-xl font-semibold mb-4">正在进行的游戏</h2>
          <LiveGameList />
        </div>
        <div>
          <h2 className="text-xl font-semibold mb-4">最新动态</h2>
          <RecentActivityFeed />
        </div>
      </div>
    </section>
  </div>
);
```

## 8. 快速匹配入口

快速匹配作为全局 CTA，在多处可见：

| 位置 | 形式 |
|------|------|
| Header 右侧 | 渐变按钮 "快速开始" |
| 首页 Hero | 大号 CTA 按钮 |
| 游戏卡片 | "一键开局" 按钮 |
| 移动端 | 浮动 FAB 按钮（可选） |

```tsx
const QuickMatchButton = ({ size = "default" }: Props) => (
  <Button size={size}
    className="bg-gradient-to-r from-blue-500 to-blue-600 hover:from-blue-600
      hover:to-blue-700 text-white shadow-md"
    onClick={() => setShowQuickMatch(true)}>
    <Zap className="h-4 w-4 mr-1" />
    快速开始
  </Button>
);
```

## 9. 全局状态栏

在 Header 或页面顶部显示平台实时状态：

```tsx
// 可选：在首页或大厅显示
const PlatformStatus = () => (
  <div className="flex items-center gap-4 text-xs text-muted-foreground">
    <span className="flex items-center gap-1">
      <div className="w-1.5 h-1.5 bg-green-500 rounded-full animate-pulse" />
      {onlineCount} 人在线
    </span>
    <span>{activeGames} 局进行中</span>
  </div>
);
```

## 10. 测试要点

- [ ] 桌面端导航所有链接可达
- [ ] 移动端 Tab Bar 正确高亮当前页
- [ ] 游戏房间内导航栏隐藏
- [ ] 好友面板滑出/收起流畅
- [ ] 通知铃铛红点正确显示
- [ ] 游戏下拉菜单展示在线人数
- [ ] 各页面在 Mobile/Tablet/Desktop 布局正确
- [ ] 快速匹配按钮全局可用
