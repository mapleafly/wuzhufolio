#!/usr/bin/env node
/**
 * WuZhuFolio 应用/打包图标资产生成（P7 携带项 ⑤）
 *
 * 单一真源 = 托盘图标同一枚标记（`app/src/main/kotlin/com/wuzhufolio/app/tray/TrayIcon.kt`：
 * accent 圆角方 #1F5A48 + 纸色 #F6F4EF「W」折线；色值取自 design-tokens §2.1 浅色主题 accent/accent-ink）。
 * 本脚本把该几何生成三平台打包所需的二进制资产，保证**应用图标与托盘图标是同一枚标记**。
 *
 * 用法：node scripts/generate-icons.mjs
 * 产出：app/icons/wuzhufolio.png · wuzhufolio.ico · wuzhufolio.icns
 *       docs/release/promo/assets/icon/wuzhufolio-icon-1024.png（评审预览）
 *
 * 说明：SVG 由 sharp 内置 librsvg 光栅化；ICO/ICNS 均为「PNG 载荷容器」，本脚本直接按规范写容器头
 * （Windows Vista+ 与 macOS 10.7+ 均支持 PNG 载荷），不引入额外依赖。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..');
const SKILL = path.join(ROOT, '.agents/skills/huashu-design');
const sharp = createRequire(path.join(SKILL, 'package.json'))('sharp');

const ACCENT = '#1F5A48'; // design-tokens §2.1 浅色主题 --accent
const PAPER = '#F6F4EF';  // design-tokens §2.1 浅色主题 --accent-ink

const OUT_APP = path.join(ROOT, 'app/icons');
const OUT_PREVIEW = path.join(ROOT, 'docs/release/promo/assets/icon');

/**
 * 生成标记 SVG。
 * @param size 画布边长（px）
 * @param pad  四周留白比例（0 = 满幅；macOS 取 Apple 图标网格的 ~10%）
 */
function markSvg(size, pad = 0) {
  const inset = size * pad;
  const side = size - inset * 2;
  // 与 TrayIcon.kt 同源的几何：圆角 = 边长 22%，W 折线 4 段 5 点，线宽 8.5%，圆头圆角
  const r = side * 0.22;
  const p = (x, y) => `${(inset + side * x).toFixed(3)} ${(inset + side * y).toFixed(3)}`;
  const w = [
    p(0.26, 0.31), p(0.375, 0.70), p(0.50, 0.50), p(0.625, 0.70), p(0.74, 0.31),
  ].join(' L ');
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
  <rect x="${inset.toFixed(3)}" y="${inset.toFixed(3)}" width="${side.toFixed(3)}" height="${side.toFixed(3)}"
        rx="${r.toFixed(3)}" ry="${r.toFixed(3)}" fill="${ACCENT}"/>
  <path d="M ${w}" fill="none" stroke="${PAPER}" stroke-width="${(side * 0.085).toFixed(3)}"
        stroke-linecap="round" stroke-linejoin="round"/>
</svg>`;
}

const png = (size, pad) => sharp(Buffer.from(markSvg(size, pad))).png({ compressionLevel: 9 }).toBuffer();

/** ICO：ICONDIR + 每张图一个 16 字节目录项 + PNG 载荷（宽/高 = 0 表示 256）。 */
function buildIco(entries) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(entries.length, 4);
  const dir = Buffer.alloc(16 * entries.length);
  let offset = 6 + dir.length;
  entries.forEach((e, i) => {
    const o = i * 16;
    dir.writeUInt8(e.size >= 256 ? 0 : e.size, o);
    dir.writeUInt8(e.size >= 256 ? 0 : e.size, o + 1);
    dir.writeUInt8(0, o + 2);
    dir.writeUInt8(0, o + 3);
    dir.writeUInt16LE(1, o + 4);
    dir.writeUInt16LE(32, o + 6);
    dir.writeUInt32LE(e.data.length, o + 8);
    dir.writeUInt32LE(offset, o + 12);
    offset += e.data.length;
  });
  return Buffer.concat([header, dir, ...entries.map((e) => e.data)]);
}

/** ICNS：'icns' + 总长 + [类型(4) + 长度(4, 含头) + PNG 载荷]… */
const ICNS_TYPES = [
  ['icp4', 16], ['icp5', 32], ['icp6', 64],
  ['ic07', 128], ['ic08', 256], ['ic09', 512], ['ic10', 1024],
  ['ic11', 32], ['ic12', 64], ['ic13', 256], ['ic14', 512],
];

function buildIcns(chunks) {
  const parts = chunks.map(({ type, data }) => {
    const head = Buffer.alloc(8);
    head.write(type, 0, 4, 'ascii');
    head.writeUInt32BE(data.length + 8, 4);
    return Buffer.concat([head, data]);
  });
  const body = Buffer.concat(parts);
  const head = Buffer.alloc(8);
  head.write('icns', 0, 4, 'ascii');
  head.writeUInt32BE(body.length + 8, 4);
  return Buffer.concat([head, body]);
}

fs.mkdirSync(OUT_APP, { recursive: true });
fs.mkdirSync(OUT_PREVIEW, { recursive: true });

// Linux / AppImage：jpackage --icon 用单张 PNG（留 4% 边距，避免圆角贴边）
const linuxPng = await png(512, 0.04);
fs.writeFileSync(path.join(OUT_APP, 'wuzhufolio.png'), linuxPng);

// Windows：多尺寸 ICO（16/24/32/48/64/128/256），Windows 侧同样留 4% 边距
const winSizes = [16, 24, 32, 48, 64, 128, 256];
const icoEntries = [];
for (const size of winSizes) icoEntries.push({ size, data: await png(size, 0.04) });
fs.writeFileSync(path.join(OUT_APP, 'wuzhufolio.ico'), buildIco(icoEntries));

// macOS：Apple 图标网格（~10% 留白 + 22% 圆角），ICNS 覆盖 16–1024
const icnsChunks = [];
for (const [type, size] of ICNS_TYPES) icnsChunks.push({ type, data: await png(size, 0.10) });
fs.writeFileSync(path.join(OUT_APP, 'wuzhufolio.icns'), buildIcns(icnsChunks));

// 评审预览（1024，macOS 网格）
fs.writeFileSync(path.join(OUT_PREVIEW, 'wuzhufolio-icon-1024.png'), await png(1024, 0.10));
// 托盘/小尺寸可读性预览（16 与 32 放大 8 倍，供人工核对缩放后是否立得住）
for (const size of [16, 32]) {
  const small = await png(size, 0.04);
  await sharp(small).resize(size * 8, size * 8, { kernel: 'nearest' })
    .png().toFile(path.join(OUT_PREVIEW, `wuzhufolio-icon-${size}px-zoom8.png`));
}

const sizes = (p) => `${(fs.statSync(p).size / 1024).toFixed(1)} KiB`;
console.log('✓ app/icons/wuzhufolio.png      ', sizes(path.join(OUT_APP, 'wuzhufolio.png')), '(512×512)');
console.log('✓ app/icons/wuzhufolio.ico      ', sizes(path.join(OUT_APP, 'wuzhufolio.ico')), `(${winSizes.join('/')})`);
console.log('✓ app/icons/wuzhufolio.icns     ', sizes(path.join(OUT_APP, 'wuzhufolio.icns')), `(${ICNS_TYPES.map(([, s]) => s).join('/')})`);
console.log('✓ docs/release/promo/assets/icon/ 预览 3 张（1024 / 16px×8 / 32px×8）');
