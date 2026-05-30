import sys

def refactor_install():
    with open('scripts/install-platform.mjs', 'r') as f:
        orig = f.readlines()
    
    out = []
    i = 1
    for line in orig:
        if i == 12:
            out.append(line)
            out.append("import { parseEnvFile, setEnvValue, randomSecret, randomBase64Secret, defaultCapacityProfiles } from './lib/env-utils.mjs';\n")
            out.append("import { run as execRun, output as execOutput, optionalOutput as execOptionalOutput, createLogger, resolveRepoRoot } from './lib/exec-utils.mjs';\n")
            out.append("import { waitForHttp, probeHttp } from './lib/http-utils.mjs';\n")
            out.append("const log = createLogger('install');\n")
        elif i == 13 or i == 14:
            if i == 14:
                out.append("const repoRoot = resolveRepoRoot(import.meta.url);\n")
        elif 40 <= i <= 102:
            pass # delete defaultCapacityProfiles
        elif 103 <= i <= 105:
            pass # delete log
        elif 124 <= i <= 142:
            if i == 124:
                out.append("""function run(command, commandArgs, options = {}) {
  try {
    return execRun(command, commandArgs, { cwd: repoRoot, ...options });
  } catch (err) {
    process.exit(err.status ?? 1);
  }
}
""")
        elif 143 <= i <= 158:
            if i == 143:
                out.append("""function output(command, commandArgs, options = {}) {
  return execOutput(command, commandArgs, { cwd: repoRoot, check: false, ...options });
}
""")
        elif 160 <= i <= 174:
            pass # delete parseEnvFile
        elif 176 <= i <= 182:
            pass # delete setEnvValue
        elif 184 <= i <= 186:
            pass # delete randomSecret
        elif 188 <= i <= 190:
            pass # delete randomBase64Secret
        else:
            out.append(line)
        i += 1
        
    with open('scripts/install-platform.mjs', 'w') as f:
        f.writelines(out)

refactor_install()
print("done")
