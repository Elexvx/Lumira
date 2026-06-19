import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync, copyFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const distRoot = fileURLToPath(new URL('../dist/', import.meta.url));
const safeAssetName = (name) => name.replaceAll('.', '__dot__');

const copyMatchingAssets = (targetType, matcher) => {
  const targetDir = join(distRoot, 'cdn-assets', targetType);
  mkdirSync(targetDir, { recursive: true });
  for (const name of readdirSync(distRoot)) {
    const source = join(distRoot, name);
    if (statSync(source).isFile() && matcher(name)) {
      copyFileSync(source, join(targetDir, safeAssetName(name)));
    }
  }
};

const copyStaticImages = () => {
  const staticDir = join(distRoot, 'static');
  if (!existsSync(staticDir)) {
    return;
  }
  const targetDir = join(distRoot, 'cdn-assets', 'img');
  mkdirSync(targetDir, { recursive: true });
  for (const name of readdirSync(staticDir)) {
    const source = join(staticDir, name);
    if (statSync(source).isFile()) {
      copyFileSync(source, join(targetDir, safeAssetName(name)));
    }
  }
};

const rewriteCssAssetUrls = () => {
  const cssDirs = [distRoot, join(distRoot, 'cdn-assets', 'css')];
  for (const dir of cssDirs) {
    if (!existsSync(dir)) {
      continue;
    }
    for (const name of readdirSync(dir)) {
      const filePath = join(dir, name);
      if (!statSync(filePath).isFile()) {
        continue;
      }
      if (!name.endsWith('.css') && !dir.endsWith('/css')) {
        continue;
      }
      const css = readFileSync(filePath, 'utf8').replace(
        /url\((['"]?)\.\/static\/([^)'"?]+)(\?[^)'"]*)?\1\)/g,
        (_match, quote, filename, query = '') => `url(${quote}/cdn-assets/img/${safeAssetName(filename)}${query}${quote})`,
      );
      writeFileSync(filePath, css);
    }
  }
};

const rewriteIndex = () => {
  const indexPath = join(distRoot, 'index.html');
  let html = readFileSync(indexPath, 'utf8');
  html = html.replace(/href="\/([^"]+\.css)"/g, (_match, file) => `href="/cdn-assets/css/${safeAssetName(file)}"`);
  html = html.replace(/src="\/([^"]+\.js)"/g, (_match, file) => `src="/cdn-assets/js/${safeAssetName(file)}"`);
  writeFileSync(indexPath, html);
};

const patchUmiRuntime = () => {
  const umiName = readdirSync(distRoot).find((name) => /^umi\..+\.js$/.test(name));
  if (!umiName) {
    throw new Error('Cannot find Umi runtime chunk in dist.');
  }
  const runtimePath = join(distRoot, 'cdn-assets', 'js', safeAssetName(umiName));
  let runtime = readFileSync(runtimePath, 'utf8');

  const originalPathResolver = 'function q(e,t="/"){return`${C(t)}${e.split("/").map(e=>encodeURIComponent(e)).join("/")}`}';
  const cdnPathResolver = 'function q(e,t="/"){if("/"===t&&"string"==typeof e){let t=e.split("/").pop();if(/\\.css(?:[?#]|$)/.test(e))return"/cdn-assets/css/"+t.replaceAll(".","__dot__");if(/\\.js(?:[?#]|$)/.test(e))return"/cdn-assets/js/"+t.replaceAll(".","__dot__")}return`${C(t)}${e.split("/").map(e=>encodeURIComponent(e)).join("/")}`}';
  if (!runtime.includes(originalPathResolver)) {
    throw new Error('Cannot patch Umi runtime path resolver.');
  }
  runtime = runtime.replace(originalPathResolver, cdnPathResolver);

  const originalScriptSource = 'if(e)return{src:e.getAttribute("src")};';
  const cdnScriptSource = 'if(e){let t=e.getAttribute("src")||"";return t=t.replace(/^.*\\/cdn-assets\\/js\\//,"").replaceAll("__dot__","."),{src:t}};';
  if (!runtime.includes(originalScriptSource)) {
    throw new Error('Cannot patch Umi runtime chunk source resolver.');
  }
  runtime = runtime.replace(originalScriptSource, cdnScriptSource);

  runtime = runtime.replace('function N(e){return L(e,".css")}', 'function N(e){return L(e,".css")||e.includes("/cdn-assets/css/")}');
  runtime = runtime.replaceAll('else if(L(t,".js"))', 'else if(L(t,".js")||t.includes("/cdn-assets/js/"))');

  writeFileSync(runtimePath, runtime);
};

if (!existsSync(distRoot)) {
  throw new Error('Build dist directory does not exist. Run pnpm build first.');
}

rmSync(join(distRoot, 'cdn-assets'), { recursive: true, force: true });
copyMatchingAssets('js', (name) => name.endsWith('.js'));
copyMatchingAssets('css', (name) => name.endsWith('.css'));
copyStaticImages();
rewriteCssAssetUrls();
rewriteIndex();
patchUmiRuntime();
