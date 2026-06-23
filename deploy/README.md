# Vercel 鍓嶇 + 鍗曚綋寰湇鍔″悗绔儴缃茶鏄?

杩欏閮ㄧ讲鐢ㄤ簬楂樺畨鍏ㄣ€侀珮绋冲畾鐨勫噯鐢熶骇婕旂ず鐜銆傚墠绔墭绠″湪 Vercel锛屾湇鍔″櫒璐熻矗鍗曚綋寰湇鍔″悗绔拰缁熶竴 API 鍏ュ彛銆?

## 閮ㄧ讲褰㈡€?

```text
鐢ㄦ埛娴忚鍣?
  -> Vercel 鍓嶇
    -> /api/** rewrite 鍒?https://saas.elexvx.com/api/**
    -> /ws/**  rewrite 鍒?https://saas.elexvx.com/ws/**

saas.elexvx.com / HTTPS / CDN / WAF
  -> 鏈嶅姟鍣?edge-proxy Nginx (80/443)
    -> /api/** 鍙嶅悜浠ｇ悊鍒?lumira-api-proxy
    -> /ws/** 鍙嶅悜浠ｇ悊鍒?lumira-api-proxy
    -> /health 鍙嶅悜浠ｇ悊鍒?lumira-api-proxy
  -> lumira-api-proxy
    -> /api/** 鍙嶅悜浠ｇ悊鍒?lumira-server
    -> /ws/** 鍙嶅悜浠ｇ悊鍒?lumira-server
    -> /api/health 鍙嶅悜浠ｇ悊鍒?lumira-server
  -> lumira-server
    -> auth module
    -> system module
    -> file module
    -> message module
    -> plugin module
    -> localization module
    -> job module
  -> MySQL / Redis / XXL-Job
  -> Nacos锛堜粎涓烘湭鏉ユ媶鍒嗗拰閰嶇疆涓績棰勭暀锛岄粯璁や笉鍚姩锛?
```

## 榛樿鍚姩缁勪欢

- MySQL锛歚mysql:8.4`
- Redis锛歚redis:7.4`
- Nacos锛歚nacos/nacos-server:v3.2.1`锛岄粯璁や笉鍚姩锛涘彧鏈?`NACOS_CONFIG_ENABLED=true`銆乣NACOS_DISCOVERY_ENABLED=true` 鎴栨樉寮忎紶鍏?`--nacos` 鏃跺惎鍔?
- XXL-Job Admin锛歚xuxueli/xxl-job-admin:3.4.0`
- edge-proxy锛?0/443 瀵瑰缁熶竴鍏ュ彛锛岃礋璐?HTTPS 缁堟鍜岃矾鐢?
- api-proxy锛歂ginx 鍚庣缁熶竴鍏ュ彛
- lumira-server锛氬崟浣撳井鏈嶅姟鍚庣鍏ュ彛锛岃仛鍚堢郴缁熴€佽璇併€佹枃浠躲€佹秷鎭€佹彃浠躲€佹湰鍦板寲鍜屼换鍔℃ā鍧?

`lumira-ui` 瀹瑰櫒鍙綔涓烘湰鍦板鐢ㄩ瑙堬紝榛樿涓嶉殢鐢熶骇閮ㄧ讲鍚姩銆傛寮忓墠绔敱 Vercel 鎵樼銆?

## 涓€閿儴缃插悗绔畬鏁村钩鍙?

骞冲彴瀹夎鍜岀幆澧冩娴嬬粺涓€鐢变竴涓剼鏈垎姝ユ墽琛屻€傚彧妫€娴嬫湇鍔″櫒鐜鏃惰繍琛岋細

```bash
node bin/install-platform.mjs --check-only
```

涓ユ牸妯″紡浼氭妸璀﹀憡涔熻涓哄け璐ワ紝閫傚悎 CI 鎴栨寮忎氦浠樺墠妫€鏌ワ細

```bash
node bin/install-platform.mjs --check-only --strict
```

闇€瑕佺粰鑷姩鍖栧钩鍙拌鍙栨椂杈撳嚭绾?JSON锛?

```bash
node bin/install-platform.mjs --check-only --json
```

棣栨瀹夎鎴栨湇鍔″櫒鎹㈣鏍兼椂锛屾帹鑽愬厛杩愯浜や簰寮忓畨瑁呭櫒锛?

```bash
node bin/install-platform.mjs
```

瀹夎鍣ㄤ細鍒嗘瀹屾垚锛?

- 鎺㈡祴 CPU銆佸唴瀛樸€佺鐩樸€佺郴缁熸灦鏋勩€?
- 妫€娴?Node.js銆丏ocker銆丏ocker Compose銆佺鍙ｅ崰鐢ㄣ€佸繀濉幆澧冨彉閲忋€佸閮?MySQL 杩為€氭€у拰璧勬簮妗ｅ缓璁€?
- 浜や簰纭 API 鍩熷悕銆佸墠绔?Origin銆佹槸鍚﹀惎鐢ㄥ唴缃?MySQL銆丯acos銆佸墠绔鍣ㄥ拰瑙傛祴鏍堛€?
- 鎸夋湇鍔″櫒瑙勬牸鑷姩鍐欏叆 `deploy/.env` 鐨?JVM銆佸鍣ㄥ唴瀛樸€丷edis銆佹暟鎹簱杩炴帴姹犮€乀omcat 绾跨▼姹犮€侀檺娴佸拰鏃ュ織杞浆鍙傛暟銆?
- 妫€鏌?Docker锛汱inux 鏈嶅姟鍣ㄧ己灏?Docker 鏃跺彲鑷姩瀹夎銆?
- 鎸夐樁娈靛惎鍔ㄥ熀纭€缁勪欢銆乣lumira-server`銆丄PI proxy銆佸彲閫夊墠绔鍣ㄥ拰鍙€夎娴嬫爤銆?
- 鑷姩杩愯閮ㄧ讲鍋ュ悍妫€鏌ュ拰杞婚噺骞跺彂鍐掔儫銆?

