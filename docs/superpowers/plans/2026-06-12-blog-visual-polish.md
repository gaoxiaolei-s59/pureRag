# puregxl.site 博客视觉升级（R2）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 首页 100vh 视频 Hero（含打字机副标题）、鼠标三件套特效、卡片倾斜、阅读进度条、导航短链接入口、视频压缩与 CI 清理，最终经本地 deploy.sh 发布。

**Architecture:** 全部新代码内聚在 `src/components/effects/`（HeroOverlay / MouseEffects / ReadingProgress），Fuwari 原文件只动挂载点与常量；鼠标/倾斜用事件委托 + 单 rAF 引擎（无三方库），天然兼容 swup 无刷新切页；视频逻辑集中在 Layout 的 body 级脚本（含移动端不加载、滚出暂停）。

**Tech Stack:** Astro + Fuwari、TypeScript/原生 Canvas、ffmpeg（视频压缩）、deploy.sh（本地构建 + rsync 发布）。

**Spec:** `docs/superpowers/specs/2026-06-12-blog-visual-polish-design.md`

**关键事实：**
- 仓库 `/Users/gaoxaiolei/IdeaProjects/puregxl.site`，构建命令前缀 `env COREPACK_ENABLE_DOWNLOAD_PROMPT=0 PATH="/opt/homebrew/opt/node@22/bin:$PATH" corepack pnpm`（下文简写 `$PNPM`）
- 常量现状 `src/constants/constants.ts`：`BANNER_HEIGHT=35`、`BANNER_HEIGHT_EXTEND=30`、`BANNER_HEIGHT_HOME=35+30`、`MAIN_PANEL_OVERLAPS_BANNER_HEIGHT=3.5`
- 视频 `public/home-banner-video.mp4`（27MB，用户提交）；banner markup 在 `src/layouts/MainGridLayout.astro` 66 行起（含 `<video>` + 黑色遮罩 div z-20）
- 正文容器类 `.custom-md`；主题色 CSS 变量 `--primary`（oklch，跟随 --hue 与深浅色）；swup 钩子用法见 Layout.astro 153 行（`window.swup.hooks.on("page:view", ...)`）
- Actions 不可用（billing 锁），**发布只用 `./deploy.sh`**；deploy.yml 保留不动

---

### Task 0: 前置准备

**Files:** 无

- [ ] **Step 1: 装 ffmpeg、确认仓库同步**

```bash
brew install ffmpeg 2>&1 | tail -1
cd /Users/gaoxaiolei/IdeaProjects/puregxl.site && git pull --ff-only && git status -sb
```

预期：ffmpeg 可用（`ffmpeg -version` 出版本号）；`## main...origin/main` 无落后无超前、工作区干净。

- [ ] **Step 2: 基线构建**

```bash
cd /Users/gaoxaiolei/IdeaProjects/puregxl.site && $PNPM build 2>&1 | tail -3
```

预期：`Finished in ...`。失败则先修再继续（基线必须绿）。

### Task 1: 视频压缩 + 首帧海报

**Files:**
- Modify: `public/home-banner-video.mp4`（压缩替换，同名）
- Create: `public/home-banner-poster.jpg`
- 备份: `~/Downloads/home-banner-video.orig.mp4`

- [ ] **Step 1: 备份 + 压缩 + 抽首帧**

```bash
cd /Users/gaoxaiolei/IdeaProjects/puregxl.site
cp public/home-banner-video.mp4 ~/Downloads/home-banner-video.orig.mp4
ffmpeg -y -i ~/Downloads/home-banner-video.orig.mp4 -vf "scale=-2:720" -c:v libx264 -crf 28 -preset slow -movflags +faststart -an public/home-banner-video.mp4
ffmpeg -y -i public/home-banner-video.mp4 -frames:v 1 -q:v 3 public/home-banner-poster.jpg
ls -lh public/home-banner-video.mp4 public/home-banner-poster.jpg
```

预期：mp4 ≤5MB（若 >5MB 把 crf 提到 30 重压一次）；poster.jpg 出现（几十~几百 KB）。`-an` 去掉音轨（banner 本来就 muted）。

