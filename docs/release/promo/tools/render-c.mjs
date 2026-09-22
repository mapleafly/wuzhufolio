#!/usr/bin/env node
/**
 * P7 产品宣传动画 · 方向板 C 渲染（docs/release/promo/direction-c.html → boards/direction-c.png）
 *
 * 技术约束对齐 tools/capture-ui.mjs：
 *  - 本机（WSL2 Ubuntu）无 CJK 系统字体（`fc-list :lang=zh` 为空）→ 页面经 http://127.0.0.1:8791 载入，
 *    页面内 @font-face 指向仓库内嵌 Noto Sans SC / Noto Serif SC / JetBrains Mono（ui/src/main/resources/fonts）。
 *  - playwright 取自项目级挂载的 huashu-design 技能依赖（ESM 不认 NODE_PATH，按路径 createRequire）。
 *  - 运行需带 LD_LIBRARY_PATH=$HOME/.local/plibs/root/usr/lib/x86_64-linux-gnu（缺系统 lib）。
 *
 * 用法：
 *   python3 -m http.server 8791 --bind 127.0.0.1 &          # 仓库根目录（若已在跑则复用）
 *   LD_LIBRARY_PATH=$HOME/.local/plibs/root/usr/lib/x86_64-linux-gnu \
 *     node docs/release/promo/tools/render-c.mjs
 *
 * 校验（任一不过即 exit 1）：pageerror=0 / console error=0（favicon 404 忽略）/ 无 undefined、NaN /
 *      三款字体已加载 / 豆腐块探测（不同汉字位图不得同形）/ 文字盒两两不重叠 / 文字不出血不被裁切 / 产出恰为 1920×1080。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, '../../../..');
const SKILL_ROOT = path.resolve(REPO, '.agents/skills/huashu-design');
const { chromium } = createRequire(path.join(SKILL_ROOT, 'package.json'))('playwright');

const BASE = process.env.WZ_BASE || 'http://127.0.0.1:8791';
const PAGE_URL = `${BASE}/docs/release/promo/direction-c.html`;
const OUT = path.resolve(REPO, 'docs/release/promo/boards/direction-c.png');
const W = 1920, H = 1080;

function findChromium() {
  const cache = path.join(process.env.HOME ?? '', '.cache/ms-playwright');
  if (!fs.existsSync(cache)) return undefined;
  const cands = [];
  for (const dir of fs.readdirSync(cache)) {
    for (const rel of ['chrome-linux64/chrome', 'chrome-linux/chrome', 'chrome-headless-shell-linux64/chrome-headless-shell']) {
      const p = path.join(cache, dir, rel);
      if (fs.existsSync(p)) cands.push(p);
    }
  }
  return cands.sort((a, b) => {
    const score = (s) => (s.includes('headless-shell') ? 0 : 1) * 100000 + (Number(s.match(/(\d{4})/)?.[1]) || 0);
    return score(b) - score(a);
  })[0];
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
const browser = await chromium.launch({
  executablePath: findChromium(),
  args: ['--font-render-hinting=none', '--force-color-profile=srgb', '--hide-scrollbars'],
});
const ctx = await browser.newContext({ viewport: { width: W, height: H }, deviceScaleFactor: 1, reducedMotion: 'reduce' });
const page = await ctx.newPage();

const isFavicon = (u = '') => /favicon/i.test(u);
const errors = [];
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
page.on('console', (m) => {
  if (m.type() === 'error' && !isFavicon(m.location()?.url) && !isFavicon(m.text())) errors.push(`console: ${m.text()}`);
});
page.on('requestfailed', (r) => { if (!isFavicon(r.url())) errors.push(`requestfailed: ${r.url()} ${r.failure()?.errorText}`); });

await page.goto(PAGE_URL, { waitUntil: 'load' });
await page.evaluate(() => document.fonts.ready);
await page.evaluate(() => Promise.all(Array.from(document.images).map((i) => (i.complete ? 0 : i.decode().catch(() => 0)))));
await page.waitForTimeout(400);

// —— 校验 1：字体真实加载（本机无 CJK 系统字体，任一 false 即豆腐块）——
const fonts = await page.evaluate(() => {
  const probe = [
    ["12px 'Noto Sans SC'", '本地机器资产列表加密零遥测开源'],
    ["12px 'Noto Serif SC'", '一份可以逐行核对的规格书'],
    ["12px 'JetBrains Mono'", 'WUZHUFOLIO 0123456789'],
  ];
  const faces = [];
  document.fonts.forEach((f) => faces.push(`${f.family}|${f.status}`));
  return { checks: probe.map(([f, t]) => [f, document.fonts.check(f, t)]), faces };
});

// —— 校验 2：豆腐块探测（不同汉字若渲染为同形＝缺字方框）——
const tofu = await page.evaluate(() => {
  const chars = '本地机器加密数据遥测规格书';
  const map = new Map();
  let dupes = 0;
  for (const ch of chars) {
    const c = document.createElement('canvas');
    c.width = 40; c.height = 40;
    const g = c.getContext('2d');
    g.fillStyle = '#fff'; g.fillRect(0, 0, 40, 40);
    g.fillStyle = '#000'; g.font = "32px 'Noto Sans SC', sans-serif";
    g.fillText(ch, 2, 32);
    const s = c.toDataURL();
    if (map.has(s)) dupes++; else map.set(s, ch);
  }
  return { n: chars.length, dupes };
});

// —— 校验 3：文案无占位符；文字盒两两不重叠、不出血 ——
const text = await page.evaluate(() => document.getElementById('stage').innerText);
const bad = ['undefined', 'NaN', '[object'].filter((t) => text.includes(t));

const layout = await page.evaluate(() => {
  const stage = document.getElementById('stage');
  const boxes = [];
  const leafText = (el) => {
    const own = Array.from(el.childNodes).some((n) => n.nodeType === 3 && n.textContent.trim());
    const kids = Array.from(el.children).some((c) => c.textContent.trim());
    return own && !kids;
  };
  (function walk(el) {
    for (const c of el.children) {
      const r = c.getBoundingClientRect();
      if (leafText(c) && r.width > 0 && r.height > 0) {
        boxes.push({ t: c.textContent.trim().replace(/\s+/g, ' ').slice(0, 20), x: +r.x.toFixed(1), y: +r.y.toFixed(1), w: +r.width.toFixed(1), h: +r.height.toFixed(1) });
      }
      walk(c);
    }
  })(stage);
  const overlaps = [];
  for (let i = 0; i < boxes.length; i++) for (let j = i + 1; j < boxes.length; j++) {
    const a = boxes[i], b = boxes[j];
    const ox = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x);
    const oy = Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y);
    if (ox > 0.5 && oy > 0.5) overlaps.push(`「${a.t}」×「${b.t}」 ${ox.toFixed(1)}×${oy.toFixed(1)}px`);
  }
  const bleed = boxes.filter((b) => b.x < 0 || b.y < 0 || b.x + b.w > 1920 || b.y + b.h > 1080)
    .map((b) => `「${b.t}」@${b.x},${b.y} ${b.w}×${b.h}`);

  // 分隔线/引出线不得横穿文字（文字盒检查看不到这一类缺陷）
  const rules = [];
  stage.querySelectorAll('.hair,.inkline,.reg').forEach((el) => {
    const r = el.getBoundingClientRect();
    if (r.width > 0 && r.height > 0) rules.push({ x: r.x, y: r.y, w: r.width, h: r.height });
  });
  const struck = [];
  for (const b of boxes) {
    const own = Array.from(stage.querySelectorAll('.online')).some((el) => el.textContent.trim() === b.t);
    if (own) continue; // 出口分析的分支标签＝线上的挖白标注，本身压在引出线上是设计意图
    for (const r of rules) {
      const ox = Math.min(b.x + b.w, r.x + r.w) - Math.max(b.x, r.x);
      const oy = Math.min(b.y + b.h, r.y + r.h) - Math.max(b.y, r.y);
      if (ox > 0.5 && oy > 0.5) { struck.push(`「${b.t}」被 ${r.w.toFixed(0)}×${r.h.toFixed(0)} 的线穿过 ${ox.toFixed(1)}×${oy.toFixed(1)}px`); break; }
    }
  }
  return { n: boxes.length, overlaps, bleed, struck, rules: rules.length };
});

await page.screenshot({ path: OUT, clip: { x: 0, y: 0, width: W, height: H } });
const bytes = fs.statSync(OUT).size;
const dim = await page.evaluate(() => {
  const i = document.getElementById('uiplane');
  return { ui: [i.getBoundingClientRect().width, i.getBoundingClientRect().height], natural: [i.naturalWidth, i.naturalHeight] };
});
const png = fs.readFileSync(OUT);
const pngDim = [png.readUInt32BE(16), png.readUInt32BE(20)];
await browser.close();

// —— 报告 ——
console.log('字体加载检查:');
for (const [f, ok] of fonts.checks) console.log(`  ${ok ? '✓' : '✗'} ${f}`);
console.log(`  faces: ${fonts.faces.join(' , ')}`);
console.log(`豆腐块探测: ${tofu.dupes === 0 ? `✓ 无同形（取样 ${tofu.n} 字）` : `✗ ${tofu.dupes} 组同形`}`);
console.log(`文案占位符: ${bad.length === 0 ? '✓ 无 undefined / NaN' : `✗ ${bad.join(',')}`}`);
console.log(`文字盒检查: ${layout.overlaps.length === 0 ? `✓ ${layout.n} 个文字盒无重叠` : `✗ ${layout.overlaps.length} 处重叠`}`);
layout.overlaps.slice(0, 12).forEach((o) => console.error('  ✗ 重叠 ' + o));
console.log(`出血/裁切检查: ${layout.bleed.length === 0 ? '✓ 全部在画幅内' : `✗ ${layout.bleed.length} 处越界`}`);
layout.bleed.slice(0, 8).forEach((o) => console.error('  ✗ 越界 ' + o));
console.log(`分隔线压字检查: ${layout.struck.length === 0 ? `✓ ${layout.rules} 条线均不穿字` : `✗ ${layout.struck.length} 处压字`}`);
layout.struck.slice(0, 8).forEach((o) => console.error('  ✗ 压字 ' + o));
console.log(`hero 真实截图: 版面 ${layout.n ? '' : ''}${dim.ui[0]}×${dim.ui[1]} css px（源 ${dim.natural[0]}×${dim.natural[1]} @2x）`);
console.log(`产出: ${path.relative(process.cwd(), OUT)}  ${bytes} B  ${pngDim[0]}×${pngDim[1]}`);
console.log(`页面错误: ${errors.length}`);

let fail = 0;
if (errors.length) { errors.slice(0, 10).forEach((e) => console.error('  ✗ ' + e)); fail = 1; }
if (fonts.checks.some(([, ok]) => !ok)) fail = 1;
if (tofu.dupes > 0 || bad.length) fail = 1;
if (layout.overlaps.length || layout.bleed.length || layout.struck.length) fail = 1;
if (pngDim[0] !== W || pngDim[1] !== H || bytes < 20000) fail = 1;
if (fail) { console.error('\n✗ 渲染校验未通过'); process.exit(1); }
console.log('\n✓ 渲染校验通过（pageerror=0 / console error=0 / 字体已加载 / 无豆腐块 / 无文字重叠 / 无线穿字 / 无出血 / 1920×1080）');
