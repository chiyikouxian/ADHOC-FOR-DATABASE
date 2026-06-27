# AI 前端设计 Skills 使用流程与规范

三个互补的设计 skill 协同工作，覆盖前端设计的完整流程：定方向、生成实现、审计打磨。

## 安装信息

| Skill | 安装路径 | 来源 |
|-------|---------|------|
| awesome-design-md | `~/.claude/skills/awesome-design-md/` | github.com/VoltAgent/awesome-design-md |
| UI/UX Pro Max | `~/.claude/skills/ui-ux-pro-max/` | github.com/nextlevelbuilder/ui-ux-pro-max-skill |
| Impeccable | `~/.claude/skills/impeccable/` | github.com/pbakaus/impeccable |

## 三者定位与分工

```
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│  awesome-design  │    │  UI/UX Pro Max   │    │   Impeccable     │
│       -md        │    │                  │    │                  │
├──────────────────┤    ├──────────────────┤    ├──────────────────┤
│ 品牌视觉方向     │ ──▶│ 设计系统生成     │ ──▶│ 质量审计与打磨   │
│ (WHAT it looks)  │    │ (HOW to build)   │    │ (IS it good)     │
├──────────────────┤    ├──────────────────┤    ├──────────────────┤
│ 73 个品牌风格    │    │ BM25 数据库搜索  │    │ 20+ 设计命令     │
│ 静态 DESIGN.md   │    │ 10 技术栈适配    │    │ Nielsen 评分体系 │
│ 零配置即用       │    │ 反模式检测       │    │ 浏览器 Live 模式 │
└──────────────────┘    └──────────────────┘    └──────────────────┘
```

---

## Phase 1: 确定视觉方向 (awesome-design-md)

### 可用品牌风格 (73 个)

airbnb, airtable, apple, binance, bmw, bmw-m, bugatti, cal, claude, clay,
clickhouse, cohere, coinbase, composio, cursor, dell-1996, elevenlabs, expo,
ferrari, figma, framer, hashicorp, hp, ibm, intercom, kraken, lamborghini,
linear.app, lovable, mastercard, meta, minimax, mintlify, miro, mistral.ai,
mongodb, nike, notion, nvidia, ollama, opencode.ai, pinterest, playstation,
posthog, raycast, renault, replicate, resend, revolut, runwayml, sanity,
sentry, shopify, slack, spacex, spotify, starbucks, stripe, supabase,
superhuman, tesla, theverge, together.ai, uber, vercel, vodafone,
voltagent, warp, webflow, wired, wise, x.ai, zapier

### 使用方式

将选定品牌的 `DESIGN.md` 复制到项目根目录：

```bash
cp ~/.claude/skills/awesome-design-md/design-md/linear.app/DESIGN.md ./DESIGN.md
```

Claude Code 会自动读取项目根目录的 `DESIGN.md`，在生成 UI 时遵循其中定义的：
- 色彩系统（具体色值）
- 字体与排版层级
- 间距与布局 token
- 组件风格约定
- 阴影、圆角、动效参数

### 选择建议

| 项目类型 | 推荐品牌风格 |
|---------|-------------|
| SaaS / 开发者工具 | linear.app, vercel, raycast, cursor |
| 金融 / 支付 | stripe, wise, revolut, mastercard |
| AI / 机器学习 | claude, mistral.ai, cohere, x.ai |
| 电商 / 消费品 | shopify, airbnb, nike, starbucks |
| 社交 / 内容 | notion, spotify, pinterest, slack |
| 汽车 / 奢侈品 | tesla, bmw-m, ferrari, lamborghini |
| 企业 / 基础设施 | hashicorp, ibm, nvidia, mongodb |

---

## Phase 2: 生成设计系统 (UI/UX Pro Max)

### 触发方式

自然语言触发（自动激活）或 slash command：

```
/ui-ux-pro-max Build a dashboard for my analytics product
```

### 核心能力

| 功能 | 说明 |
|------|------|
| 设计系统生成 | `--design-system` 从关键词合成完整设计规范 |
| 领域搜索 | `--domain style/color/typography/chart/ux/landing/product` |
| 技术栈适配 | `--stack react/nextjs/vue/svelte/swiftui/flutter/tailwind/shadcn` |
| 持久化 | `--persist` 生成 MASTER.md 保持跨会话一致 |

### 搜索命令

