// Runs every page check in this folder one after the other and prints a summary (npm run check).
// Exit code 0 only if all of them pass. Each check is a small stand-alone script: open it to see what it checks.
import { readdirSync } from 'fs';
import { spawnSync } from 'child_process';
import { fileURLToPath } from 'url';
import path from 'path';

const here = path.dirname(fileURLToPath(import.meta.url));
const checks = readdirSync(here).filter(f => f.endsWith('.mjs') && f !== 'common.mjs' && f !== 'run-all.mjs').sort();
let failed = 0;
for (const check of checks) {
    const r = spawnSync(process.execPath, [path.join(here, check)], { encoding: 'utf8' });
    const ok = r.status === 0;
    if (!ok) failed++;
    console.log((ok ? 'PASS  ' : 'FAIL  ') + check);
    if (!ok) console.log((r.stdout || '') + (r.stderr || ''));
}
console.log(`\n${checks.length - failed} of ${checks.length} checks passed`);
process.exit(failed ? 1 : 0);
