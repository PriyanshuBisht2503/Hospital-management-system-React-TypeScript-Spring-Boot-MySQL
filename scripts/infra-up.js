const { execSync } = require('child_process');
const net = require('net');

function checkPort(port) {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    socket.setTimeout(2000);
    socket.on('connect', () => {
      socket.destroy();
      resolve(true);
    });
    socket.on('timeout', () => {
      socket.destroy();
      resolve(false);
    });
    socket.on('error', () => {
      resolve(false);
    });
    socket.connect(port, 'localhost');
  });
}

async function main() {
  console.log('[infra] Starting local development infrastructure...');
  let dockerSuccess = false;

  try {
    console.log('[infra] Attempting: docker compose up -d');
    execSync('docker compose up -d', { stdio: 'inherit' });
    dockerSuccess = true;
  } catch (err) {
    console.log('[infra] Docker Compose command not available or failed. Checking local MySQL service...');
  }

  // Check if MySQL is listening on 3306
  const isListening = await checkPort(3306);
  if (isListening) {
    console.log('[infra] MySQL is UP and listening on localhost:3306.');
    process.exit(0);
  }

  // If not listening, try to start Windows MySQL service
  try {
    console.log('[infra] Attempting to start Windows MySQL service (MySQL80)...');
    execSync('powershell -Command "Get-Service MySQL80 -ErrorAction SilentlyContinue | Start-Service"', { stdio: 'inherit' });
  } catch (err) {
    console.warn('[infra] Warning: Could not start MySQL80 service directly:', err.message);
  }

  const recheck = await checkPort(3306);
  if (recheck) {
    console.log('[infra] MySQL is UP and listening on localhost:3306.');
  } else {
    console.log('[infra] Note: Ensure MySQL is running on localhost:3306 before starting the backend.');
  }
}

main();