鏃犱汉鍊煎畧瀹夎锛?

```bash
node bin/install-platform.mjs --yes
```

甯哥敤鍙傛暟锛?

```bash
node bin/install-platform.mjs \
  --api-domain=saas.elexvx.com \
  --lumira-ui-origin=https://saas.elexvx.com \
  --yes
```

濡傛灉闇€瑕佸畬鍏ㄦ湰鏈哄寲婕旂ず锛屽彲鍚敤鍐呯疆 MySQL銆丯acos 鍜屽墠绔鍣細

```bash
node bin/install-platform.mjs --local-mysql --nacos --lumira-ui
```

鏃ュ父宸叉湁鐜鏇存柊鎺ㄨ崘鐩存帴鎷夊彇 CI 浜х墿锛?

浠庝粨搴撴牴鐩綍杩愯锛?

```bash
node bin/deploy-container.mjs --pull
```

`main` 鍒嗘敮 CI 浼氬湪鍚庣 Maven 娴嬭瘯銆佸墠绔?lint/typecheck/test 閮介€氳繃鍚庯紝鑷姩鏋勫缓骞跺彂甯冮暅鍍忥細

- `ghcr.io/elexvx/lumira/lumira-server:main`
- `ghcr.io/elexvx/lumira/lumira-ui:main`
- `ghcr.io/elexvx/lumira/lumira-server:sha-<12浣嶆彁浜?`
- `ghcr.io/elexvx/lumira/lumira-ui:sha-<12浣嶆彁浜?`

鏈嶅姟鍣ㄤ娇鐢?`deploy/.env` 涓殑 `LUMIRA_SERVER_IMAGE` 鍜?`LUMIRA_FRONTEND_IMAGE` 鍐冲畾瑕侀儴缃插摢涓暅鍍忋€傝拷姹傚彲鍥炴粴鍜屽彲澶嶇幇鏃讹紝寤鸿鎶?`main` 鏀规垚瀵瑰簲鐨?`sha-<鎻愪氦>` tag銆傚鏋滈渶瑕佸湪鏈嶅姟鍣ㄦ湰鏈洪噸鏂扮紪璇戦暅鍍忥紝鍙户缁娇鐢細

