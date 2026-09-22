#!/usr/bin/env node
/**
 * WuZhuFolio 宣传动画 · 逐帧 seek 渲染器
 *
 * 与技能自带 scripts/render-video-seek.js 同构（Chromium 逐帧 __seek(t) → 截图 → ffmpeg H.264），
 * 但有两点本机必要的差异：
 *   1. 页面经 http://127.0.0.1:8791 打开，而不是 file:// —— 本机无 CJK 系统字体，
 *      字体必须由静态服务供给；file:// 页面跨源取 http 字体在 Chromium 下会被拦。
 *   2. playwright 与缓存浏览器构建号不一致，显式解析 ~/.cache/ms-playwright 里的可执行文件。
 *
 * 用法：
 *   python3 -m http.server 8791 --bind 127.0.0.1 &          # 仓库根目录
 *   LD_LIBRARY_PATH=$HOME/.local/plibs/root/usr/lib/x86_64-linux-gnu \
 *   NODE_PATH=.agents/skills/huashu-design/node_modules \
 *   node docs/release/promo/tools/render-promo-seek.mjs \
 *     docs/release/promo/wuzhufolio-promo.html --duration=30 --fps=60 --width=1920 --height=1080
 *
 * 选项：--fps --duration --width --height --concurrency --settle --check --out
 *   --check  只做页面自检（pageerror / console error / 字体 / 豆腐块 / NaN），不渲染
 */
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, '../../../..');
const SKILL = path.join(REPO, '.agents/skills/huashu-design');
const { chromium } = createRequire(path.join(SKILL, 'package.json'))('playwright');

function arg(name, def) {
  const p = process.argv.find(a => a.startsWith('--' + name + '='));
  return p ? p.slice(name.length + 3) : def;
}
const hasFlag = n => process.argv.includes('--' + n);

const HTML = process.argv[2];
if (!HTML || HTML.startsWith('--')) { console.error('usage: render-promo-seek.mjs <html> [--fps=60 ...]'); process.exit(1); }
const DURATION = parseFloat(arg('duration', '30'));
const FPS = parseFloat(arg('fps', '60'));
const WIDTH = parseInt(arg('width', '1920'));
const HEIGHT = parseInt(arg('height', '1080'));
const CONC = Math.max(1, parseInt(arg('concurrency', '4')));
const SETTLE = Math.max(1, parseInt(arg('settle', '2')));
const CHECK_ONLY = hasFlag('check');
const BASE = process.env.WZ_BASE || 'http://127.0.0.1:8791';
const PORT = parseInt(process.env.WZ_PORT || '8791');

const TOTAL = Math.round(FPS * DURATION);
const REL = path.relative(REPO, path.resolve(HTML));
const PAGE_URL = `${BASE}/${REL}`;
const OUT = arg('out', path.resolve(HTML).replace(/\.html$/, '.mp4'));
const TMP = path.join(path.dirname(path.resolve(HTML)), '.seek-tmp-' + Date.now() + '-' + process.pid);

function findChromium() {
  const cache = path.join(process.env.HOME ?? '', '.cache/ms-playwright');
  if (!fs.existsSync(cache)) return undefined;
  const c = [];
  for (const d of fs.readdirSync(cache))
    for (const r of ['chrome-linux64/chrome', 'chrome-linux/chrome'])
      if (fs.existsSync(path.join(cache, d, r))) c.push(path.join(cache, d, r));
  return c.sort((a, b) => (Number(b.match(/(\d{4})/)?.[1]) || 0) - (Number(a.match(/(\d{4})/)?.[1]) || 0))[0];
}

const raf = (page, n) => page.evaluate(c => new Promise(res => {
  let i = 0; const s = () => { i++; i >= c ? res() : requestAnimationFrame(s); }; requestAnimationFrame(s);
}), n);

const browser = await chromium.launch({ executablePath: findChromium() });
const ctx = await browser.newContext({ viewport: { width: WIDTH, height: HEIGHT }, deviceScaleFactor: 1 });
await ctx.addInitScript(() => { window.__recording = true; window.__seekRender = true; });

/* ── 自检页：收集 pageerror / console error / 字体 / 豆腐块 ── */
const diag = { pageerror: [], consoleError: [], bad: [] };
async function openPage() {
  const page = await ctx.newPage();
  page.on('pageerror', e => diag.pageerror.push(String(e && e.message || e)));
  page.on('console', m => {
    if (m.type() !== 'error') return;
    const t = m.text();
    if (/favicon/i.test(t)) return;                       // favicon 404 可忽略
    diag.consoleError.push(t);
  });
  await page.goto(PAGE_URL, { waitUntil: 'load', timeout: 90000 });
  await page.waitForFunction(() => window.__ready === true && typeof window.__seek === 'function', { timeout: 120000 });
  return page;
}

console.log(`▸ ${PAGE_URL}`);
console.log(`  ${WIDTH}×${HEIGHT} · ${FPS}fps · ${DURATION}s · ${TOTAL} frames · workers ${CONC}`);

