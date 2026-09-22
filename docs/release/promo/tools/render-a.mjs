#!/usr/bin/env node
/**
 * P7 产品宣传动画 · 方向板 A《账簿 The Ledger》渲染与校验
 *
 * 依赖与 capture-ui.mjs 完全一致的三个技术约束（本机 WSL2 Ubuntu，无 CJK 系统字体）：
 *   1. 页面必须经静态服务打开（字体同源，file:// 会被 CORS 拦掉）：
 *        python3 -m http.server 8791 --bind 127.0.0.1 &      # 仓库根目录
 *   2. 字体由页面内 @font-face 指向 http://127.0.0.1:8791/ui/src/main/resources/fonts/*.ttf
 *   3. playwright 按路径从技能依赖解析（ESM 不认 NODE_PATH），launch 传缓存里的 chromium 可执行文件
 *
 * 用法：
 *   LD_LIBRARY_PATH=$HOME/.local/plibs/root/usr/lib/x86_64-linux-gnu \
 *     node docs/release/promo/tools/render-a.mjs
 *
 * 产出：docs/release/promo/boards/direction-a.png（1920×1080，deviceScaleFactor=1）
 * 校验：pageerror=0 / console error=0（忽略 favicon 404）/ 无 undefined·NaN / 字体族确实生效 /
 *      正文与标题字形完整（豆腐块检测：逐字测量与「空字形」基准宽度比对）。
 * 复核图（非交付物）：/tmp/wzf/zoom-*.png —— 2x 放大局部，用于人工目视确认无豆腐块。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SKILL_ROOT = path.resolve(HERE, '../../../../.agents/skills/huashu-design');
const { chromium } = createRequire(path.join(SKILL_ROOT, 'package.json'))('playwright');

const OUT = path.resolve(HERE, '../boards/direction-a.png');
const ZOOM_DIR = '/tmp/wzf';
const BASE = process.env.WZ_BASE || 'http://127.0.0.1:8791';
const PAGE_URL = `${BASE}/docs/release/promo/direction-a.html`;

/** 本机 playwright 包版本与已缓存构建号不一致 → 显式解析缓存里的 chromium。 */
function findChromium() {
  const pinned = path.join(process.env.HOME ?? '', '.cache/ms-playwright/chromium-1234/chrome-linux64/chrome');
  if (fs.existsSync(pinned)) return pinned;
  const cache = path.join(process.env.HOME ?? '', '.cache/ms-playwright');
  if (!fs.existsSync(cache)) return undefined;
  const cands = [];
  for (const dir of fs.readdirSync(cache)) {
    for (const rel of ['chrome-linux64/chrome', 'chrome-linux/chrome']) {
      const p = path.join(cache, dir, rel);
      if (fs.existsSync(p)) cands.push(p);
    }
  }
  return cands.sort((a, b) => (Number(b.match(/(\d{4})/)?.[1]) || 0) - (Number(a.match(/(\d{4})/)?.[1]) || 0))[0];
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.mkdirSync(ZOOM_DIR, { recursive: true });

const browser = await chromium.launch({
  executablePath: findChromium(),
  args: ['--font-render-hinting=none', '--force-color-profile=srgb'],
});
const ctx = await browser.newContext({
  viewport: { width: 1920, height: 1080 },
  deviceScaleFactor: 1,
  reducedMotion: 'reduce',
});
const page = await ctx.newPage();

const errors = [];
const isFavicon = (u = '') => /favicon/i.test(u);
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
page.on('console', (m) => {
  // 资源 404 的 console 文案不含 URL，取 location().url 判定，favicon 404 按约定忽略
  if (m.type() === 'error' && !isFavicon(m.location()?.url)) errors.push(`console: ${m.text()} @ ${m.location()?.url ?? '?'}`);
});
page.on('response', (r) => {
  if (r.status() >= 400 && !isFavicon(r.url())) errors.push(`http ${r.status()}: ${r.url()}`);
});
page.on('requestfailed', (r) => {
  if (!isFavicon(r.url())) errors.push(`requestfailed: ${r.url()} ${r.failure()?.errorText ?? ''}`);
});

await page.goto(PAGE_URL, { waitUntil: 'load' });
await page.evaluate(() => document.fonts.ready);
await page.evaluate(async () => {
  await Promise.all([...document.images].map((i) => (i.complete ? null : i.decode())));
});
await page.waitForTimeout(500);

// ——— 校验：文字完整性 / 字体生效 / 尺寸 ———
const audit = await page.evaluate(() => {
  const board = document.querySelector('.board');
  const rect = board.getBoundingClientRect();
  const text = board.innerText;

  // 字体族是否真的生效：与「不存在的字族」基准宽度比对（相同 = 落回默认字体 = 没生效）
  const cv = document.createElement('canvas');
  const cx = cv.getContext('2d');
  const width = (family, str) => { cx.font = `40px ${family}`; return cx.measureText(str).width; };
  const FAM = { sans: "'Noto Sans SC'", serif: "'Noto Serif SC'", mono: "'JetBrains Mono','Noto Sans SC'" };
  const applied = {};
  for (const [k, f] of Object.entries(FAM)) {
    const sample = k === 'mono' ? '0123456789 #EDE8DF 账簿' : '账簿私密克制可信 100%';
    applied[k] = Math.abs(width(f, sample) - width("'__NoSuchFontFamily__'", sample)) > 0.5;
  }

  // 豆腐块检测：把每个唯一字符画进离屏 canvas，与其字族的「缺字方框」(.notdef, 私用区 U+E123) 位图比对。
  // 等宽字体所有字形推进宽度相同（宽度判不出缺字），必须比位图；全空白同样判为异常。
  const tofu = (() => {
    const S = 44, cv = document.createElement('canvas');
    cv.width = S * 2; cv.height = S * 2;
    const c = cv.getContext('2d', { willReadFrequently: true });
    const sig = (font, ch) => {
      c.clearRect(0, 0, cv.width, cv.height);
      c.font = font; c.textBaseline = 'top'; c.fillStyle = '#000';
      c.fillText(ch, 8, 8);
      const d = c.getImageData(0, 0, cv.width, cv.height).data;
      let h = 0, ink = 0;
      for (let i = 3; i < d.length; i += 4) if (d[i] > 8) { ink++; h = (h * 31 + i) >>> 0; }
      return { h, ink };
    };
    const out = [], seen = new Set(), refs = {};
    const walker = document.createTreeWalker(document.querySelector('.board'), NodeFilter.SHOW_TEXT);
    for (let n = walker.nextNode(); n; n = walker.nextNode()) {
      const el = n.parentElement;
      if (!el || !n.textContent.trim()) continue;
      const font = getComputedStyle(el).font;
      if (!refs[font]) refs[font] = sig(font, '\uE123');
      for (const ch of n.textContent) {
        if (/\s/.test(ch) || seen.has(font + ch)) continue;
        seen.add(font + ch);
        const s = sig(font, ch);
        if (s.ink === 0) out.push({ ch, why: 'blank', font: font.slice(0, 26) });
        else if (s.h === refs[font].h) out.push({ ch, why: 'notdef', font: font.slice(0, 26) });
      }
    }
    return out;
  })();

  const img = document.querySelector('.plate-clip img');
  return {
    board: { w: rect.width, h: rect.height },
    fontsLoaded: [...document.fonts].filter((f) => f.status === 'loaded').map((f) => f.family + '/' + f.weight),
    fontApplied: applied,
    tofu,
    hero: { src: img?.currentSrc.slice(0, 24), natural: [img?.naturalWidth, img?.naturalHeight], box: [img?.clientWidth, img?.clientHeight] },
    placeholderLeft: /__HERO_B64__/.test(document.documentElement.outerHTML),
    badText: ['undefined', 'NaN', '[object'].filter((s) => text.includes(s)),
    chars: text.replace(/\s+/g, '').length,
    scroll: [document.documentElement.scrollWidth, document.documentElement.scrollHeight],
  };
});

await page.screenshot({ path: OUT });

// 目视复核用的 2x 局部放大（只写 /tmp，不落仓库）
const ZOOMS = [
  ['zoom-left', { x: 84, y: 120, width: 520, height: 740 }],
  ['zoom-hero-corner', { x: 760, y: 140, width: 620, height: 300 }],
  ['zoom-palette', { x: 84, y: 880, width: 1750, height: 180 }],
];
const zoomPage = await (await browser.newContext({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 2 })).newPage();
await zoomPage.goto(PAGE_URL, { waitUntil: 'load' });
await zoomPage.evaluate(() => document.fonts.ready);
await zoomPage.waitForTimeout(400);
for (const [name, clip] of ZOOMS) await zoomPage.screenshot({ path: path.join(ZOOM_DIR, `${name}.png`), clip });

await browser.close();

const rel = (p) => path.relative(process.cwd(), p);
console.log(`画板      ${audit.board.w}×${audit.board.h}  滚动区 ${audit.scroll.join('×')}  文字 ${audit.chars} 字`);
console.log(`hero      natural ${audit.hero.natural.join('×')} → 版面 ${audit.hero.box.join('×')}  ${audit.hero.src}…`);
console.log(`字体生效  sans=${audit.fontApplied.sans} serif=${audit.fontApplied.serif} mono=${audit.fontApplied.mono}  loaded=[${audit.fontsLoaded.join(', ')}]`);
console.log(`豆腐块    ${audit.tofu.length ? '✗ ' + JSON.stringify(audit.tofu.slice(0, 12)) : '✓ 0'}`);
console.log(`占位残留  ${audit.placeholderLeft ? '✗ 仍有 __HERO_B64__' : '✓ 无'}    文本异常  ${audit.badText.length ? '✗ ' + audit.badText.join(',') : '✓ 无'}`);
console.log(`错误      pageerror+console+requestfailed = ${errors.length}`);
errors.slice(0, 10).forEach((e) => console.log('  ' + e));
console.log(`产出      ${rel(OUT)}   复核图 ${ZOOM_DIR}/zoom-*.png`);

if (errors.length || audit.tofu.length || audit.badText.length || audit.placeholderLeft || !Object.values(audit.fontApplied).every(Boolean)) {
  console.error('\n✗ 校验未通过');
  process.exit(1);
}
console.log('\n✓ 校验通过（0 错误 / 0 豆腐块 / 0 占位）');