```bash
node bin/deploy-container.mjs --rebuild
```

榛樿闀滃儚鏋勫缓涓嶄細涓嬭浇 OpenTelemetry Java agent锛岄伩鍏嶉粯璁ゅ叧闂殑瑙傛祴鑳藉姏闃诲鍙戝竷鏋勫缓銆傜敓浜х幆澧冮渶瑕佸惎鐢?`OTEL_JAVAAGENT_ENABLED=true` 鏃讹紝鍏堝湪鏋勫缓鐜璁剧疆鍙俊鍒跺搧鍦板潃锛?

```bash
OTEL_JAVAAGENT_URL=https://your-artifact-repository/opentelemetry-javaagent.jar \
node bin/deploy-container.mjs --rebuild
```

濡傛灉杩愯鏃跺紑鍚簡 agent 浣嗛暅鍍忓唴娌℃湁闈炵┖ agent 鏂囦欢锛宍lumira-server` 浼氬惎鍔ㄥけ璐ュ苟杈撳嚭鏄庣‘閿欒锛岄伩鍏嶉潤榛樹涪澶?trace銆?

濡傛灉鏈嶅姟鍣ㄦ棤娉曠ǔ瀹氳闂?Docker Hub锛屽彲鍦ㄦ湰鏈?rebuild 鏃跺垏鍒板彲淇￠暅鍍忔簮锛?

```bash
MAVEN_IMAGE=registry.example.com/maven:3.9.11-eclipse-temurin-21 \
JRE_IMAGE=registry.example.com/eclipse-temurin:21-jre \
NODE_IMAGE=registry.example.com/node:22-bookworm-slim \
NGINX_IMAGE=registry.example.com/nginx:1.29-alpine \
node bin/deploy-container.mjs --rebuild
```


娉ㄦ剰锛氫笂闈㈢殑閲嶅缓鍛戒护浼氫繚鐣欑幇鏈?MySQL 鏁版嵁锛屼笉浼氶噸缃?`admin` 瀵嗙爜銆傚叏鏂伴儴缃茬殑榛樿绠＄悊鍛樿处鍙锋潵鑷?Flyway 鍩虹嚎鏁版嵁锛?

- 鐢ㄦ埛鍚嶏細`admin`
- 鍒濆瀵嗙爜锛歚123456`
- 鐢熶骇鐜鍙€氳繃 `LUMIRA_INITIAL_ADMIN_PASSWORD` 瑕嗙洊棣栨鐧诲綍瀵嗙爜锛涜鐩栧彧浼氬湪 `admin` 浠嶅浜庡嚭鍘傚瘑鐮佹椂鐢熸晥銆?- 棣栨鐧诲綍鍚庝細寮哄埗淇敼鍒濆瀵嗙爜

濡傛灉闇€瑕佸湪娴嬭瘯鐜褰诲簳閲嶈鏁版嵁搴擄紝鍏堢‘璁ゆ暟鎹彲浠ュ垹闄わ紝鍐嶆墽琛岋細

```bash
node bin/deploy-container.mjs --reset
node bin/deploy-container.mjs --rebuild
```

`--reset` 浼氬垹闄ゆ暟鎹簱銆佷笂浼犳枃浠躲€佹彃浠舵枃浠跺拰浠诲姟鏃ュ織鏁版嵁锛屼笉鑳界敤浜庨渶瑕佷繚鐣欎笟鍔℃暟鎹殑鐜銆傝剼鏈細瑕佹眰鍦ㄤ氦浜掔粓绔緭鍏?`DELETE_LEGENDARY_DATA`锛汣I 鎴栬嚜鍔ㄥ寲鐜蹇呴』鏄惧紡璁剧疆 `DEPLOY_RESET_CONFIRM=DELETE_LEGENDARY_DATA`锛屽惁鍒欐嫆缁濇墽琛屻€?