const page0 = await openPage();
/* 字体是否真的加载（否则满屏豆腐块）*/
const fontInfo = await page0.evaluate(() => {
  const loaded = [];
  document.fonts.forEach(f => loaded.push(f.family + '/' + f.status));
  const probe = (fam, txt) => {
    const c = document.createElement('canvas').getContext('2d');
    c.font = '64px "' + fam + '"';
    const w1 = c.measureText(txt).width;
    c.font = '64px "NoSuchFont-xyz"';
    const w2 = c.measureText(txt).width;
    return { fam, width: +w1.toFixed(1), differsFromFallback: Math.abs(w1 - w2) > 0.5 };
  };
  return {
    faces: loaded,
    probes: [probe('Noto Serif SC', '账簿'), probe('Noto Sans SC', '本地加密'), probe('JetBrains Mono', 'WuZhuFolio')],
  };
});
console.log('  fonts:', JSON.stringify(fontInfo.faces));
fontInfo.probes.forEach(p => console.log(`  probe ${p.fam.padEnd(16)} width=${p.width} 非fallback=${p.differsFromFallback}`));

/* 豆腐块探测：把关键帧渲染出来，统计「同一行内重复出现同一字形宽度」的异常 —— 改为直接读 canvas 占比更稳，
   这里用 DOM 层的兜底：检查关键元素文本是否为空 / 含 NaN / undefined */
async function scanFrames(times) {
  const bad = [];
  for (const t of times) {
    const r = await page0.evaluate((tt) => {
      window.__seek(tt);
      const txt = document.body.innerText || '';
      const out = { t: tt, nan: /NaN|undefined|nullpx/.test(document.getElementById('world').getAttribute('style') || '') };
      out.txtBad = /NaN|undefined/.test(txt);
      const wm = document.getElementById('wmZoom').getBoundingClientRect();
      out.wmBox = [Math.round(wm.width), Math.round(wm.height)];
      return out;
    }, t);
    if (r.nan || r.txtBad || r.wmBox[0] < 10) bad.push(r);
  }
  return bad;
}
const baddies = await scanFrames([0, 3, 5, 9, 11.3, 13, 15, 17, 19, 21, 23, 24.5, 26, 28, 29.9]);
console.log('  scan bad frames:', baddies.length ? JSON.stringify(baddies) : 'none');

if (CHECK_ONLY) {
  console.log('  pageerror  :', diag.pageerror.length, diag.pageerror.slice(0, 3));
  console.log('  consoleErr :', diag.consoleError.length, diag.consoleError.slice(0, 3));
  /* 抽 6 帧存图供人工目视 */
  const dir = path.join(path.dirname(path.resolve(HTML)), 'tools', '_check');
  fs.mkdirSync(dir, { recursive: true });
  for (const t of [0, 2.67, 5.0, 9.33, 12.0, 16.67, 20.67, 24.17, 29.0]) {
    await page0.evaluate(tt => window.__seek(tt), t);
    await raf(page0, SETTLE);
    await page0.screenshot({ path: path.join(dir, 'f' + String(Math.round(t * FPS)).padStart(4, '0') + '.png'), clip: { x: 0, y: 0, width: WIDTH, height: HEIGHT } });
  }
  console.log('  ✓ check frames →', path.relative(process.cwd(), dir));
  await browser.close();
  process.exit(0);
}

/* ── 渲染 ── */
fs.mkdirSync(TMP, { recursive: true });
const buckets = Array.from({ length: CONC }, () => []);
for (let f = 0; f < TOTAL; f++) buckets[f % CONC].push(f);
await page0.close();

const t0 = Date.now();
await Promise.all(buckets.map(async (frames, wi) => {
  const page = await openPage();
  for (const f of frames) {
    await page.evaluate(t => window.__seek(t), f / FPS);
    await raf(page, SETTLE);
    await page.screenshot({ path: path.join(TMP, 'frame-' + String(f).padStart(6, '0') + '.png'), clip: { x: 0, y: 0, width: WIDTH, height: HEIGHT } });
  }
  await page.close();
  process.stdout.write(`\r  worker ${wi + 1}/${CONC} done   `);
}));
const pngCount = fs.readdirSync(TMP).filter(f => f.endsWith('.png')).length;
console.log(`\n▸ captured ${pngCount}/${TOTAL} frames in ${((Date.now() - t0) / 1000).toFixed(0)}s · encoding H.264…`);
await browser.close();
if (pngCount < TOTAL) { console.error(`✗ 缺帧 ${TOTAL - pngCount}`); process.exit(1); }

const ff = spawnSync('ffmpeg', [
  '-y', '-framerate', String(FPS), '-i', path.join(TMP, 'frame-%06d.png'),
  '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '18', '-preset', 'medium',
  '-r', String(FPS), '-movflags', '+faststart', '-profile:v', 'high', '-level', '4.0', OUT,
], { stdio: ['ignore', 'ignore', 'pipe'] });
if (ff.status !== 0) { console.error('✗ ffmpeg:\n' + ff.stderr.toString().slice(-1500)); process.exit(1); }
fs.rmSync(TMP, { recursive: true, force: true });

console.log(`✓ silent MP4 → ${path.relative(process.cwd(), OUT)} (${(fs.statSync(OUT).size / 1048576).toFixed(1)} MB)`);
console.log(`  pageerror=${diag.pageerror.length} consoleError=${diag.consoleError.length}`);
if (diag.pageerror.length) console.log('   ', diag.pageerror.slice(0, 5));
