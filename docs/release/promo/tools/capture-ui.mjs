#!/usr/bin/env node
/**
 * P7 产品宣传动画 · UI 素材采集（§1.a 资产协议：用真实 UI，不手画界面）
 *
 * 素材真源 = docs/design/prototype/wuzhufolio-light.html（P1 唯一原型真源，P4/P5 的 UI 视觉基准）。
 * 本机（WSL2 Ubuntu）无 CJK 系统字体（`fc-list :lang=zh` 为空），直接截图会出豆腐块 ——
 * 故注入随包内嵌的 Noto Sans SC / Noto Serif SC / JetBrains Mono（OFL-1.1，ui/src/main/resources/fonts），
 * 与真实应用（WzFonts.kt：正文 Noto Sans SC / Display Noto Serif SC / 数字 JetBrains Mono）一致。
 *
 * 用法：
 *   python3 -m http.server 8791 --bind 127.0.0.1 &          # 仓库根目录起静态服务（字体需经 HTTP 取）
 *   NODE_PATH=.agents/skills/huashu-design/node_modules \
 *     node docs/release/promo/tools/capture-ui.mjs
 *
 * 产出：docs/release/promo/assets/ui/*.png（deviceScaleFactor=2，窗口元素 1240×820 → 2480×1640）
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const HERE = path.dirname(fileURLToPath(import.meta.url));
// playwright 取自项目级挂载的 huashu-design 技能依赖（ESM 不认 NODE_PATH，故按路径解析）
const SKILL_ROOT = path.resolve(HERE, '../../../../.agents/skills/huashu-design');
const { chromium } = createRequire(path.join(SKILL_ROOT, 'package.json'))('playwright');
const OUT = path.resolve(HERE, '../assets/ui');
const BASE = process.env.WZ_BASE || 'http://127.0.0.1:8791';
const PAGE_URL = `${BASE}/docs/design/prototype/wuzhufolio-light.html`;

const FONT_CSS = `
@font-face{font-family:'Noto Sans SC';src:url('${BASE}/ui/src/main/resources/fonts/NotoSansSC.ttf') format('truetype');font-weight:100 900;font-display:block}
@font-face{font-family:'Noto Serif SC';src:url('${BASE}/ui/src/main/resources/fonts/NotoSerifSC.ttf') format('truetype');font-weight:100 900;font-display:block}
@font-face{font-family:'JetBrains Mono';src:url('${BASE}/ui/src/main/resources/fonts/JetBrainsMono.ttf') format('truetype');font-weight:100 800;font-display:block}
/* 本机无 -apple-system/PingFang/雅黑/system-ui 的中文字形 → 全量改绑内嵌 Noto（与桌面端 WzFonts 一致） */
*:not(.k):not(.num):not(.kbd):not(.totalline .v):not(.brand .logo):not(.card .big){font-family:'Noto Sans SC',sans-serif !important}
.brand .logo,.card .big,.gbrand .glogo{font-family:'Noto Serif SC',serif !important}
.nav-item .k,.num,.kbd,.totalline .v{font-family:'JetBrains Mono','Noto Sans SC',monospace !important}
`;

/** 采集清单：name → 页面状态。 */
const SHOTS = [
  { name: 'login-light', gate: 'login', theme: 'light' },
  { name: 'create-light', gate: 'create', theme: 'light' },
  { name: 'dashboard-light', page: 'dashboard', theme: 'light' },
  { name: 'dashboard-dark', page: 'dashboard', theme: 'dark' },
  { name: 'portfolio-light', page: 'portfolio', theme: 'light' },
  { name: 'transactions-light', page: 'transactions', theme: 'light' },
  { name: 'transactions-dark', page: 'transactions', theme: 'dark' },
  { name: 'funds-light', page: 'funds', theme: 'light' },
  { name: 'quotes-light', page: 'quotes', theme: 'light' },
  { name: 'settings-light', page: 'settings', theme: 'light' },
  { name: 'settings-dark', page: 'settings', theme: 'dark' },
  { name: 'coin-btc-light', page: 'portfolio', theme: 'light', after: "openCoin('BTC')" },
];

// 本机 Playwright 包版本（1.59.1）与已缓存的浏览器构建号不一致（缓存为 1228/1234），
// 故显式解析缓存里最新的 Chromium 可执行文件，避免为一次截图下载 150MB 浏览器。
function findChromium() {
  const cache = path.join(process.env.HOME ?? '', '.cache/ms-playwright');
  if (!fs.existsSync(cache)) return undefined;
  const cands = [];
  for (const dir of fs.readdirSync(cache)) {
    for (const rel of [
      'chrome-linux64/chrome',
      'chrome-linux/chrome',
      'chrome-headless-shell-linux64/chrome-headless-shell',
    ]) {
      const p = path.join(cache, dir, rel);
      if (fs.existsSync(p)) cands.push(p);
    }
  }
  // 构建号大者优先；同时优先完整 chromium（chrome-linux*）而非 headless shell
  return cands.sort((a, b) => {
    const score = (s) => (s.includes('headless-shell') ? 0 : 1) * 100000 + (Number(s.match(/(\d{4})/)?.[1]) || 0);
    return score(b) - score(a);
  })[0];
}

fs.mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({
  executablePath: findChromium(),
  args: ['--font-render-hinting=none', '--force-color-profile=srgb'],
});
const ctx = await browser.newContext({
  viewport: { width: 1400, height: 940 },
  deviceScaleFactor: 2,
  reducedMotion: 'reduce',
});
const page = await ctx.newPage();

const errors = [];
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
page.on('console', (m) => {
  if (m.type() === 'error') errors.push(`console: ${m.text()}`);
});

await page.goto(PAGE_URL, { waitUntil: 'load' });
await page.addStyleTag({ content: FONT_CSS });
// 主题按钮的 ☾/☀ 字形不在内嵌 Noto SC 覆盖范围内（本机无系统符号字体 → 渲染为豆腐块）。
// 真实桌面端用的是矢量图标，故此处同义替换为内联 SVG，避免素材出现缺字。
await page.evaluate(() => {
  const paint = (dark) => {
    const btn = document.getElementById('themeBtn');
    if (!btn) return;
    btn.innerHTML = dark
      ? '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><circle cx="12" cy="12" r="4.2"/><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.2 5.2l1.4 1.4M17.4 17.4l1.4 1.4M18.8 5.2l-1.4 1.4M6.6 17.4l-1.4 1.4"/></svg>'
      : '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"><path d="M20.5 14.2A8.6 8.6 0 1 1 9.8 3.5a6.7 6.7 0 0 0 10.7 10.7z"/></svg>';
  };
  paint(document.documentElement.getAttribute('data-theme') === 'dark');
  new MutationObserver(() => paint(document.documentElement.getAttribute('data-theme') === 'dark'))
    .observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
});
await page.evaluate(() => document.fonts.ready);
await page.waitForTimeout(600);

for (const shot of SHOTS) {
  await page.evaluate(([s]) => {
    window.applyTheme(s.theme);
    if (s.gate) {
      window.showGate(s.gate);
    } else {
      window.enterShell(s.page);
      if (s.after) {
        // eslint-disable-next-line no-new-func
        new Function(s.after)();
      }
    }
  }, [shot]);
  await page.waitForTimeout(shot.after ? 700 : 450);
  const win = await page.$('#win');
  const file = path.join(OUT, `${shot.name}.png`);
  await win.screenshot({ path: file });
  const { width, height } = await win.boundingBox();
  console.log(`✓ ${shot.name.padEnd(20)} ${width}×${height} @2x → ${path.relative(process.cwd(), file)}`);
  // 弹层复位，避免影响下一张
  await page.evaluate(() => window.closeModal && window.closeModal());
}

await browser.close();

if (errors.length) {
  console.error(`\n✗ 页面错误 ${errors.length} 条：`);
  errors.slice(0, 10).forEach((e) => console.error('  ' + e));
  process.exit(1);
}
console.log(`\n✓ 全部完成（0 页面错误）→ ${path.relative(process.cwd(), OUT)}`);