榛樿閮ㄧ讲鎸?4C4G 灏忓瀷鏈嶅姟鍣ㄦ敹鏁涜祫婧愬崰鐢細Java 鏈嶅姟闄愬埗鍫嗘瘮渚嬪拰鍏冪┖闂达紝Tomcat 绾跨▼姹犮€丠ikari 杩炴帴姹犮€丷edis 鍐呭瓨銆丏ocker 鏃ュ織鍜?API 鍏ュ彛闄愭祦閮芥湁榛樿涓婇檺銆傞珮娴侀噺鏃朵紭鍏堣繑鍥?429 鎴栨帓闃燂紝鑰屼笉鏄 JVM銆佹暟鎹簱杩炴帴鍜岀鐩樻棩蹇楁妸鏈嶅姟鍣ㄦ墦婊°€?

棣栨杩愯浼氳嚜鍔ㄧ敓鎴?`deploy/.env`锛屽苟涓烘暟鎹簱銆丣WT銆佹彃浠剁鍚嶃€佷换鍔″唴閮ㄨ皟鐢ㄧ瓑閰嶇疆鐢熸垚闅忔満瀵嗛挜銆?

鏂囦欢瀹夊叏鎵弿榛樿浣跨敤鍐呯疆杞婚噺瑙勫垯寮曟搸锛屼笉渚濊禆澶栭儴杩涚▼銆傜敓浜х幆澧冮渶瑕佹帴鍏?ClamAV 鏃讹紝鍦?`deploy/.env` 璁剧疆锛?

```bash
LUMIRA_FILE_SECURITY_SCAN_MODE=CLAMAV
LUMIRA_FILE_SECURITY_SCAN_CLAMAV_HOST=127.0.0.1
LUMIRA_FILE_SECURITY_SCAN_CLAMAV_PORT=3310
LUMIRA_FILE_SECURITY_SCAN_TIMEOUT_MILLIS=3000
```

鎵弿浠嶇敱 File owner 鐨勫紓姝ュ鐞嗕换鍔℃墽琛岋紝涓婁紶 HTTP 鍥炲寘涓嶄細绛夊緟澶栭儴鎵弿锛汣lamAV 涓嶅彲鐢ㄦ椂浠诲姟澶辫触骞惰繘鍏ユ棦鏈夐噸璇?姝讳俊娌荤悊锛屼笉浼氭妸鏂囦欢璇爣涓哄畨鍏ㄣ€?

鍥剧墖 OCR 榛樿鍏抽棴锛屼絾 OCR 浠诲姟浠嶄細鍐欏叆 `OCR_RESULT/SKIPPED` 浜х墿骞舵垚鍔熺粨鏉燂紝閬垮厤寮傛闃熷垪鍙嶅澶辫触銆傜敓浜х幆澧冮渶瑕?OCR 鏃讹紝鍦ㄩ暅鍍忔垨瀹夸富鏈轰晶鍑嗗 Tesseract锛屽苟璁剧疆锛?

```bash
LUMIRA_FILE_OCR_MODE=TESSERACT
LUMIRA_FILE_OCR_TESSERACT_COMMAND=tesseract
LUMIRA_FILE_OCR_LANGUAGES=eng+chi_sim
LUMIRA_FILE_OCR_TIMEOUT_MILLIS=5000
```

OCR 鍚屾牱鐢?File owner 鐨勫紓姝ュ鐞嗕换鍔℃墽琛岋紱鎶藉彇鍒版枃鏈椂浼氬啓鍏?`TEXT_CONTENT` artifact锛屼緵 AI owner 閫氳繃 `FileInternalApi` 鐨勫彧璇诲绾︽秷璐广€?

鍥剧墖缂╃暐鍥惧悓鏍疯蛋 File owner 寮傛澶勭悊浠诲姟銆傛湰鍦板瓨鍌ㄤ細鐢熸垚 `.thumb.jpg` 骞跺啓鍏?`THUMBNAIL_RESULT/GENERATED`锛涜繙绋嬪璞″瓨鍌ㄥ湪鏈帴鍏ュ叿浣?provider 鍘熺敓缂╃暐鍥惧墠浼氬啓鍏?`THUMBNAIL_RESULT/DEFERRED_REMOTE_STORAGE`锛屼换鍔℃垚鍔熺粨鏉燂紝閬垮厤闃熷垪鍙嶅閲嶈瘯銆?

