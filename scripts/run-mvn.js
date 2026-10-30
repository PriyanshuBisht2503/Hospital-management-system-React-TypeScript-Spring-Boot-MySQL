const { spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');

const rootDir = path.resolve(__dirname, '..');
const apiDir = path.resolve(rootDir, 'apps', 'api');

// Load .env if present
const envPath = path.join(rootDir, '.env');
if (fs.existsSync(envPath)) {
  const envContent = fs.readFileSync(envPath, 'utf8');
  for (const line of envContent.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (trimmed && !trimmed.startsWith('#')) {
      const idx = trimmed.indexOf('=');
      if (idx !== -1) {
        const key = trimmed.slice(0, idx).trim();
        let val = trimmed.slice(idx + 1).trim();
        if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
          val = val.slice(1, -1);
        }
        if (!process.env[key]) {
          process.env[key] = val;
        }
      }
    }
  }
}

function findMaven() {
  // 1. Check mvn in PATH
  try {
    const res = spawnSync('mvn', ['-v'], { shell: true });
    if (res.status === 0) return 'mvn';
  } catch (_) {}

  // 2. Check local mvnw.cmd in apps/api
  const localMvnw = path.join(apiDir, 'mvnw.cmd');
  if (fs.existsSync(localMvnw)) return localMvnw;

  // 3. Check known Windows installations
  const candidates = [
    'C:\\Users\\priya\\.m2\\wrapper\\dists\\apache-maven-3.9.16-bin\\5grr65jo27hi51sujmtcldfovl\\apache-maven-3.9.16\\bin\\mvn.cmd',
    'C:\\Program Files\\JetBrains\\IntelliJ IDEA 2026.1.4\\plugins\\maven\\lib\\maven3\\bin\\mvn.cmd',
    'C:\\Program Files\\NetBeans-23\\netbeans\\java\\maven\\bin\\mvn.cmd'
  ];

  for (const c of candidates) {
    if (fs.existsSync(c)) return c;
  }

  return 'mvn';
}

const mvnPath = findMaven();
const args = process.argv.slice(2);

console.log(`[maven] Running: "${mvnPath}" ${args.join(' ')} in ${apiDir}`);

const result = spawnSync(`"${mvnPath}"`, args, {
  cwd: apiDir,
  shell: true,
  stdio: 'inherit',
  env: process.env
});

process.exit(result.status ?? 0);
