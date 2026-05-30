const fs = require('fs');
const files = [
  'services/gateway-service/src/main/java/com/legendary/invention/gateway/GatewayServiceApplication.java',
  'services/plugin-service/src/main/java/com/legendary/invention/plugin/PluginServiceApplication.java',
  'services/job-executor/src/main/java/com/legendary/invention/job/JobExecutorApplication.java',
  'services/message-service/src/main/java/com/legendary/invention/message/MessageServiceApplication.java',
  'services/file-service/src/main/java/com/legendary/invention/file/FileServiceApplication.java',
  'services/auth-service/src/main/java/com/legendary/invention/auth/AuthServiceApplication.java'
];

for (const file of files) {
  let content = fs.readFileSync(file, 'utf8');
  if (content.includes('@SpringBootApplication') && !content.includes('com.legendary.invention.common')) {
    const pkgMatch = content.match(/package com\.legendary\.invention\.([^;]+);/);
    if (pkgMatch) {
      const pkg = pkgMatch[1];
      content = content.replace(/@SpringBootApplication(\([^)]+\))?/, `@SpringBootApplication(scanBasePackages = { "com.legendary.invention.${pkg}", "com.legendary.invention.common" })`);
      fs.writeFileSync(file, content);
      console.log(`Fixed ${file}`);
    }
  }
}