閮ㄧ讲瀹屾垚鍚庤剼鏈細鑷姩妫€鏌ワ細

- 瀵瑰鍏ュ彛锛歚https://saas.elexvx.com/health`
- API 鍋ュ悍妫€鏌ワ細`https://saas.elexvx.com/api/health`
- 鐗堟湰妫€鏌ワ細`https://saas.elexvx.com/api/version`
- 骞冲彴鏇存柊鎻愰啋锛氬悗鍙?`绯荤粺鐩戞帶 -> 骞冲彴鏇存柊` 浼氬彧璇绘鏌?GitHub 鏈€鏂版彁浜わ紱榛樿鏇存柊婧愪负 `https://api.github.com/repos/Elexvx/lumira/commits/main`锛屽闇€鏇挎崲瀹樻柟鏇存柊婧愶紝鍙湪 `deploy/.env` 璁剧疆 `PLATFORM_UPDATE_SOURCE_URL`銆?- 骞冲彴鎵嬪姩鏇存柊锛氭帹鑽愰厤缃?`PLATFORM_UPDATE_MANIFEST_URL` 鎸囧悜瀹樻柟 release manifest銆傚悗鍙板彂鐜版柊鐗堟湰鍚庯紝鍙€氳繃瀹夸富鏈轰晶 `lumira-updater` 鎵嬪姩瀹夎銆備笟鍔″鍣ㄥ彧璋冪敤鏈満 updater锛屼笉鐩存帴鎵ц Docker 鎴?shell銆?- 鍚姩 updater 绀轰緥锛?
```bash
PLATFORM_UPDATE_AGENT_TOKEN=replace-with-strong-local-token \
node bin/lumira-updater.mjs
```

`deploy/.env` 涓繚鎸佸悓涓€涓?token锛?
```text
PLATFORM_UPDATE_MANIFEST_URL=https://your-release-host/lumira-release-manifest.json
PLATFORM_UPDATE_AGENT_URL=http://127.0.0.1:9788
PLATFORM_UPDATE_AGENT_TOKEN=replace-with-strong-local-token
```

婕旂粌 updater 娴佺▼浣嗕笉鏀瑰啓 `.env`銆佷笉鎵ц閮ㄧ讲鍛戒护鏃讹細

```bash
LUMIRA_UPDATER_DRY_RUN=true node bin/lumira-updater.mjs --dry-run
```
- lumira-server 鍋ュ悍妫€鏌ワ細`http://127.0.0.1:8080/actuator/health`
- 鍏紑鐧诲綍閰嶇疆鎺ュ彛锛歚https://saas.elexvx.com/api/v1/public/login-capabilities`

## 鍙娴嬫€ч棴鐜?

榛樿閮ㄧ讲涓嶅惎鍔ㄨ娴嬫爤銆傞渶瑕?Prometheus 鎸囨爣銆丱penTelemetry trace銆丩oki 鏃ュ織銆乀empo trace 瀛樺偍鍜?Grafana 鐪嬫澘鏃惰繍琛岋細

```bash
node bin/deploy-container.mjs --rebuild --observability
```

瑙傛祴绔彛榛樿鍙粦瀹氭湰鏈猴細

- Grafana锛歚http://127.0.0.1:3001`
- Prometheus锛歚http://127.0.0.1:9090`
- Loki锛歚http://127.0.0.1:3100`
- Tempo锛歚http://127.0.0.1:3200`
- Alloy锛歚http://127.0.0.1:12345`

