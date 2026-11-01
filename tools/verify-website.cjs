// npm install --prefix <test-dir> playwright
// PSF_PLAYWRIGHT=<test-dir>/node_modules/playwright node tools/verify-website.cjs <url> <output-dir>
const { chromium } = require(process.env.PSF_PLAYWRIGHT || 'playwright');
const fs=require('node:fs'),path=require('node:path');
(async()=>{
  const base=process.argv[2]||'http://127.0.0.1:8765';
  const output=process.argv[3]||'shots/website';fs.mkdirSync(output,{recursive:true});
  const browser=await chromium.launch({channel:'chrome',headless:true});
  try {
    const reports=[];
    for(const [name,width,height] of [['phone',393,852],['tablet',1024,768],['desktop',1440,900]]){
      const page=await browser.newPage({viewport:{width,height},reducedMotion:'reduce'});
      const errors=[];page.on('pageerror',e=>errors.push(e.message));
      await page.goto(base,{waitUntil:'networkidle'});
      await page.locator('img').evaluateAll(async images=>{
        images.forEach(i=>i.loading='eager');
        await Promise.all(images.map(i=>i.decode().catch(()=>{})));
      });
      for(const language of ['zh','en']){
        if(language==='en')await page.locator('#language').click();
        for(const dark of [false,true]){
          await page.evaluate(dark=>{localStorage.setItem('theme',dark?'dark':'light');},dark);
          await page.emulateMedia({colorScheme:dark?'dark':'light'});
          // Exercise the public theme button rather than assuming saved-key names.
          for(let n=0;n<3;n++){
            if(await page.evaluate(()=>document.documentElement.dataset.theme)===(dark?'dark':'light'))break;
            await page.locator('#theme').click();
          }
          const state=await page.evaluate(()=>({language:document.documentElement.lang,
            overflow:document.documentElement.scrollWidth>innerWidth+1,
            brokenImages:[...document.images].filter(i=>!i.complete||!i.naturalWidth).map(i=>i.src),
            body:document.body.innerText}));
          if(state.overflow||state.brokenImages.length||!state.body.includes('1.0'))throw Error(JSON.stringify(state));
          await page.screenshot({path:path.join(output,`${name}-${language}-${dark?'dark':'light'}.png`),fullPage:true});
          reports.push({name,language,dark,overflow:state.overflow,brokenImages:state.brokenImages});
        }
      }
      if(errors.length)throw Error(errors.join('\n'));
      await page.close();
    }
    fs.writeFileSync(path.join(output,'report.json'),JSON.stringify(reports,null,2));
    console.log(`PASS ${reports.length} viewport/language/theme combinations`);
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