- [ ] **Step 2: Commit**

```bash
git add public/home-banner-video.mp4 public/home-banner-poster.jpg && git commit -m "perf: 压缩首页视频(27MB→<5MB)并加首帧海报"
```

### Task 2: 导航短链接入口 + CI 清理

**Files:**
- Modify: `src/config.ts`（navBarConfig.links）
- Delete: `.github/workflows/biome.yml`、`.github/workflows/build.yml`

- [ ] **Step 1: navbar 加外链**

`src/config.ts` 的 `navBarConfig.links` 数组，在 GitHub 外链对象后追加：

```ts
		{
			name: "短链接",
			url: "https://link.puregxl.site",
			external: true,
		},
```

- [ ] **Step 2: 删冗余 workflow + 构建验证 + Commit**

```bash
cd /Users/gaoxaiolei/IdeaProjects/puregxl.site && git rm -q .github/workflows/biome.yml .github/workflows/build.yml
$PNPM build 2>&1 | tail -2 && grep -c "link.puregxl.site" dist/index.html
git add -A && git commit -m "feat: 导航加短链接入口；ci: 移除与 deploy 重复的模板 workflow"
```

预期：构建绿；grep ≥1。

### Task 3: 首页 100vh 视频 Hero + 打字机悬浮层

**Files:**
- Modify: `src/constants/constants.ts`（BANNER_HEIGHT_HOME）
- Modify: `src/layouts/MainGridLayout.astro`（mainPanelTop 公式、video 属性、挂 HeroOverlay）
- Modify: `src/layouts/Layout.astro`（body 级视频控制脚本）
- Create: `src/components/effects/HeroOverlay.astro`

- [ ] **Step 1: 常量解耦**

`constants.ts` 中 `export const BANNER_HEIGHT_HOME = BANNER_HEIGHT + BANNER_HEIGHT_EXTEND;` 改为：

```ts
export const BANNER_HEIGHT_HOME = 100;
```

- [ ] **Step 2: MainGridLayout 首页面板下移到 100vh**

`mainPanelTop` 三元改为（保持文章页原公式不变）：

```ts
const mainPanelTop = bannerVisible
	? isHomePage
		? `calc(${BANNER_HEIGHT_HOME}vh - ${MAIN_PANEL_OVERLAPS_BANNER_HEIGHT}rem - ${BANNER_HEIGHT_EXTEND}vh)`
		: `calc(${BANNER_HEIGHT}vh - ${MAIN_PANEL_OVERLAPS_BANNER_HEIGHT}rem)`
	: "5.5rem";
```

原理：首页 `#main-grid` 会再被 `.enable-banner.is-home` 平移 `--banner-height-extend`（≈30vh 取 4px 整），净起点 ≈ `100vh - 3.5rem`。需要 import `BANNER_HEIGHT_HOME`（该文件已 import 其他常量，从同处加）。

- [ ] **Step 3: video 标签改造（poster + 延迟决定加载）**

MainGridLayout 中用户的 `<video ...><source .../></video>` 整体替换为（去掉 `<source>` 子节点，逻辑交给 Layout 脚本）：

```astro
        {showBannerVideo && <video
            id="banner-video"
            class="absolute inset-0 z-10 h-full w-full object-cover"
            muted
            loop
            playsinline
            preload="none"
            poster="/home-banner-poster.jpg"
            data-video-src={siteConfig.banner.video?.src}
            aria-label="Homepage banner video"
        ></video>}
```

- [ ] **Step 4: 挂 HeroOverlay**

紧跟黑色遮罩 `<div class="absolute inset-0 z-20 bg-black/20 pointer-events-none"></div>` 之后、`#banner` 闭合前加：

```astro
        {isHomePage && <HeroOverlay />}
```

并在 frontmatter import：`import HeroOverlay from "../components/effects/HeroOverlay.astro";`

- [ ] **Step 5: 新建 HeroOverlay.astro（完整内容）**