Grafana 浼氳嚜鍔?provision Prometheus銆丩oki銆乀empo 鏁版嵁婧愬拰 `Lumira Observability Overview` 鐪嬫澘銆俙lumira-server` 浼氭毚闇?`/actuator/prometheus`锛屽苟鍦ㄥ惎鐢ㄨ娴嬫爤鏃堕€氳繃 OpenTelemetry Java Agent 鎶?trace 鍙戦€佸埌 Alloy銆?

4C4G 鏈嶅姟鍣ㄤ笂涓嶅缓璁父椹诲畬鏁磋娴嬫爤锛涢渶瑕佹帓鏌ユ€ц兘闂鏃剁煭鏃跺紑鍚紝鎺掓煡缁撴潫鍚庡仠姝㈣娴嬫爤閲婃斁鍐呭瓨銆?

## 4C4G 绋冲畾杩愯寤鸿

- 榛樿浣跨敤澶栭儴鎴?1Panel MySQL锛涙湰浠撳簱鍐呯疆 MySQL 浠呯敤浜?`local-mysql` profile銆?
- 榛樿涓嶅惎鍔?Nacos锛涘綋鍓嶅崟浣撳井鏈嶅姟妯″紡涓嶄緷璧栨湇鍔″彂鐜般€傜‘瀹炶婕旂粌鏈潵鎷嗗垎鏃讹紝鍙繍琛?`node bin/deploy-container.mjs --rebuild --nacos`銆?
- 榛樿涓嶅惎鍔?`lumira-ui` 瀹瑰櫒锛屾寮忓墠绔蛋 Vercel锛涙湇鍔″櫒鍙壙鎷呭悗绔拰 API proxy銆?
- `deploy/.env` 閲岀殑 `*_MEM_LIMIT`銆乣SERVER_TOMCAT_THREADS_MAX`銆乣SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` 鍜?`SAAS_TRAFFIC_*_QPS` 鏄皬鏈哄櫒瀹归噺闂搁棬銆傚厛鍘嬫祴瑙傚療锛屽啀閫愭璋冨ぇ銆?
- API proxy 瀵瑰崟 IP 鍋氬熀纭€闄愭祦鍜岃繛鎺ユ暟闄愬埗锛涗笟鍔″眰 Sentinel 缁х画淇濇姢鐧诲綍銆佸叕寮€閰嶇疆銆侀獙璇佺爜鍜屽悗绔矾鐢便€?
- Redis 榛樿 `maxmemory=256mb` 涓斾娇鐢?`allkeys-lru`锛岄伩鍏嶇紦瀛樻垨浼氳瘽宄板€兼妸瀹夸富鏈哄唴瀛樻嫋鍨€?
- Docker 鏃ュ織榛樿杞浆锛岄伩鍏嶉珮娴侀噺閿欒鏃ュ織鎾戞弧纾佺洏銆?

閮ㄧ讲鍚庡彲浠ヨ窇涓€涓交閲忓帇鍔涘啋鐑燂細

```bash
LOAD_SMOKE_BASE_URL=https://saas.elexvx.com \
LOAD_SMOKE_DURATION_MS=30000 \
LOAD_SMOKE_CONCURRENCY=24 \
LOAD_SMOKE_RPS=48 \
node bin/load-smoke.mjs
```

濡傛灉闇€瑕侀獙璇佺櫥褰曞悗鐨勯灞忔帴鍙ｏ紝鍑嗗涓€涓笉闇€瑕佷簩娆￠獙璇佸拰寮哄埗鏀瑰瘑鐨勬祴璇曡处鍙峰悗杩愯锛?

```bash
AUTH_LOAD_BASE_URL=https://saas.elexvx.com \
AUTH_LOAD_USERNAME=admin \
AUTH_LOAD_PASSWORD='replace-with-test-password' \
AUTH_LOAD_DURATION_MS=30000 \
AUTH_LOAD_CONCURRENCY=16 \
AUTH_LOAD_RPS=32 \
node bin/auth-load-smoke.mjs
```

鍏綉鍩熷悕妫€鏌ワ細

```bash
LOAD_SMOKE_BASE_URL=https://saas.elexvx.com \
LOAD_SMOKE_DURATION_MS=30000 \
LOAD_SMOKE_CONCURRENCY=24 \
LOAD_SMOKE_RPS=48 \
node bin/load-smoke.mjs
```

## Vercel 鍓嶇閰嶇疆

褰撳墠 `lumira-ui/vercel.json` 宸插皢鍓嶇璇锋眰杞彂鍒板悗绔煙鍚嶏細

```json
{
  "source": "/api/:path*",
  "destination": "https://saas.elexvx.com/api/:path*"
}
```

鍓嶇榛樿浣跨敤鍚屾簮 `/api`銆傚鏋滀笉浣跨敤 Vercel rewrites锛屼篃鍙互鍦?Vercel 鐜鍙橀噺涓厤缃細

```text
UMI_APP_API_BASE_URL=https://saas.elexvx.com
```

## 鏈嶅姟鍣ㄥ煙鍚嶅拰 HTTPS

鎺ㄨ崘璁╀富鏈?Nginx銆?Panel銆佽礋杞藉潎琛″櫒銆丆DN 鎴?WAF 璐熻矗 HTTPS锛屽苟鍙嶅悜浠ｇ悊鍒板鍣?edge proxy锛?

```text
https://saas.elexvx.com -> http://127.0.0.1:80
```

榛樿瀵瑰鍙毚闇?80/443锛沗API_PROXY_BIND` 鍜?`FRONTEND_BIND` 鍙繚鐣欐湰鏈鸿皟璇曠敤閫斻€?

