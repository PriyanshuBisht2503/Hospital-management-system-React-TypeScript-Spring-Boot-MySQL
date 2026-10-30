const { spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');

const apiDir = path.resolve(__dirname, '..', 'apps', 'api');

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
  stdio: 'inherit'
});

process.exit(result.status ?? 0);