```astro
---
import { siteConfig } from "../../config";
---
<div id="hero-overlay" class="absolute inset-0 z-30 flex flex-col items-center justify-center pointer-events-none text-white">
    <h1 class="text-5xl md:text-7xl font-bold tracking-wide drop-shadow-lg">{siteConfig.title}</h1>
    <p class="mt-6 h-8 text-lg md:text-2xl drop-shadow-md">
        <span id="hero-type"></span><span id="hero-caret" class="animate-pulse">|</span>
    </p>
    <button id="hero-arrow" aria-label="向下滚动" class="pointer-events-auto absolute bottom-8 left-1/2 -translate-x-1/2 text-white/90 hover:text-white transition animate-bounce text-3xl">
        ↓
    </button>
</div>
<script is:inline>
(function () {
	if (window.__pgxlHero) return;
	window.__pgxlHero = true;
	var phrases = ["记录开发与学习", "Java · 后端 · 折腾不止", "欢迎来到我的小站"];
	var pi = 0, ci = 0, deleting = false, timer = null;
	function el() { return document.getElementById("hero-type"); }
	function tick() {
		var t = el();
		if (!t) { timer = setTimeout(tick, 800); return; }
		var p = phrases[pi];
		if (!deleting) {
			ci++;
			if (ci >= p.length) { deleting = true; timer = setTimeout(tick, 2200); t.textContent = p; return; }
		} else {
			ci--;
			if (ci <= 0) { deleting = false; pi = (pi + 1) % phrases.length; }
		}
		t.textContent = p.slice(0, ci);
		timer = setTimeout(tick, deleting ? 40 : 110);
	}
	tick();
	document.addEventListener("click", function (e) {
		var a = e.target && e.target.closest && e.target.closest("#hero-arrow");
		if (!a) return;
		window.scrollTo({ top: Math.round(window.innerHeight * 0.96), behavior: "smooth" });
	});
})();
</script>
```

- [ ] **Step 6: Layout 加视频控制脚本（与 GoatCounter 脚本并列处）**

`src/layouts/Layout.astro` 的 GoatCounter `<script is:inline>` 之后加：

```html
		<script is:inline>
			// 视频策略：触屏/减动效只看海报；滚出首屏自动暂停
			(function () {
				if (window.__pgxlVideo) return;
				window.__pgxlVideo = true;
				function setup() {
					var v = document.getElementById("banner-video");
					if (!v || v.dataset.bound) return;
					v.dataset.bound = "1";
					var fine = window.matchMedia("(pointer: fine)").matches;
					var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
					if (fine && !reduced && v.dataset.videoSrc) {
						v.src = v.dataset.videoSrc;
						v.autoplay = true;
						v.play().catch(function () {});
						new IntersectionObserver(function (es) {
							es.forEach(function (en) { en.isIntersecting ? v.play().catch(function(){}) : v.pause(); });
						}, { threshold: 0.05 }).observe(v);
					}
				}
				setup();
				var h = setInterval(function () {
					if (window.swup && window.swup.hooks) {
						clearInterval(h);
						window.swup.hooks.on("page:view", setup);
					}
				}, 500);
			})();
		</script>
```

- [ ] **Step 7: 构建验证 + Commit**

```bash
cd /Users/gaoxaiolei/IdeaProjects/puregxl.site && $PNPM build 2>&1 | tail -2
grep -c "hero-overlay" dist/index.html && grep -c "banner-poster" dist/index.html
grep -c "hero-overlay" dist/posts/hello-world/index.html || echo "0（文章页无 hero，正确）"
git add -A && git commit -m "feat: 首页 100vh 视频 Hero + 打字机副标题 + 视频懒加载/滚出暂停"
```

预期：首页两个 grep ≥1；文章页为 0。

### Task 4: 鼠标效果引擎（光标圈/星尘拖尾/点击绽放）

**Files:**
- Create: `src/components/effects/MouseEffects.astro`
- Modify: `src/layouts/Layout.astro`（body 内挂载）

- [ ] **Step 1: 新建 MouseEffects.astro（完整内容）**