濡傛灉浣犲凡缁忔湁姝ｅ紡鍩熷悕鍜岃瘉涔︼紝鎶?`deploy/.env` 閲岀殑 `API_DOMAIN`銆乣FRONTEND_ORIGIN` 鍜?`CORS_ALLOWED_ORIGIN_PATTERNS` 涓€骞舵敼鎴愭寮忓€笺€?

## 鍗曠嫭鑷

婕旂ず鍓嶅彲浠ュ崟鐙繍琛岋細

```bash
node bin/check-deployment.mjs
```

## 澶囦唤涓庢仮澶?

鐢熶骇鍙樻洿銆佹彃浠跺崌绾у拰鏁版嵁杩佺Щ鍓嶏紝鍏堝垱寤哄钩鍙板浠斤細

```bash
bash deploy/backup-platform.sh
```

鑴氭湰浼氬鍑?MySQL銆丷edis RDB銆佷笂浼犳枃浠剁洰褰曘€佹彃浠剁洰褰曞拰 `deploy/.env` 蹇収锛岄粯璁や繚瀛樺埌 `backups/<鏃堕棿鎴?/`銆傚闇€鎸囧畾澶囦唤鏍圭洰褰曪細

```bash
BACKUP_ROOT=/opt/lumira/backups bash deploy/backup-platform.sh
```

婕旂粌澶囦唤鍛戒护閾句絾涓嶅啓鍏ユ暟鎹€佷笉璁块棶瀹瑰櫒锛?

```bash
DRY_RUN=1 BACKUP_ROOT=/tmp/lumira-backup-dry-run bash deploy/backup-platform.sh
```

鎭㈠鍒版祴璇曠幆澧冩垨鐏惧鐜锛?

```bash
bash deploy/restore-platform.sh backups/20260520-120000
```

鎭㈠鍓嶈纭鐩爣鐜鐨?`deploy/.env` 宸插氨浣嶏紝骞跺凡鍚姩 MySQL/Redis 瀹瑰櫒銆傛仮澶嶈剼鏈細瑕嗙洊鐩爣鏁版嵁搴撳苟閲嶅惎 Redis銆?

婕旂粌鎭㈠鍛戒护閾句絾涓嶅啓鍏ユ暟鎹簱銆佷笉閲嶅惎 Redis锛?

```bash
DRY_RUN=1 bash deploy/restore-platform.sh backups/20260520-120000
```

濡傛灉瑕佹鏌ュ叕缃戝悗绔煙鍚嶏細

```bash
DEPLOY_CHECK_BASE_URL=https://saas.elexvx.com \
DEPLOY_CHECK_BACKEND_URL=http://127.0.0.1:8080 \
node bin/check-deployment.mjs
```

## 甯哥敤鍛戒护

鏌ョ湅瀹瑰櫒鐘舵€侊細

```bash
node bin/deploy-container.mjs --ps
```

鏌ョ湅鏃ュ織锛?

```bash
node bin/deploy-container.mjs --logs
```

鍋滄瀹屾暣鍚庣閮ㄧ讲锛?

```bash
node bin/deploy-container.mjs --stop
```

鍋滄骞跺垹闄ゆ暟鎹嵎锛?

```bash
DEPLOY_RESET_CONFIRM=DELETE_LEGENDARY_DATA node bin/deploy-container.mjs --reset
```

`--reset` 浼氬垹闄ゆ暟鎹簱銆佷笂浼犳枃浠躲€佹彃浠舵枃浠跺拰浠诲姟鏃ュ織鏁版嵁锛屽彧鑳藉湪纭涓嶉渶瑕佷繚鐣欐暟鎹椂浣跨敤銆備笉瑕佹妸 `DEPLOY_RESET_CONFIRM` 鍐欏叆 `deploy/.env`銆丆I 榛樿鍙橀噺鎴栧叕寮€鑴氭湰閲岋紝鍙湪纭疄闇€瑕佹竻搴撶殑閭ｄ竴娆″懡浠ゅ墠涓存椂浼犲叆銆?

## 瀹夊叏閰嶇疆

- `deploy/.env` 涓嶈鎻愪氦鍒?Git銆?
- 瀵瑰鍙毚闇?`https://saas.elexvx.com`锛屽鍣ㄥ唴閮ㄦ湇鍔＄鍙ｅ彧鍦ㄥ唴缃戣闂€?
- `DB_PASSWORD`銆乣JWT_SECRET`銆乣FIELD_SECRET`銆乣PLUGIN_SIGNATURE_SECRET`銆乣SAAS_JOB_INTERNAL_TOKEN` 蹇呴』浣跨敤寮洪殢鏈哄€笺€?
- 鐙珛閮ㄧ讲鎻掍欢鏈嶅姟鏃堕厤缃?`SAAS_JOB_PLUGIN_SERVICE_BASE_URL`锛岃仛鍚堥儴缃插彲娌跨敤榛樿鐨?lumira-server 鍦板潃銆?
- `CORS_ALLOWED_ORIGIN_PATTERNS` 鍦ㄧ敓浜х幆澧冨彧淇濈暀瀹為檯 Vercel 鍩熷悕鍜岃嚜瀹氫箟鍓嶇鍩熷悕锛涙湰鍦拌皟璇曞湴鍧€浠呮斁鍏?dev/test 鐜銆?
- `LUMIRA_INITIAL_ADMIN_PASSWORD` can set a production-only first-login password; leave it unset to use the factory default `admin / 123456`, which still requires an immediate password change.
- HTTPS/CDN/WAF 鏀惧湪瀹瑰櫒鍓嶉潰锛孉PI proxy 鍙壙鎷呭鍣ㄥ唴鍙嶅悜浠ｇ悊銆?
- `XXL_JOB_EXECUTOR_ENABLED=false` 鍙敤浜?runtime smoke銆佸噯鐢熶骇 owner 婕旂粌鎴栦复鏃剁鐢ㄥ閮ㄨ皟搴︽敞鍐岋紱姝ｅ紡闇€瑕?XXL-JOB 璋冨害鏃朵繚鎸侀粯璁?`true` 骞堕厤缃?`XXL_JOB_ADMIN_ADDRESSES`銆乣XXL_JOB_ACCESS_TOKEN`銆?
- `XXL_JOB_EXECUTOR_LOG_HOST_PATH` 榛樿浣跨敤 `/opt/lumira/data/xxl-job/logs`锛岄儴缃插墠浼氭巿鏉冪粰瀹瑰櫒鍐?`app` 鐢ㄦ埛鍐欏叆銆?

## 鍏ュ彛绾﹀畾

- 鍓嶇璁块棶鍏ュ彛锛歏ercel 鍩熷悕
- 鍚庣鍏綉鍏ュ彛锛歚https://saas.elexvx.com`
- 鍓嶇璇锋眰鍚庣锛歚/api`
- WebSocket锛歚/ws`
- 鏈満 API proxy锛歚http://127.0.0.1:8000`
- 鏈満鍓嶇棰勮锛歚http://127.0.0.1:8001`
- 鏈満 lumira-server 鍋ュ悍妫€鏌ワ細`http://127.0.0.1:8080/actuator/health`
