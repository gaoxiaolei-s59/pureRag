# puregxl.site 博客视觉升级（R2）设计

日期：2026-06-12
状态：已获用户批准（部署渠道按用户要求改为本地 deploy.sh，Actions 保留待解锁）
前置：博客已上线（见 2026-06-12-personal-site-design.md），用户自行加了首页视频 banner（27MB mp4，banner 高度 65vh）

## 1. 需求（用户原话拆解）

1. 短链接项目加一个链接 → 导航栏外链入口
2. 主页背景图（实为视频 banner）不能覆盖全部 → 首屏 100vh 视频 Hero
3. 优化前端 + 鼠标效果 + 多做动态效果，参考 haowallpaper.com → 鼠标三件套（用户在演示板试玩后全选）+ 页面动效（用户授权我定：打字机/卡片倾斜/进度条，不做全站飘落粒子——与拖尾叠加过闹）

## 2. 决策表

| 决策点 | 结论 |
|---|---|
| Hero | 首页 banner 高度 65vh→100vh；悬浮层=站名+打字机副标题（轮播数组）+下滑箭头；文章页不变 |
| 视频 | ffmpeg 压 27MB→3~5MB（720p H.264 CRF≈28），首帧海报图做 poster；原 mp4 本地备份 |
| 视频性能 | 滚过首屏 IntersectionObserver 暂停；移动端(pointer:coarse)与 prefers-reduced-motion 不加载视频只显示海报 |
| 鼠标效果 | 光标圈+星尘拖尾+点击绽放，全选；自写引擎（无三方库，≤8KB），全局 canvas + 单 rAF |
| 页面动效 | 文章卡片 3D 倾斜（桌面 hover，≤6°+高光）；文章页顶部 2px 阅读进度条 |
| 导航 | navBarConfig.links 加「短链接」外链 → https://link.puregxl.site |
| CI | 删模板自带 biome.yml/build.yml（与 deploy 构建重复且持续红叉）；deploy.yml 保留 |
| 部署渠道 | **本地 ./deploy.sh（构建+rsync）**——用户确认 Actions 暂不可用（billing 锁）；解锁后 push 自动部署自然恢复 |

## 3. 组件设计（实现方式：独立模块，最小侵入 Fuwari）

新增 `src/components/effects/` 内聚全部新代码；Fuwari 原文件只动挂载点与常量：

- `HeroOverlay.astro`：站名/打字机/下滑箭头悬浮层（仅 isHomePage 渲染，置于 banner-wrapper 内 z-30）
- `MouseEffects.astro`（含内联 script）：canvas 覆盖层引擎；禁用条件 pointer:coarse / prefers-reduced-motion / 窗口 blur 暂停；正文 .custom-md 区域内拖尾密度减半；颜色取 CSS 变量 --primary（hue 自适应深浅色） 
- `ReadingProgress.astro`：文章页（有 headings 时）顶部进度条
- 卡片倾斜：PostCard 上加 data-tilt 属性 + 效果引擎统一处理（transform rotateX/Y，mouseleave 复位）
- 挂载点：Layout.astro（MouseEffects/ReadingProgress）、MainGridLayout.astro（HeroOverlay）；constants.ts 改 BANNER_HEIGHT_HOME=100
- swup 兼容：引擎挂 body 级一次初始化，切页不重复绑定；打字机在 swup 回首页时重启

## 4. 不做（YAGNI）

全站飘落粒子（列为未来可选）；自定义鼠标指针图片；hero 视频换源/多源；Decap CMS。

## 5. 验收标准

1. 首页进入：视频铺满整屏，打字机逐字轮播，下滑箭头滚动到文章区；文章页 banner 行为与现状一致
2. 压缩后视频 ≤5MB，桌面端自动播放，移动端显示海报图不下载视频
3. 桌面端三种鼠标效果全部生效且跟随主题色；触屏/减动效设置下完全关闭；swup 切几页后仍正常
4. 卡片悬停倾斜+高光；文章页进度条随滚动增长
5. 导航栏出现「短链接」外链
6. 仓库不再有 biome.yml/build.yml；push 后 GitHub 只有 Deploy Blog 一个 workflow（失败可接受，billing 锁外因）
7. 线上由 ./deploy.sh 发布成功，既有服务无影响

## 6. 回滚

git revert 对应提交 + ./deploy.sh 重发；视频原件备份在本地 ~/Downloads/home-banner-video.orig.mp4
