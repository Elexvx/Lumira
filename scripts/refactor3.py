import sys

def refactor_deploy():
    with open('scripts/deploy-container.mjs', 'r') as f:
        lines = f.readlines()
        
    lines.insert(9, "import { parseEnvFile, randomSecret, randomBase64Secret } from './lib/env-utils.mjs';\n")
    lines.insert(10, "import { run as execRun, output as execOutput, optionalOutput as execOptionalOutput, createLogger, resolveRepoRoot } from './lib/exec-utils.mjs';\n")
    lines.insert(11, "import { waitForHttp, probeHttp } from './lib/http-utils.mjs';\n")

    # Update line offsets by 3
    # 10,11 (now 13,14) -> repoRoot, log
    # 54-56 (now 57-59) -> delete log
    # 96-109 (now 99-112) -> replace run
    # 129-136 (now 132-139) -> replace output
    # 137-142 (now 140-145) -> replace optionalOutput
    # 223-225 (now 226-228) -> delete randomSecret
    # 227-229 (now 230-232) -> delete randomBase64Secret
    # 349-366 (now 352-369) -> delete parseEnvFile
    # 487-507 (now 490-510) -> delete probeHttp
    # 509-535 (now 512-538) -> delete waitForHttp

    # Instead of offsets, let's just process line-by-line using original line numbers (1-indexed)
    with open('scripts/deploy-container.mjs', 'r') as f:
        orig = f.readlines()
    
    out = []
    i = 1
    for line in orig:
        if i == 9:
            out.append(line)
            out.append("import { parseEnvFile, randomSecret, randomBase64Secret } from './lib/env-utils.mjs';\n")
            out.append("import { run as execRun, output as execOutput, optionalOutput as execOptionalOutput, createLogger, resolveRepoRoot } from './lib/exec-utils.mjs';\n")
            out.append("import { waitForHttp, probeHttp } from './lib/http-utils.mjs';\n")
            out.append("const log = createLogger('deploy');\n")
        elif i == 10 or i == 11:
            if i == 11:
                out.append("const repoRoot = resolveRepoRoot(import.meta.url);\n")
        elif 54 <= i <= 56:
            pass # delete log
        elif 96 <= i <= 108:
            if i == 96:
                out.append("""function run(command, commandArgs, options = {}) {
  try {
    return execRun(command, commandArgs, { cwd: repoRoot, ...options });
  } catch (err) {
    process.exit(err.status ?? 1);
  }
}
""")
        elif 129 <= i <= 135:
            if i == 129:
                out.append("""function output(command, commandArgs) {
  return execOutput(command, commandArgs, { cwd: repoRoot, check: false });
}
""")
        elif 137 <= i <= 140:
            if i == 137:
                out.append("""function optionalOutput(command, commandArgs) {
  return execOptionalOutput(command, commandArgs, { cwd: repoRoot });
}
""")
        elif 223 <= i <= 225:
            pass
        elif 227 <= i <= 229:
            pass
        elif 349 <= i <= 366:
            pass
        elif 487 <= i <= 507:
            pass
        elif 509 <= i <= 535:
            pass
        else:
            out.append(line)
        i += 1
        
    with open('scripts/deploy-container.mjs', 'w') as f:
        f.writelines(out)

refactor_deploy()
print("done")