```bash
# 生成完整设计系统
python3 scripts/search.py "fintech banking" --design-system -p "FinPay"

# 按领域搜索
python3 scripts/search.py "glassmorphism" --domain style
python3 scripts/search.py "elegant serif" --domain typography
python3 scripts/search.py "warm earth tones" --domain color

# 按技术栈过滤
python3 scripts/search.py "responsive grid" --stack html-tailwind

# 持久化设计系统
python3 scripts/search.py "fintech" --design-system --persist -p "FinPay"
```

### 优先级规则 (1-10)

| 优先级 | 类别 | 影响 | 关键检查 |
|--------|------|------|---------|
| 1 | 无障碍 | CRITICAL | 对比度 4.5:1, Alt 文本, 键盘导航 |
| 2 | 触摸交互 | CRITICAL | 最小 44×44px, 间距 8px+, 加载反馈 |
| 3 | 性能 | HIGH | WebP/AVIF, 懒加载, CLS < 0.1 |
| 4 | 风格选择 | HIGH | 匹配产品类型, 一致性, SVG 图标 |
| 5 | 布局响应 | HIGH | 移动优先, viewport meta, 无水平滚动 |
| 6 | 排版配色 | MEDIUM | 基础 16px, 行高 1.5, 语义色彩 token |
| 7 | 动画 | MEDIUM | 时长 150-300ms, 有意义的动效 |
| 8 | 表单反馈 | MEDIUM | 可见标签, 错误就近显示 |
| 9 | 导航模式 | HIGH | 可预测返回, 底部导航 ≤5 项 |
| 10 | 图表数据 | LOW | 图例, 工具提示, 无障碍配色 |

---

## Phase 3: 审计与打磨 (Impeccable)

### 触发方式

```
/impeccable [command] [target]
```

### 完整命令列表

| 命令 | 类别 | 用途 |
|------|------|------|
| `craft [feature]` | 构建 | 从零设计并实现一个功能 |
| `shape [feature]` | 构建 | 编码前规划 UX/UI |
| `init` | 构建 | 初始化项目上下文 (PRODUCT.md, DESIGN.md) |
| `document` | 构建 | 从现有代码生成 DESIGN.md |
| `extract [target]` | 构建 | 提取可复用 token 和组件到设计系统 |
| `critique [target]` | 评估 | UX 设计审查 + 启发式评分 |
| `audit [target]` | 评估 | 技术质量检查 (a11y, 性能, 响应式) |
| `polish [target]` | 精炼 | 发布前最终质量检查 |
| `bolder [target]` | 精炼 | 放大保守/平淡的设计 |
| `quieter [target]` | 精炼 | 降低过度刺激的设计 |
| `distill [target]` | 精炼 | 精简到本质，去除复杂性 |
| `harden [target]` | 精炼 | 生产就绪：错误、i18n、边界情况 |
| `onboard [target]` | 精炼 | 设计首次运行流程、空状态 |
| `animate [target]` | 增强 | 添加有目的的动画和动效 |
| `colorize [target]` | 增强 | 为单色 UI 添加战略性色彩 |
| `typeset [target]` | 增强 | 改善排版层级和字体 |
| `layout [target]` | 增强 | 修复间距、节奏和视觉层级 |
| `delight [target]` | 增强 | 添加个性和记忆点 |
| `overdrive [target]` | 增强 | 突破常规限制 |
| `clarify [target]` | 修复 | 改善 UX 文案、标签和错误消息 |
| `adapt [target]` | 修复 | 适配不同设备和屏幕尺寸 |
| `optimize [target]` | 修复 | 诊断并修复 UI 性能 |
| `live` | 迭代 | 浏览器实时迭代模式 |

### 首次使用流程

```bash
# 1. 初始化项目上下文
/impeccable init

# 2. 如果已有代码但没有 DESIGN.md
/impeccable document

# 3. 开始设计审查
/impeccable critique src/pages/Home.tsx
```

### 设计规则要点

**色彩：**
- 正文对比度 ≥ 4.5:1，大文本 ≥ 3:1
- 使用 OKLCH 色彩空间
- 避免 cream/sand/beige 作为默认背景（AI 生成的典型特征）

**排版：**
- 正文行宽限制 65-75ch
- 层级通过缩放 + 字重对比（≥1.25 比率）
- 字体家族上限 3 个
- 禁止全大写正文

