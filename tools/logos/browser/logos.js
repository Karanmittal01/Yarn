// Opens each brand's website in headless Chromium and saves logo candidates:
// h1 = the site's icon (SVG icons rendered at 512px), h2/h3 = screenshots of header logo elements.
// Usage: node logos.js jobs.json outdir
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const [, , jobsFile, outDir, prefix] = process.argv;
const P = prefix || 'h';
const jobs = JSON.parse(fs.readFileSync(jobsFile, 'utf8'));
const UA = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Safari/537.36';

async function renderSvg(ctx, svgText, file) {
  const page = await ctx.newPage();
  try {
    const data = 'data:image/svg+xml;base64,' + Buffer.from(svgText).toString('base64');
    await page.setViewportSize({ width: 512, height: 512 });
    await page.setContent(`<html><body style="margin:0;background:transparent"><img src="${data}" style="width:512px;height:512px;object-fit:contain"></body></html>`);
    await page.waitForTimeout(300);
    await page.screenshot({ path: file, omitBackground: true });
    return true;
  } catch (e) { return false; } finally { await page.close(); }
}

async function one(ctx, job) {
  const { slug, urls } = job;
  const page = await ctx.newPage();
  const result = { slug, saved: [], title: null };
  try {
    let ok = false;
    for (const url of urls) {
      try {
        await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });
        await page.waitForTimeout(parseInt(process.env.WAIT || '3000'));
        result.title = await page.title();
        if (!/just a moment|attention required|access denied|403|forbidden/i.test(result.title)) { ok = true; break; }
      } catch (e) { result.error = e.message.split('\n')[0]; }
    }
    if (!ok) return result;
    // 1. The site's icons: prefer SVG, then the largest raster.
    const icons = await page.evaluate(() => [...document.querySelectorAll('link[rel*="icon"], link[rel="mask-icon"]')]
      .map(l => ({ href: l.href, sizes: l.getAttribute('sizes') || '', type: l.getAttribute('type') || '' })));
    const svgIcon = icons.find(i => /\.svg(\?|$)/i.test(i.href) || /svg/i.test(i.type));
    if (svgIcon) {
      try {
        const r = await ctx.request.get(svgIcon.href, { timeout: 15000 });
        if (r.ok()) {
          const f = path.join(outDir, `${slug}.${P}1.png`);
          if (await renderSvg(ctx, await r.text(), f)) result.saved.push(P + '1');
        }
      } catch (e) {}
    }
    // 2. Logo elements in the page (header first), screenshotted as the site shows them.
    const handles = await page.$$('header img, header svg, a[class*="logo" i] img, a[class*="logo" i] svg, [class*="logo" i] img, [class*="logo" i] svg, img[src*="logo" i], img[alt*="logo" i], [id*="logo" i] img, [id*="logo" i] svg, a[href="/"] img, a[href="/"] svg');
    let n = 2;
    const seen = new Set();
    for (const h of handles) {
      if (n > 3) break;
      try {
        const box = await h.boundingBox();
        if (!box || box.width < 24 || box.height < 16 || box.y > 400) continue;
        const key = Math.round(box.x) + ':' + Math.round(box.y);
        if (seen.has(key)) continue;
        seen.add(key);
        const f = path.join(outDir, `${slug}.${P}${n}.png`);
        await h.screenshot({ path: f, omitBackground: true, timeout: 8000 });
        result.saved.push(P + n);
        n++;
      } catch (e) {}
    }
  } finally { await page.close(); }
  return result;
}

(async () => {
  const browser = await chromium.launch({ proxy: { server: process.env.HTTPS_PROXY } });
  const ctx = await browser.newContext({ deviceScaleFactor: 3, userAgent: UA, viewport: { width: 1280, height: 900 }, ignoreHTTPSErrors: true });
  const queue = jobs.slice();
  const out = [];
  async function worker() {
    while (queue.length) {
      const job = queue.shift();
      const r = await Promise.race([one(ctx, job), new Promise(res => setTimeout(() => res({ slug: job.slug, error: 'timeout' }), 75000))]).catch(e => ({ slug: job.slug, error: String(e) }));
      out.push(r);
      console.log(JSON.stringify(r));
    }
  }
  await Promise.all([worker(), worker(), worker(), worker()]);
  fs.writeFileSync(path.join(outDir, '..', 'pw_results.json'), JSON.stringify(out, null, 1));
  await browser.close();
})();
