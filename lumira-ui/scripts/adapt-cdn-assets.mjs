import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync, copyFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const distRoot = fileURLToPath(new URL('../dist/', import.meta.url));
const packageJsonPath = fileURLToPath(new URL('../package.json', import.meta.url));

const readPackageVersion = () => {
  try {
    return JSON.parse(readFileSync(packageJsonPath, 'utf8')).version || '0.1.0';
  } catch (_error) {
    return '0.1.0';
  }
};

const resolveBuildInfo = () => ({
  app: 'lumira-ui',
  version: process.env.UMI_APP_FRONTEND_VERSION || process.env.FRONTEND_VERSION || process.env.BUILD_VERSION || readPackageVersion(),
  buildTime: process.env.UMI_APP_BUILD_TIME || process.env.BUILD_TIME || new Date().toISOString(),
  gitCommit: process.env.UMI_APP_GIT_COMMIT || process.env.GIT_COMMIT || 'unknown',
  gitBranch: process.env.UMI_APP_GIT_BRANCH || process.env.GIT_BRANCH || 'unknown',
});

const safeAssetName = (name) => {
  const extensionIndex = name.lastIndexOf('.');
  if (extensionIndex <= 0) {
    return name.replaceAll('.', '__dot__');
  }
  return `${name.slice(0, extensionIndex).replaceAll('.', '__dot__')}${name.slice(extensionIndex)}`;
};

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

const copyLegacyAssetAliases = () => {
  const aliases = [
    {
      targetType: 'css',
      sourcePattern: /^src_9813bdc8__dot__.+\.css$/,
      aliasName: 'src_9813bdc8__dot__668d6288.css',
    },
  ];
  for (const { targetType, sourcePattern, aliasName } of aliases) {
    const targetDir = join(distRoot, 'cdn-assets', targetType);
    if (!existsSync(targetDir) || existsSync(join(targetDir, aliasName))) {
      continue;
    }
    const sourceName = readdirSync(targetDir).find((name) => sourcePattern.test(name));
    if (sourceName) {
      copyFileSync(join(targetDir, sourceName), join(targetDir, aliasName));
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
  html = html.replace(/href="\/(?!cdn-assets\/)([^"/]+\.css)"/g, (_match, file) => `href="/cdn-assets/css/${safeAssetName(file)}"`);
  html = html.replace(/src="\/(?!cdn-assets\/)([^"/]+\.js)"/g, (_match, file) => `src="/cdn-assets/js/${safeAssetName(file)}"`);
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
  const cdnPathResolver = 'function q(e,t="/"){if("/"===t&&"string"==typeof e){let t=e.split("/").pop(),s=t.lastIndexOf("."),i=s>0?t.slice(0,s).replaceAll(".","__dot__")+t.slice(s):t.replaceAll(".","__dot__");if(/\\.css(?:[?#]|$)/.test(e))return"/cdn-assets/css/"+i;if(/\\.js(?:[?#]|$)/.test(e))return"/cdn-assets/js/"+i}return`${C(t)}${e.split("/").map(e=>encodeURIComponent(e)).join("/")}`}';
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

const writeVersionManifest = () => {
  const manifestPath = join(distRoot, 'version.json');
  writeFileSync(manifestPath, `${JSON.stringify(resolveBuildInfo(), null, 2)}\n`);
};

if (!existsSync(distRoot)) {
  throw new Error('Build dist directory does not exist. Run pnpm build first.');
}

rmSync(join(distRoot, 'cdn-assets'), { recursive: true, force: true });
copyMatchingAssets('js', (name) => name.endsWith('.js'));
copyMatchingAssets('css', (name) => name.endsWith('.css'));
copyStaticImages();
copyLegacyAssetAliases();
rewriteCssAssetUrls();
rewriteIndex();
patchUmiRuntime();
writeVersionManifest();
