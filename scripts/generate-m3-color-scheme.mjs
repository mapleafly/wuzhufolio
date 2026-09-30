#!/usr/bin/env node
/**
 * 由**品牌种子色**生成 Material 3 配色方案 + 语义扩展色（ADR-007 §2.2 / design-tokens §2）。
 *
 * 为什么用脚本生成而不是手写 hex：M3 的配色规范是**算法**（HCT 色空间 → 色调板 tonal palette → 语义 role），
 * 不是一张手工色表。手写"模仿"M3 只能得到"像 M3 但不是 M3"的结果。
 *
 * 产物：`ui/src/main/kotlin/com/wuzhufolio/ui/theme/M3ColorRoles.kt`（**生成物，勿手改**）。
 * 依赖：`@material/material-color-utilities`（Google 官方实现，Apache-2.0）——**仅生成期使用，不进产物**。
 *
 * 用法（ESM 按脚本位置解析 node_modules，故需在装了依赖的目录里跑）：
 *   mkdir -p /tmp/mcu && cd /tmp/mcu && npm i @material/material-color-utilities && npm i -D esbuild
 *   cp <repo>/scripts/generate-m3-color-scheme.mjs .
 *   npx esbuild generate-m3-color-scheme.mjs --bundle --platform=node --format=cjs --outfile=gen.cjs
 *   node gen.cjs > <repo>/ui/src/main/kotlin/com/wuzhufolio/ui/theme/M3ColorRoles.kt
 *
 * 换品牌色：改 SEED（浅色与暗色**共用同一个种子**——这是 M3 规范的口径）。
 */

import {
  Hct,
  SchemeTonalSpot,
  TonalPalette,
  argbFromHex,
  hexFromArgb,
} from '@material/material-color-utilities';

/** 品牌种子色：墨绿（design-tokens §1「私人账本 / 安全控制台」气质）。 */
const SEED = '#1F5A48';
/** 语义扩展色的种子（M3 无 gain/loss/warn role，按同一套色调板算法派生，保证与主色同族质感）。 */
const SEED_GAIN = '#1E6B45';   // 涨/盈利（绿）
const SEED_LOSS = '#A93A37';   // 跌/亏损（红）
const SEED_WARN = '#8A6416';   // 提示/待定价（赭黄）

const ROLES = [
  'primary', 'onPrimary', 'primaryContainer', 'onPrimaryContainer', 'inversePrimary',
  'secondary', 'onSecondary', 'secondaryContainer', 'onSecondaryContainer',
  'tertiary', 'onTertiary', 'tertiaryContainer', 'onTertiaryContainer',
  'error', 'onError', 'errorContainer', 'onErrorContainer',
  'background', 'onBackground',
  'surface', 'onSurface', 'surfaceVariant', 'onSurfaceVariant',
  'surfaceContainerLowest', 'surfaceContainerLow', 'surfaceContainer', 'surfaceContainerHigh',
  'surfaceContainerHighest', 'surfaceDim', 'surfaceBright',
  'outline', 'outlineVariant', 'inverseSurface', 'inverseOnSurface', 'scrim',
];

const seedHct = Hct.fromInt(argbFromHex(SEED));
const lightScheme = new SchemeTonalSpot(seedHct, false, 0.0);
const darkScheme = new SchemeTonalSpot(seedHct, true, 0.0);

// 语义扩展色：同一套 tonal palette 算法（浅色取 tone 40、暗色取 tone 80 —— M3 对"强调色"的通行档位）
const gainLight = TonalPalette.fromInt(argbFromHex(SEED_GAIN)).tone(40);
const gainDark = TonalPalette.fromInt(argbFromHex(SEED_GAIN)).tone(80);
const lossLight = TonalPalette.fromInt(argbFromHex(SEED_LOSS)).tone(40);
const lossDark = TonalPalette.fromInt(argbFromHex(SEED_LOSS)).tone(80);
const warnLight = TonalPalette.fromInt(argbFromHex(SEED_WARN)).tone(40);
const warnDark = TonalPalette.fromInt(argbFromHex(SEED_WARN)).tone(80);

const hex = (argb) => '0xFF' + hexFromArgb(argb).replace('#', '').toUpperCase();

/** WCAG 相对亮度与对比度（生成期自检，保证语义色在两种主题下都 ≥ 4.5:1）。 */
const srgb = (c) => { const s = c / 255; return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4); };
const lum = (argb) => 0.2126 * srgb((argb >> 16) & 255) + 0.7152 * srgb((argb >> 8) & 255) + 0.0722 * srgb(argb & 255);
const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };

