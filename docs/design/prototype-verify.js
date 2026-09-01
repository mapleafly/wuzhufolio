/**
 * WuZhuFolio P1 原型验证脚本（prototype-verify.js）
 * 验证对象：docs/design/prototype/wuzhufolio-light.html（单一真源 · 内置明暗双主题）
 * 覆盖：登录链路 4 页（F1）、代理指示（F2）、日志与诊断（F3）、对比度两主题（F4）、
 *       a11y 基线（F5）、原型缺口（F9）、重放引擎数值自洽（F11）、环形图主题重渲染（N5）、
 *       全量回归（页面/Modal/表单计算/环形图占比/NaN 扫描/双视口溢出/零外部依赖）。
 * 运行（本机示例，Playwright 1.62.1 位于 npx 缓存）：
 *   NODE_PATH=$(find ~/.npm/_npx -maxdepth 3 -name playwright -type d | head -1)/.. \
 *   LD_LIBRARY_PATH=$HOME/.local/plibs/root/usr/lib/x86_64-linux-gnu \
 *   node docs/design/prototype-verify.js
 * 输出：JSON 结果（T.* 为各断言取值，errors 应为空数组）
 */
const path = require('path');
const { chromium } = require('playwright');
const PROTO = 'file://' + path.resolve(__dirname, 'prototype', 'wuzhufolio-light.html');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 960 } });
  const errors = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
  await page.goto(PROTO);
  await page.waitForTimeout(400);

  const T = {};
  // ===== F1 登录链路 =====
  T.gateLogin = await page.locator('#gate .gtitle').textContent();                     // 「登录」
  T.gateUserSelect = await page.locator('#lgUser option').count();                     // 用户名枚举（2 账户）
  T.rememberChecked = await page.locator('#lgRemember').isChecked();                   // 记住我默认勾选
  await page.locator('#lgBtn').click();
  T.emptyPwErrShown = await page.locator('#lgErr').evaluate(el => getComputedStyle(el).display !== 'none'); // V 类校验
  await page.locator('.glinks .glink').first().click();                                // 忘记密码
  await page.waitForTimeout(100);
  T.forgotText = (await page.locator('#gate .riskbox').textContent()).trim();          // PRD 逐字文案
  await page.locator('#gate .glink').click();                                          // 返回登录
  await page.waitForTimeout(100);
  await page.locator('#lgPw').fill('demo1234');
  await page.locator('#lgBtn').click();                                                // 登录（解密 loading -> 主壳）
  await page.waitForTimeout(900);
  T.gatedAfterLogin = await page.locator('#win').evaluate(el => el.classList.contains('gated')); // false
  T.crumb = await page.locator('#crumb').textContent();

  // ===== F11 重放引擎推导的仪表盘数值（黄金用例对齐） =====
  T.tv = await page.locator('.card').first().locator('.big').textContent();
  T.pnl24 = await page.locator('.cards .card').nth(1).locator('.big').textContent();
  T.pnl24pct = await page.locator('.cards .card').nth(1).locator('.delta').textContent();
  T.roi = await page.locator('.cards .card').nth(2).locator('.big').textContent();
  T.net = await page.locator('.cards .card').nth(3).locator('.big').textContent();
  T.cash = await page.locator('.cards .card').nth(4).locator('.big').textContent();
  T.totalRet = await page.locator('.cards .card').nth(5).locator('.big').textContent();
  T.realized = await page.locator('.cards .card').nth(6).locator('.big').textContent();
  T.donutPaths = await page.locator('#donut path').count();
  const pcts = await page.locator('#donut .lg .pct').allTextContents();
  T.donutPctSum = pcts.reduce((a, p) => a + parseFloat(p), 0).toFixed(2);

  // ===== F2 状态栏代理指示 =====
  T.statusbar = (await page.locator('#statusbar').textContent()).trim();

  // ===== N5 主题切换即时重渲染环形图 =====
  const fillBefore = await page.locator('#donut path').first().getAttribute('fill');
  await page.locator('#themeBtn').click();
  await page.waitForTimeout(200);
  const fillAfter = await page.locator('#donut path').first().getAttribute('fill');
  T.fillBefore = fillBefore; T.fillAfter = fillAfter;
  T.themeFillChanged = fillBefore !== fillAfter;
  T.darkBodyBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  await page.locator('#themeBtn').click();
  await page.waitForTimeout(200);

  // ===== F9 环形图点击高亮 + 浮窗 =====
  const svgBox = await page.locator('#donut svg').boundingBox();
  await page.mouse.click(svgBox.x + 100, svgBox.y + 14);                               // 点击扇区（环带坐标）
  await page.waitForTimeout(150);
  T.donutPopOpen = await page.locator('#donutPop').evaluate(el => el.classList.contains('open'));
  T.donutPopText = (await page.locator('#donutPop').textContent()).trim().replace(/\s+/g, ' ');
  await page.keyboard.press('Escape');
  T.donutPopClosed = await page.locator('#donutPop').evaluate(el => !el.classList.contains('open'));
  await page.locator('#donut .lg').first().click();                                    // 图例按钮同样可选
  T.legendClickPop = await page.locator('#donutPop').evaluate(el => el.classList.contains('open'));
  await page.locator('#donut .lg').first().click();

  // ===== F9 资产列表表头排序 =====
  await page.locator('.nav-item').nth(1).click();
  await page.waitForTimeout(150);
  T.portFirstRowCoin = (await page.locator('.panel tbody tr').first().locator('td').first().textContent()).trim();
  await page.locator('th[onclick="portSort(\'s\')"]').click();
  T.portSortedByS = (await page.locator('.panel tbody tr').first().locator('td').first().textContent()).trim();
  T.portThAria = await page.locator('th[onclick="portSort(\'s\')"]').getAttribute('aria-sort');
  T.portRowTabindex = await page.locator('.panel tbody tr').first().getAttribute('tabindex');

  // ===== 币种详情（4 汇总卡 + 三重筛选） =====
  await page.locator('.panel tbody tr').first().click();
  await page.waitForTimeout(200);
  T.coinModalTitle = await page.locator('.modal h3').first().textContent();
  T.coinCards = await page.locator('.modal .card').count();
  T.coinTxFilters = await page.locator('#cex, #cty, #cq').count();
  T.coinTxRows = await page.locator('#coinTxBody tr').count();
  await page.locator('#cty').selectOption('卖出');
  T.coinTxFilteredRows = await page.locator('#coinTxBody tr').count();                 // 空态占位行
  await page.keyboard.press('Escape');

  // ===== 交易表单回归（总价/自动手续费/交易对补全） =====
  await page.locator('.nav-item').nth(2).click();
  await page.locator('.toolbar .btn.primary').first().click();
  await page.waitForTimeout(200);
  T.txTotal = await page.locator('#txTotal').textContent();
  await page.locator('.modal .btn.ghost.sm').first().click();
  T.autoFeeValue = await page.locator('#txFee').inputValue();
  await page.locator('#txPair').fill('BTC');
  await page.locator('#txPair').dispatchEvent('input');
  T.pairSugCount = await page.locator('#txPairSug .opt').count();
  await page.keyboard.press('Escape');

  // ===== F9 资金页筛选 =====
  await page.locator('.nav-item').nth(3).click();
  await page.waitForTimeout(150);
  T.fundsRows = await page.locator('.panel tbody tr').count();
  T.fundsFilterControls = await page.locator('.toolbar .select').count();
  await page.locator('.toolbar .select').first().selectOption('增资');
  T.fundsFilteredRows = await page.locator('.panel tbody tr').count();
  await page.locator('.toolbar .select').first().selectOption('全部类型');
  await page.locator('.toolbar .select').nth(1).selectOption('近 30 天');
  T.fundsFilteredByDate = await page.locator('.panel tbody tr').count();
  await page.locator('.toolbar .select').nth(1).selectOption('全部时间');

  // ===== 设置页：F8 主题行 / F2 网络 / F3 日志与诊断 / F9 API 弹窗 =====
  await page.locator('.nav-item').nth(4).click();
  await page.waitForTimeout(150);
  T.settingsGroups = await page.locator('.settings-group h3').allTextContents();
  await page.locator('#themeSeg button').nth(1).click();
  T.themeViaSettings = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  await page.locator('#themeSeg button').nth(0).click();
  T.themeBack = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  await page.locator('.set-row').filter({ hasText: '查看日志' }).locator('button').click();
  T.logsModalRows = await page.locator('.modal tbody tr').count();
  await page.keyboard.press('Escape');
  await page.locator('.set-row').filter({ hasText: 'API 管理' }).locator('button').click();
  await page.locator('.modal .btn.primary').click();                                   // ＋ 添加 API
  T.apiAddTitle = await page.locator('.modal h3').first().textContent();
  T.apiAddFields = await page.locator('.modal .field').count();
  await page.locator('.modal .mfoot .btn').nth(0).click();                             // 测试请求
  T.testApiToast = (await page.locator('#toast').textContent()).trim();
  await page.keyboard.press('Escape');

  // ===== F4 对比度：两主题分别验证（computed token） =====
  T.contrast = await page.evaluate(() => {
    function lum(h){ h=h.replace('#',''); const c=[0,2,4].map(i=>parseInt(h.slice(i,i+2),16)/255).map(v=>v<=0.04045?v/12.92:Math.pow((v+0.055)/1.055,2.4)); return 0.2126*c[0]+0.7152*c[1]+0.0722*c[2]; }
    function cr(f,b){ const l1=lum(f),l2=lum(b); return (Math.max(l1,l2)+0.05)/(Math.min(l1,l2)+0.05); }
    function tok(name){ return getComputedStyle(document.documentElement).getPropertyValue(name).trim(); }
    const out={};
    ['light','dark'].forEach(t=>{
      document.documentElement.setAttribute('data-theme',t);
      document.body.setAttribute('data-theme',t);
      const bg=tok('--bg'), sur=tok('--surface');
      out[t]={};
      ['--ink','--ink2','--ink3','--gain','--loss','--warn','--accent'].forEach(k=>{ out[t][k]={bg:+cr(tok(k),bg).toFixed(2), sur:+cr(tok(k),sur).toFixed(2)}; });
    });
    document.documentElement.setAttribute('data-theme','light');
    document.body.setAttribute('data-theme','light');
    return out;
  });

  // ===== F5 a11y 基线 =====
  T.a11y = await page.evaluate(() => {
    return {
      navBtn: document.querySelectorAll('.nav button.nav-item').length,
      switches: document.querySelectorAll('button.switch[role="switch"]').length,
      ariaLabels: document.querySelectorAll('[aria-label]').length,
      focusRule: !!Array.from(document.styleSheets).some(ss => { try { return Array.from(ss.cssRules).some(r => r.selectorText && r.selectorText.includes(':focus-visible')); } catch(e){ return false; } }),
      divOnclick: document.querySelectorAll('div[onclick]').length,
      toastAria: document.getElementById('toast').getAttribute('role')
    };
  });
  await page.locator('.nav-item').nth(0).click();
  await page.locator('body').focus();
  for (let i=0;i<8;i++){ await page.keyboard.press('Tab'); }
  T.tabReached = await page.evaluate(() => document.activeElement ? document.activeElement.tagName + '.' + (document.activeElement.className||'').toString().slice(0,40) : 'none');

  // ===== 登出 -> 登录页；创建账户 -> 风险确认 -> 初始化向导 -> 四路径 =====
  await page.locator('#acctBtn').click();
  await page.locator('#acctMenu .mi.danger').click();
  await page.waitForTimeout(200);
  T.gatedAfterLogout = await page.locator('#win').evaluate(el => el.classList.contains('gated'));
  await page.locator('.glinks .glink').nth(1).click();
  T.createTitle = await page.locator('#gate .gtitle').textContent();
  await page.locator('#gate .btn.primary').click();
  T.createErrs = await page.locator('#gate .err-msg.show').count();                    // V8 类校验
  await page.locator('#cuUser').fill('Demo');
  await page.locator('#cuPw').fill('Abc12345');
  await page.locator('#cuPw').dispatchEvent('input');
  T.pwMeter = await page.locator('#cuMeter').getAttribute('class');
  await page.locator('#cuPw2').fill('Abc12345');
  await page.locator('#gate .btn.primary').click();
  T.riskText = (await page.locator('.modal .riskbox').textContent()).trim().replace(/\s+/g, ' ');
  T.rcBtnDisabled = await page.locator('#rcBtn').isDisabled();                         // 未勾选禁用
  await page.locator('#rcAgree').check();
  T.rcBtnEnabled = !(await page.locator('#rcBtn').isDisabled());
  await page.locator('#rcBtn').click();
  await page.waitForTimeout(200);
  T.wizardTitle = await page.locator('#gate .gtitle').textContent();
  T.wizardOpts = await page.locator('.wopt').count();
  await page.locator('.wopt').nth(2).click();                                          // API（推荐）路径
  await page.waitForTimeout(300);
  T.afterWizardCrumb = await page.locator('#crumb').textContent();
  T.apiWizardModal = await page.locator('.modal h3').first().textContent();
  await page.locator('.modal .mfoot .btn.primary').click();                            // 保存 -> 立即首次同步（F10）
  T.saveApiToast = (await page.locator('#toast').textContent()).trim();
  await page.keyboard.press('Escape');

  async function createAndWizard(user){
    await page.locator('#acctBtn').click(); await page.waitForTimeout(80);
    await page.locator('#acctMenu .mi.danger').click(); await page.waitForTimeout(200);
    await page.locator('.glinks .glink').nth(1).click(); await page.waitForTimeout(150);
    await page.locator('#cuUser').fill(user); await page.locator('#cuPw').fill('Abc12345');
    await page.locator('#cuPw').dispatchEvent('input');
    await page.locator('#cuPw2').fill('Abc12345');
    await page.locator('#gate .btn.primary').click(); await page.waitForTimeout(200);
    await page.locator('#rcAgree').check(); await page.locator('#rcBtn').click(); await page.waitForTimeout(200);
  }
  await createAndWizard('Demo2');
  await page.locator('.wopt').nth(0).click();                                          // 手动添加路径
  await page.waitForTimeout(300);
  T.manualPathModal = await page.locator('.modal h3').first().textContent();
  await page.keyboard.press('Escape');
  await createAndWizard('Demo3');
  await page.locator('.wopt').nth(1).click();                                          // CSV 导入路径
  await page.waitForTimeout(300);
  T.csvPathModal = await page.locator('.modal h3').first().textContent();
  T.csvTemplateBtn = await page.locator('.modal .field .btn.ghost.sm').count();        // F10 模板下载
  await page.keyboard.press('Escape');
  await createAndWizard('Demo4');
  await page.locator('.wopt').nth(3).click();                                          // 备份恢复路径
  await page.waitForTimeout(300);
  T.restoreModalTitle = await page.locator('.modal h3').first().textContent();
  T.restoreAcctField = await page.locator('#rvAcct').inputValue();                     // F10 恢复前建账
  await page.keyboard.press('Escape');

  // ===== 收尾扫描 =====
  const bodyText = await page.evaluate(() => document.body.innerText);
  T.hasNaN = /undefined|NaN/.test(bodyText);
  const html = await page.content();
  T.externalDeps = (html.match(/https?:\/\/|fetch\(|XMLHttpRequest|<link|cdn|@font-face/g) || []).length;
  await page.setViewportSize({ width: 1280, height: 820 });
  T.overflow1280 = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1);

  console.log(JSON.stringify({ T, errors }, null, 1));
  await browser.close();
})().catch(e => { console.error('FATAL', e.message); process.exit(1); });
