#!/usr/bin/env node
/**
 * WuZhuFolio 宣传动画 · 构建器
 * 把 tools/promo.template.html 的 __IMG_*__ token 换成 base64 data URI，
 * 产出单文件自包含动画 docs/release/promo/wuzhufolio-promo.html
 *
 * 用法：
 *   node docs/release/promo/tools/build-promo.mjs
 *
 * 素材真源 = docs/release/promo/assets/ui/*.png（真实应用截图 2480×1640 @2x，
 * 由 tools/capture-ui.mjs 从 P1 唯一原型真源采集）。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PROMO = path.resolve(HERE, '..');
const UI = path.join(PROMO, 'assets', 'ui');
const TPL = path.join(HERE, 'promo.template.html');
const OUT = path.join(PROMO, 'wuzhufolio-promo.html');

const MAP = {
  __IMG_DASHBOARD__: 'dashboard-light.png',
  __IMG_PORTFOLIO__: 'portfolio-light.png',
  __IMG_TRANSACTIONS__: 'transactions-light.png',
  __IMG_QUOTES__: 'quotes-light.png',
};

let html = fs.readFileSync(TPL, 'utf8');
for (const [token, file] of Object.entries(MAP)) {
  const p = path.join(UI, file);
  const b64 = fs.readFileSync(p).toString('base64');
  const uri = 'data:image/png;base64,' + b64;
  const before = html.length;
  html = html.split(token).join(uri);
  console.log(`  ${token.padEnd(22)} ← ${file.padEnd(24)} ${(b64.length / 1024).toFixed(0)} KB base64 · ×${(before === html.length ? 0 : 1)}`);
}
if (/__IMG_[A-Z]+__/.test(html)) {
  console.error('✗ 仍有未替换的图片 token：', html.match(/__IMG_[A-Z]+__/g));
  process.exit(1);
}
fs.writeFileSync(OUT, html);
console.log(`✓ ${path.relative(process.cwd(), OUT)}  ${(fs.statSync(OUT).size / 1024 / 1024).toFixed(2)} MB`);
