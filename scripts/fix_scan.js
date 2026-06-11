const fs = require('fs');
const files = [
  'services/gateway-service/src/main/java/com/lumira/gateway/GatewayServiceApplication.java',
  'services/plugin-service/src/main/java/com/lumira/plugin/PluginServiceApplication.java',
  'services/job-executor/src/main/java/com/lumira/job/JobExecutorApplication.java',
  'services/message-service/src/main/java/com/lumira/message/MessageServiceApplication.java',
  'services/file-service/src/main/java/com/lumira/file/FileServiceApplication.java',
  'services/auth-service/src/main/java/com/lumira/auth/AuthServiceApplication.java'
];

for (const file of files) {
  let content = fs.readFileSync(file, 'utf8');
  if (content.includes('@SpringBootApplication') && !content.includes('com.lumira.common')) {
    const pkgMatch = content.match(/package com\.lumira\.invention\.([^;]+);/);
    if (pkgMatch) {
      const pkg = pkgMatch[1];
      content = content.replace(/@SpringBootApplication(\([^)]+\))?/, `@SpringBootApplication(scanBasePackages = { "com.lumira.${pkg}", "com.lumira.common" })`);
      fs.writeFileSync(file, content);
      console.log(`Fixed ${file}`);
    }
  }
}