```astro
<canvas id="fx-canvas" style="position:fixed;inset:0;z-index:9999;pointer-events:none;"></canvas>
<script is:inline>
(function () {
	if (window.__pgxlFx) return;
	window.__pgxlFx = true;
	var fine = window.matchMedia("(pointer: fine)").matches;
	var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
	var cv = document.getElementById("fx-canvas");
	if (!fine || reduced || !cv) { if (cv) cv.remove(); return; }
	var ctx = cv.getContext("2d");
	var parts = [], mx = -100, my = -100, rx = -100, ry = -100, hover = false, paused = false;
	var color = "#7F77DD", frame = 0;
	function refreshColor() {
		var c = getComputedStyle(document.documentElement).getPropertyValue("--primary").trim();
		if (!c) return;
		ctx.fillStyle = "#000"; ctx.fillStyle = c;
		if (ctx.fillStyle !== "#000000") color = c;
	}
	function fit() { cv.width = innerWidth; cv.height = innerHeight; }
	fit(); addEventListener("resize", fit);
	refreshColor(); setInterval(refreshColor, 1500);
	document.documentElement.classList.add("pgxl-cursor");
	document.addEventListener("mousemove", function (e) {
		mx = e.clientX; my = e.clientY;
		hover = !!(e.target && e.target.closest && e.target.closest("a,button,[role=button],input,select,textarea,[data-tilt]"));
		var inProse = !!(e.target && e.target.closest && e.target.closest(".custom-md"));
		var n = inProse ? 1 : 2;
		for (var i = 0; i < n; i++)
			parts.push({ x: mx + (Math.random() * 10 - 5), y: my + (Math.random() * 10 - 5), vx: (Math.random() - 0.5) * 0.6, vy: (Math.random() - 0.5) * 0.6 - 0.3, life: 1, size: Math.random() * 2.2 + 0.8, kind: "t" });
		if (parts.length > 220) parts.splice(0, parts.length - 220);
	}, { passive: true });
	document.addEventListener("click", function (e) {
		parts.push({ x: e.clientX, y: e.clientY, life: 1, ring: 1, kind: "r" });
		for (var i = 0; i < 22; i++) {
			var a = Math.PI * 2 * i / 22, sp = Math.random() * 3 + 1.2;
			parts.push({ x: e.clientX, y: e.clientY, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp, life: 1, size: Math.random() * 2.4 + 1, kind: "b" });
		}
	}, { passive: true });
	addEventListener("blur", function () { paused = true; });
	addEventListener("focus", function () { paused = false; });
	function ga(a) { ctx.globalAlpha = Math.max(0, Math.min(1, a)); }
	function tick() {
		requestAnimationFrame(tick);
		if (paused) return;
		frame++;
		ctx.clearRect(0, 0, cv.width, cv.height);
		for (var i = parts.length - 1; i >= 0; i--) {
			var p = parts[i];
			if (p.kind === "r") {
				p.ring += 2.6; p.life -= 0.04;
				if (p.life <= 0) { parts.splice(i, 1); continue; }
				ga(p.life * 0.8); ctx.beginPath(); ctx.arc(p.x, p.y, p.ring, 0, 6.29); ctx.strokeStyle = color; ctx.lineWidth = 2; ctx.stroke();
			} else {
				p.x += p.vx; p.y += p.vy; if (p.kind === "b") p.vy += 0.06;
				p.life -= p.kind === "b" ? 0.022 : 0.03;
				if (p.life <= 0) { parts.splice(i, 1); continue; }
				ga(p.life * 0.9); ctx.beginPath(); ctx.arc(p.x, p.y, p.size * p.life, 0, 6.29); ctx.fillStyle = color; ctx.fill();
			}
		}
		if (mx >= 0) {
			rx += (mx - rx) * 0.18; ry += (my - ry) * 0.18;
			ga(0.9); ctx.beginPath(); ctx.arc(rx, ry, hover ? 24 : 13, 0, 6.29); ctx.strokeStyle = color; ctx.lineWidth = 1.5; ctx.stroke();
			ga(1); ctx.beginPath(); ctx.arc(mx, my, 2.6, 0, 6.29); ctx.fillStyle = color; ctx.fill();
		}
		ga(1);
	}
	tick();
})();
</script>
<style is:global>
	html.pgxl-cursor, html.pgxl-cursor a, html.pgxl-cursor button { cursor: none; }
	@media (pointer: coarse) { #fx-canvas { display: none; } }
</style>
```

