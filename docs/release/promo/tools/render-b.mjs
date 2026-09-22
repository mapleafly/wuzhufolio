#!/usr/bin/env node
/**
 * P7 产品宣传动画 · 方向板 B「暗夜金库 Vault Noir」渲染器
 *
 * 做两件事：
 *   1) 把真实 UI 截图（assets/ui/dashboard-dark.png）转 base64 内联进 direction-b.html
 *      —— 脚本内联，不手工粘贴 base64；已内联则跳过（幂等）。
 *   2) 用 Playwright 以 1920×1080 渲染 direction-b.html → boards/direction-b.png，并做交付校验。
 *
 * 技术约束（与 tools/capture-ui.mjs 一致，缺一即豆腐块 / 起不来）：
 *   - 本机（WSL2 Ubuntu）无 CJK 系统字体 → 必须先起静态服务：
 *       python3 -m http.server 8791 --bind 127.0.0.1 &      # 仓库根目录
 *     页面经 http://127.0.0.1:8791/... 打开，@font-face 才能取到内嵌 Noto/JetBrains。
 *   - playwright 取自技能依赖（ESM 不认 NODE_PATH，故 createRequire 按路径解析）。
 *   - 必须带 LD_LIBRARY_PATH=$HOME/.local/plibs/root/usr/lib/x86_64-linux-gnu 运行。
 *
 * 用法：
 *   LD_LIBRARY_PATH=$HOME/.local/plibs/root/usr/lib/x86_64-linux-gnu \
 *     node docs/release/promo/tools/render-b.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, '../../../..');
const SKILL_ROOT = path.join(REPO, '.agents/skills/huashu-design');
const { chromium } = createRequire(path.join(SKILL_ROOT, 'package.json'))('playwright');

const PAGE = path.resolve(HERE, '../direction-b.html');
const HERO_PNG = path.resolve(HERE, '../assets/ui/dashboard-dark.png');
const OUT_PNG = path.resolve(HERE, '../boards/direction-b.png');
const BASE = process.env.WZ_BASE || 'http://127.0.0.1:8791';
const URL_PAGE = `${BASE}/docs/release/promo/direction-b.html`;
const CHROME = path.join(process.env.HOME ?? '', '.cache/ms-playwright/chromium-1234/chrome-linux64/chrome');

// ---- 1) 内联真实截图（只做一次；二次运行不再改文件）---------------------------------
let html = fs.readFileSync(PAGE, 'utf8');
if (html.includes('__HERO_PNG_B64__')) {
  const b64 = fs.readFileSync(HERO_PNG).toString('base64');
  html = html.replace('__HERO_PNG_B64__', `data:image/png;base64,${b64}`);
  fs.writeFileSync(PAGE, html);
  console.log(`✓ 已内联 hero 截图 ${path.relative(REPO, HERO_PNG)} → ${(b64.length / 1024 / 1024).toFixed(2)} MB base64`);
} else {
  console.log('· hero 截图已内联，跳过');
}
fs.mkdirSync(path.dirname(OUT_PNG), { recursive: true });

// ---- 2) 渲染 + 校验 ---------------------------------------------------------------
const browser = await chromium.launch({
  executablePath: fs.existsSync(CHROME) ? CHROME : undefined,
  args: ['--font-render-hinting=none', '--force-color-profile=srgb'],
});
const ctx = await browser.newContext({
  viewport: { width: 1920, height: 1080 },
  deviceScaleFactor: 1,
  reducedMotion: 'no-preference',
});
const page = await ctx.newPage();

const errors = [];
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
page.on('console', (m) => {
  const where = m.location()?.url || '';
  if (m.type() !== 'error') return;
  // favicon 404 与页面无关（本机静态服务无 favicon.ico），按约定忽略
  if (/favicon/i.test(m.text()) || /favicon/i.test(where)) return;
  errors.push(`console: ${m.text()}${where ? ` @ ${where}` : ''}`);
});

await page.goto(URL_PAGE, { waitUntil: 'load' });
await page.evaluate(() => document.fonts.ready);
await page.waitForTimeout(700);

const report = await page.evaluate(async () => {
  const fam = (f, w, s) => document.fonts.check(`${w} 20px "${f}"`, s);
  const probes = {
    'Noto Sans SC': fam('Noto Sans SC', 400, '暗夜金库零遥测设备边界出站白名单'),
    'Noto Serif SC': fam('Noto Serif SC', 600, '暗夜金库光从数据里来'),
    'JetBrains Mono': fam('JetBrains Mono', 400, 'api.coingecko.com 0123456789 #D0A85C'),
  };
  const box = (sel) => {
    const el = document.querySelector(sel);
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) };
  };
  // 逐文本节点做字形覆盖检查：任何一段文字在契约字体里画不出来 = 潜在豆腐块
  const tofu = [];
  const walk = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  for (let n = walk.nextNode(); n; n = walk.nextNode()) {
    const t = n.textContent.trim();
    if (!t || !/[\u3400-\u9FFF\uF900-\uFAFF]/.test(t)) continue;
    const cs = getComputedStyle(n.parentElement);
    if (!document.fonts.check(`${cs.fontWeight} ${cs.fontSize} ${cs.fontFamily}`, t)) {
      tofu.push(`[${cs.fontFamily.split(',')[0]}] ${t.slice(0, 24)}`);
    }
  }
  // 缺字兜底：页面里任何 U+FFFD 或空渲染宽度
  const bad = /\b(undefined|NaN)\b/.test(document.body.innerText);
  const img = document.querySelector('.shot');

  // 文字互不重叠 / 不出血：逐文本节点取 Range 盒，两两求交
  const runs = [];
  const walk2 = document.createTreeWalker(document.querySelector('.stage'), NodeFilter.SHOW_TEXT);
  for (let n = walk2.nextNode(); n; n = walk2.nextNode()) {
    const t = n.textContent.trim();
    if (!t) continue;
    const r = document.createRange();
    r.selectNodeContents(n);
    for (const b of r.getClientRects()) {
      if (b.width < 1 || b.height < 1) continue;
      runs.push({ t: t.slice(0, 16), x: b.x, y: b.y, w: b.width, h: b.height, p: n.parentElement.className });
    }
  }
  const overlaps = [];
  for (let i = 0; i < runs.length; i++) {
    for (let j = i + 1; j < runs.length; j++) {
      const a = runs[i], b = runs[j];
      const ox = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x);
      const oy = Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y);
      if (ox > 0.5 && oy > 0.5) overlaps.push(`「${a.t}」×「${b.t}」(${ox.toFixed(0)}×${oy.toFixed(0)}px)`);
    }
  }
  const bleed = runs.filter((r) => r.x < -0.5 || r.y < -0.5 || r.x + r.w > 1920.5 || r.y + r.h > 1080.5)
    .map((r) => `「${r.t}」@${r.x.toFixed(0)},${r.y.toFixed(0)}+${r.w.toFixed(0)}`);
  // 文字压色块（如色板色块）也算重叠
  const blocks = [...document.querySelectorAll('.sw i')].map((el) => el.getBoundingClientRect());
  const onBlocks = [];
  for (const r of runs) {
    for (const b of blocks) {
      const ox = Math.min(r.x + r.w, b.x + b.width) - Math.max(r.x, b.x);
      const oy = Math.min(r.y + r.h, b.y + b.height) - Math.max(r.y, b.y);
      if (ox > 0.5 && oy > 0.5) onBlocks.push(`「${r.t}」压色块`);
    }
  }
  return {
    fonts: probes,
    tofu,
    undefinedOrNaN: bad,
    textRuns: runs.length,
    overlaps,
    bleed,
    onBlocks,
    hero: box('.screen'),
    stageScroll: { w: document.documentElement.scrollWidth, h: document.documentElement.scrollHeight },
    imgLoaded: img ? img.complete && img.naturalWidth > 0 : false,
    imgNatural: img ? `${img.naturalWidth}×${img.naturalHeight}` : null,
    hasReflect: CSS.supports('-webkit-box-reflect', 'below 1px'),
  };
});

// 合成后实测：界面必须以暗色坐在暗场里（不是被冲淡的浅灰面板）
const win = report.hero;
const probe = await ctx.newPage();
async function px(x, y) {
  const buf = await page.screenshot({ clip: { x, y, width: 6, height: 6 } });
  await probe.setContent(`<img id="i" src="data:image/png;base64,${buf.toString('base64')}">`);
  return probe.evaluate(() => {
    const i = document.getElementById('i');
    const c = document.createElement('canvas');
    c.width = i.naturalWidth; c.height = i.naturalHeight;
    const g = c.getContext('2d');
    g.drawImage(i, 0, 0);
    const d = g.getImageData(3, 3, 1, 1).data;
    return { hex: '#' + [d[0], d[1], d[2]].map((v) => v.toString(16).padStart(2, '0')).join(''), lum: (0.2126 * d[0] + 0.7152 * d[1] + 0.0722 * d[2]) / 255 };
  });
}
const uiSample = await px(win.x + 60, win.y + win.h - 120);   // 窗口内暗面（侧栏空白）
const roomSample = await px(300, 900);                        // 板底暗场
await probe.close();

await page.screenshot({ path: OUT_PNG, clip: { x: 0, y: 0, width: 1920, height: 1080 } });
await browser.close();

// ---- 3) 校验输出 ---------------------------------------------------------------
const st = fs.statSync(OUT_PNG);
const problems = [];
if (errors.length) problems.push(`页面错误 ${errors.length} 条`);
if (Object.entries(report.fonts).some(([, ok]) => !ok)) problems.push('契约字体未就绪');
if (report.tofu.length) problems.push(`疑似豆腐块 ${report.tofu.length} 处`);
if (report.undefinedOrNaN) problems.push('画面含 undefined/NaN');
if (!report.imgLoaded) problems.push('hero 截图未加载');
if (report.stageScroll.w !== 1920 || report.stageScroll.h !== 1080) problems.push('画布溢出');
if (report.overlaps.length) problems.push(`文字重叠 ${report.overlaps.length} 组`);
if (report.bleed.length) problems.push(`文字出血 ${report.bleed.length} 处`);
if (report.onBlocks.length) problems.push(`文字压色块 ${report.onBlocks.length} 处`);
if (uiSample.lum > 0.26) problems.push(`界面不够暗（实测 ${uiSample.hex}）`);

console.log(`✓ 页面错误 pageerror+console = ${errors.length}`);
errors.slice(0, 8).forEach((e) => console.log('   ! ' + e));
console.log(`✓ 字体就绪 ${JSON.stringify(report.fonts)}`);
console.log(`✓ 豆腐块扫描 ${report.tofu.length === 0 ? '0 处' : report.tofu.join(' | ')}`);
console.log(`✓ 文字排布：${report.textRuns} 段文本，重叠 ${report.overlaps.length} 组，出血 ${report.bleed.length} 处，压色块 ${report.onBlocks.length} 处`);
[...report.overlaps, ...report.bleed, ...report.onBlocks].slice(0, 8).forEach((x) => console.log('   ! ' + x));
console.log(`✓ hero 截图 ${report.imgNatural} 内联加载=${report.imgLoaded} 投影框=${JSON.stringify(report.hero)}`);
console.log(`✓ 暗色校验：界面内实测 ${uiSample.hex}（相对亮度 ${uiSample.lum.toFixed(3)}），板底暗场 ${roomSample.hex}`);
console.log(`✓ 画布 ${report.stageScroll.w}×${report.stageScroll.h}（body 1920×1080）`);
console.log(`✓ 产物 ${path.relative(REPO, OUT_PNG)}  ${(st.size / 1024).toFixed(0)} KB`);
console.log(problems.length ? `\n✗ 未通过：${problems.join('；')}` : '\n✓ 全部校验通过');
process.exit(problems.length ? 1 : 0);