const obj = (name, entries) => `/** ${name}。 */
internal object ${name} {
${entries.map(([k, v]) => `    val ${k}: Color = Color(${hex(v)})`).join('\n')}
}`;

// 三级文字（表头/时间戳/图例）：M3 无对应 role，取中性色调板中**仍满足 AA 4.5:1 的最浅一档**。
// 浅色 = 中性 tone 45（tone 50 恰好压线 4.50，不用）；暗色 = M3 `outline`（5.44:1）。
const ink3Light = TonalPalette.fromInt(lightScheme.onSurfaceVariant).tone(45);
const ink3Dark = darkScheme.outline;

const lightEntries = ROLES.map((r) => [r, lightScheme[r]]).concat([
  ['gain', gainLight], ['loss', lossLight], ['warn', warnLight], ['ink3', ink3Light],
]);
const darkEntries = ROLES.map((r) => [r, darkScheme[r]]).concat([
  ['gain', gainDark], ['loss', lossDark], ['warn', warnDark], ['ink3', ink3Dark],
]);

// ── 生成期自检：正文/语义色对底色的对比度（不达标直接失败，避免"生成的配色读不清"）──
const checks = [
  ['light onSurface/surface', lightScheme.onSurface, lightScheme.surface, 4.5],
  ['light onSurfaceVariant/surface', lightScheme.onSurfaceVariant, lightScheme.surface, 4.5],
  ['light gain/surface', gainLight, lightScheme.surface, 4.5],
  ['light loss/surface', lossLight, lightScheme.surface, 4.5],
  ['light warn/surface', warnLight, lightScheme.surface, 4.5],
  ['light onPrimary/primary', lightScheme.onPrimary, lightScheme.primary, 4.5],
  ['dark onSurface/surface', darkScheme.onSurface, darkScheme.surface, 4.5],
  ['dark onSurfaceVariant/surface', darkScheme.onSurfaceVariant, darkScheme.surface, 4.5],
  ['dark gain/surface', gainDark, darkScheme.surface, 4.5],
  ['dark loss/surface', lossDark, darkScheme.surface, 4.5],
  ['dark warn/surface', warnDark, darkScheme.surface, 4.5],
  ['dark onPrimary/primary', darkScheme.onPrimary, darkScheme.primary, 4.5],
  // 三级文字必须同时满足「对卡片底」与「对应用底」两个背景
  ['light ink3/surface', ink3Light, lightScheme.surfaceContainerLowest, 4.5],
  ['light ink3/surface', ink3Light, lightScheme.surface, 4.5],
  ['dark ink3/surface', ink3Dark, darkScheme.surfaceContainerLow, 4.5],
  ['dark ink3/surface', ink3Dark, darkScheme.surface, 4.5],
  // 语义扩展色在**卡片底**上同样要达标（表格/卡片里大量使用）
  ['light gain/card', gainLight, lightScheme.surfaceContainerLowest, 4.5],
  ['light loss/card', lossLight, lightScheme.surfaceContainerLowest, 4.5],
  ['light warn/card', warnLight, lightScheme.surfaceContainerLowest, 4.5],
  ['dark gain/card', gainDark, darkScheme.surfaceContainerLow, 4.5],
  ['dark loss/card', lossDark, darkScheme.surfaceContainerLow, 4.5],
  ['dark warn/card', warnDark, darkScheme.surfaceContainerLow, 4.5],
];
let failed = false;
const report = checks.map(([name, fg, bg, min]) => {
  const r = ratio(fg, bg);
  if (r < min) failed = true;
  return `//   ${r >= min ? '✅' : '❌'} ${name}: ${r.toFixed(2)}:1`;
});

if (failed) {
  console.error('生成期自检失败：以下组合低于 WCAG AA 4.5:1\n' + report.join('\n'));
  process.exit(1);
}

console.log(`package com.wuzhufolio.ui.theme

import androidx.compose.ui.graphics.Color

// ⚠️ 本文件由 scripts/generate-m3-color-scheme.mjs 生成（种子色 ${SEED}）——**不要手改**。
// 改色 = 改脚本里的 SEED 并重跑；语义层映射（WzColors 的用途）见 ColorTokens.kt。
// 算法：Google material-color-utilities 0.4.0 · SchemeTonalSpot（M3 基线方案）+ TonalPalette（语义扩展色）。
//
// 生成期对比度自检（WCAG 2.1，AA 正文 ≥ 4.5:1）：
${report.join('\n')}

${obj('M3LightRoles', lightEntries)}

${obj('M3DarkRoles', darkEntries)}
`);