- [ ] **Step 2: Layout 挂载 + 构建 + Commit**

`Layout.astro` 的 `<slot />` 之后（`#page-height-extend` div 之前）加一行 `<MouseEffects />`，frontmatter import：`import MouseEffects from "@components/effects/MouseEffects.astro";`

```bash
cd /Users/gaoxaiolei/IdeaProjects/puregxl.site && $PNPM build 2>&1 | tail -2 && grep -c "fx-canvas" dist/index.html
git add -A && git commit -m "feat: 鼠标特效引擎（光标圈/星尘拖尾/点击绽放，自动降级）"
```

预期：构建绿，grep ≥1。

### Task 5: 卡片 3D 倾斜 + 阅读进度条

**Files:**
- Modify: `src/components/PostCard.astro`（根元素加 data-tilt）
- Create: `src/components/effects/ReadingProgress.astro`
- Modify: `src/layouts/Layout.astro`（挂载）、`src/components/effects/MouseEffects.astro`（追加 tilt 委托）

- [ ] **Step 1: PostCard 根元素加属性**

打开 `src/components/PostCard.astro`，找到根元素（带 `card-base` class 的第一个 `<div class:list=...>`），在标签上添加 `data-tilt`。

- [ ] **Step 2: MouseEffects 追加 tilt 委托（在 `tick();` 调用行之前插入）**

```js
	var tiltEl = null;
	document.addEventListener("mouseover", function (e) {
		var t = e.target && e.target.closest && e.target.closest("[data-tilt]");
		if (t && t !== tiltEl) { tiltEl = t; t.style.transition = "transform .15s"; t.style.willChange = "transform"; }
	}, { passive: true });
	document.addEventListener("mousemove", function (e) {
		if (!tiltEl) return;
		if (!tiltEl.matches(":hover")) { tiltEl.style.transform = ""; tiltEl = null; return; }
		var r = tiltEl.getBoundingClientRect();
		var dx = (e.clientX - r.left) / r.width - 0.5, dy = (e.clientY - r.top) / r.height - 0.5;
		tiltEl.style.transform = "perspective(800px) rotateX(" + (-dy * 6).toFixed(2) + "deg) rotateY(" + (dx * 6).toFixed(2) + "deg)";
	}, { passive: true });
	document.addEventListener("mouseout", function (e) {
		if (tiltEl && e.target === tiltEl && !tiltEl.contains(e.relatedTarget)) { tiltEl.style.transform = ""; tiltEl = null; }
	}, { passive: true });
```

- [ ] **Step 3: 新建 ReadingProgress.astro（完整内容）**

```astro
<div id="reading-progress" style="position:fixed;top:0;left:0;height:2px;width:0;z-index:10000;background:var(--primary);transition:width .1s linear;"></div>
<script is:inline>
(function () {
	if (window.__pgxlProg) return;
	window.__pgxlProg = true;
	var bar = document.getElementById("reading-progress");
	function onScroll() {
		var article = document.querySelector(".custom-md");
		if (!article) { bar.style.width = "0"; return; }
		var max = document.documentElement.scrollHeight - innerHeight;
		bar.style.width = (max > 0 ? Math.min(100, (scrollY / max) * 100) : 0) + "%";
	}
	addEventListener("scroll", onScroll, { passive: true });
	onScroll();
	var h = setInterval(function () {
		if (window.swup && window.swup.hooks) { clearInterval(h); window.swup.hooks.on("page:view", onScroll); }
	}, 500);
})();
</script>
```

- [ ] **Step 4: Layout 挂载 + 构建 + Commit**