**布局：**
- 变化间距创造节奏
- 卡片不是万能答案，嵌套卡片永远是错的
- Flexbox 用于 1D，Grid 用于 2D

**动效：**
- 使用指数缓出曲线（ease-out-quart/quint/expo）
- 必须提供 `prefers-reduced-motion` 替代方案
- 禁止弹跳/弹性效果

### 绝对禁止项

- 侧边条纹边框（border-left > 1px 作为装饰）
- 渐变文字（background-clip: text + gradient）
- 玻璃拟态作为默认风格
- 英雄指标模板（大数字 + 小标签 + 渐变）
- 相同卡片网格无限重复
- 每个区块上方的小号大写字母标题（01 / 02 / 03）
- 文字溢出容器

---

## 协同工作流

### 场景 1：全新项目

```
1. 选择品牌方向
   cp ~/.claude/skills/awesome-design-md/design-md/stripe/DESIGN.md ./DESIGN.md

2. 生成设计系统
   /ui-ux-pro-max Design a SaaS analytics dashboard

3. 初始化 Impeccable
   /impeccable init

4. 开始构建
   /impeccable craft dashboard

5. 审计质量
   /impeccable audit src/
   /impeccable critique src/pages/Dashboard.tsx

6. 最终打磨
   /impeccable polish src/
```

### 场景 2：改进现有项目

```
1. 从现有代码提取设计系统
   /impeccable document

2. 用 Pro Max 补充缺失的设计决策
   /ui-ux-pro-max Review and enhance our design system

3. 审计当前状态
   /impeccable audit src/

4. 针对性修复
   /impeccable colorize src/components/   # 配色单调
   /impeccable typeset src/               # 排版层级不清
   /impeccable layout src/pages/          # 间距节奏问题

5. 生产就绪
   /impeccable harden src/
   /impeccable polish src/
```

### 场景 3：快速迭代

```
1. 启动浏览器实时模式
   /impeccable live

2. 在浏览器中选择元素，生成替代方案

3. 确认后应用更改
```

---

## 最佳实践

### 何时用哪个

| 需求 | 使用 |
|------|------|
| "我想要 Linear 那种感觉" | awesome-design-md → 复制 linear.app 的 DESIGN.md |
| "帮我选配色和字体" | UI/UX Pro Max → `--domain color` / `--domain typography` |
| "这个页面看起来不够专业" | Impeccable → `/impeccable critique` |
| "发布前最后检查" | Impeccable → `/impeccable polish` + `/impeccable audit` |
| "动画太生硬" | Impeccable → `/impeccable animate` |
| "设计太平淡" | Impeccable → `/impeccable bolder` |
| "设计太花哨" | Impeccable → `/impeccable quieter` |
| "需要完整设计系统" | UI/UX Pro Max → `--design-system --persist` |

### 注意事项

1. **DESIGN.md 优先级**：项目根目录的 DESIGN.md 会被所有 skill 读取，确保它反映你想要的方向
2. **不要混用冲突的品牌**：选定一个 DESIGN.md 后坚持使用，不要中途切换
3. **Impeccable 需要初始化**：首次使用必须运行 `/impeccable init` 生成 PRODUCT.md
4. **持久化设计决策**：用 UI/UX Pro Max 的 `--persist` 生成 MASTER.md 保持跨会话一致
5. **AI Slop 测试**：如果界面一眼看出是 AI 生成的，就没通过质量门禁

---

## 文件结构参考

安装后的目录结构：

```
~/.claude/skills/
├── awesome-design-md/
│   ├── README.md
│   └── design-md/
│       ├── stripe/DESIGN.md
│       ├── linear.app/DESIGN.md
│       ├── notion/DESIGN.md
│       └── ... (73 个品牌)
├── ui-ux-pro-max/
│   ├── SKILL.md
│   ├── data/           # CSV 数据库
│   │   ├── styles.csv
│   │   ├── colors.csv
│   │   ├── typography.csv
│   │   ├── charts.csv
│   │   └── stacks/    # 技术栈特定
│   └── scripts/
│       └── search.py   # BM25 搜索引擎
└── impeccable/
    ├── SKILL.md
    ├── reference/      # 各命令的详细参考
    │   ├── craft.md
    │   ├── audit.md
    │   ├── polish.md
    │   └── ...
    └── scripts/
        ├── context.mjs
        ├── palette.mjs
        ├── detect.mjs
        └── context-signals.mjs
```