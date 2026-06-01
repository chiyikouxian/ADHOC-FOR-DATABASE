# FANET Platform — Design System

基于 Linear.app 深色主题,适配无人机指挥监控大屏场景。

## Color Tokens (Tailwind 自定义)

| Token | Hex | 用途 |
|-------|-----|------|
| canvas | #010102 | 页面背景 |
| surface-1 | #0f1011 | 卡片/面板背景 |
| surface-2 | #141516 | 次级面板/侧边栏 |
| surface-3 | #18191a | 悬浮/弹窗背景 |
| hairline | #23252a | 边框/分割线 |
| ink | #f7f8f8 | 主文字 |
| ink-muted | #d0d6e0 | 次要文字 |
| ink-subtle | #8a8f98 | 辅助文字/标签 |
| primary | #5e6ad2 | 主色(薰衣草蓝) |
| primary-hover | #828fff | 主色悬浮 |
| success | #27a644 | 在线/正常/完成 |
| warning | #f5a623 | 低电量/信号弱 |
| danger | #e5484d | 离线/告警/错误 |
| info | #3b82f6 | 信息提示 |

## 无人机状态色映射

| 状态 | 色彩 | 含义 |
|------|------|------|
| idle | ink-subtle (#8a8f98) | 空闲待命 |
| assigned | primary (#5e6ad2) | 已分配任务 |
| flying | success (#27a644) | 飞行中 |
| offline | danger (#e5484d) | 离线 |
| maintenance | warning (#f5a623) | 维护中 |

## Typography

- 显示标题: Inter 600, 32px, tracking -0.5px
- 页面标题: Inter 600, 24px
- 卡片标题: Inter 500, 16px
- 正文: Inter 400, 14px, line-height 1.6
- 数据值: JetBrains Mono 500, 20px (遥测数值)
- 标签: Inter 400, 12px, ink-subtle

## Spacing (4px 基准)

- 组件内间距: 12px / 16px
- 卡片间距: 16px
- 区块间距: 24px
- 页面边距: 24px

## 组件规范

### 卡片
- 背景: surface-1
- 边框: 1px solid hairline
- 圆角: 8px
- 无阴影(深色主题不需要)

### 按钮
- 主按钮: bg-primary, text-white, hover:bg-primary-hover
- 次按钮: bg-surface-2, text-ink-muted, border hairline
- 危险按钮: bg-danger, text-white
- 高度: 36px, 圆角 6px, padding 0 16px

### 状态指示器
- 圆点: 8px, 对应状态色
- 带脉冲动画(flying 状态)

## 布局结构

```
┌─────────────────────────────────────────────┐
│ TopBar (48px): Logo + 导航 + 用户头像       │
├────────┬────────────────────────────────────┤
│Sidebar │ Main Content Area                  │
│(200px) │                                    │
│        │                                    │
│ 导航项 │ (路由视图)                          │
│        │                                    │
└────────┴────────────────────────────────────┘
```

## ECharts 主题

- 背景透明(继承 surface-1)
- 网格线: hairline (#23252a)
- 数据线: primary, success, warning
- 文字: ink-subtle
- 工具提示: surface-3 背景, hairline 边框