`<MouseEffects />` 旁边加 `<ReadingProgress />` 并 import（`@components/effects/ReadingProgress.astro`）。

```bash
cd /Users/gaoxaiolei/IdeaProjects/puregxl.site && $PNPM build 2>&1 | tail -2
grep -c "reading-progress" dist/posts/hello-world/index.html && grep -c "data-tilt" dist/index.html
git add -A && git commit -m "feat: 文章卡片 3D 倾斜 + 阅读进度条"
```

预期：两个 grep ≥1。

### Task 6: 本地预览自检

**Files:** 无

- [ ] **Step 1: 起预览服务并 spot check**

```bash
cd /Users/gaoxaiolei/IdeaProjects/puregxl.site && ($PNPM preview --port 4321 &) && sleep 3
curl -s http://localhost:4321/ | grep -oE "hero-overlay|fx-canvas|banner-poster|短链接" | sort | uniq -c
curl -s http://localhost:4321/posts/hello-world/ | grep -oE "reading-progress|custom-md" | sort | uniq -c
```

预期：首页四个标记齐全；文章页两个标记齐全。

- [ ] **Step 2: 浏览器肉眼验收（若 Claude Preview 工具可用则截图自检，否则提示用户开 http://localhost:4321 看）**

检查项：视频铺满首屏、打字机轮播、点击下滑箭头平滑滚动、三种鼠标效果、卡片悬停倾斜、文章页进度条、窄窗口（模拟移动）无鼠标特效。完成后 `kill %1` 关闭预览。

### Task 7: 发布与线上验证

**Files:** 无（执行 deploy.sh）

- [ ] **Step 1: push 源码 + 本地发布**

```bash
cd /Users/gaoxaiolei/IdeaProjects/puregxl.site && git push origin main; ./deploy.sh 2>&1 | tail -3
```

预期：`✅ 已部署`（push 即使触发 Actions 失败也不影响——deploy.yml 在 billing 解锁前会红，忽略）。

- [ ] **Step 2: 线上 spot check**

```bash
curl -s --resolve puregxl.site:443:101.35.5.210 https://puregxl.site/ | grep -oE "hero-overlay|fx-canvas|banner-poster|短链接" | sort | uniq -c
curl -sI --resolve puregxl.site:443:101.35.5.210 https://puregxl.site/home-banner-video.mp4 | grep -iE "content-length|HTTP"
curl -s --resolve puregxl.site:443:101.35.5.210 https://puregxl.site/posts/hello-world/ | grep -c "reading-progress"
ssh ubuntu@101.35.5.210 'systemctl is-active nginx goatcounter && sudo docker ps --format "{{.Names}}: {{.Status}}" | head -3'
```

预期：首页标记齐全；视频 Content-Length ≤ 5*1024*1024；文章页有进度条；服务全 active/Up。

### Task 8: 收尾

- [ ] **Step 1: 按 spec 第 5 节验收清单逐项核对并记录结果**
- [ ] **Step 2: 在 RagTest 仓库把本计划文件的复选框勾掉并提交；向用户交付总结（含：原视频备份位置、打字机文案改法 phrases 数组、Actions 解锁后自动恢复说明）**

---

## Self-Review 记录

1. **Spec 覆盖**：§2 决策表 → 视频压缩/海报(T1)、Hero+打字机+性能策略(T3)、鼠标三件套(T4)、倾斜+进度条(T5)、导航外链+CI 清理(T2)、deploy.sh 发布(T7)；§5 验收 1-7 → T6/T7/T8。无缺口。
2. **占位符扫描**：所有代码均完整给出；唯一运行时决定值是 crf 重压条件（已给阈值与动作）。
3. **一致性**：id 命名 fx-canvas/hero-overlay/hero-type/hero-arrow/banner-video/reading-progress、guard 变量 __pgxlFx/__pgxlHero/__pgxlVideo/__pgxlProg、`$PNPM` 前缀、`data-tilt` 属性在各任务间一致；HeroOverlay import 路径 `../../config`（位于 components/effects/ 下两级）正确。
