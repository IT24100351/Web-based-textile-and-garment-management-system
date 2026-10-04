import { spawn } from "node:child_process";
import fs from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const backendEnvironment = path.join(projectRoot, "backend", ".env");
const isWindows = process.platform === "win32";
const npmCommand = isWindows ? "npm.cmd" : "npm";
const pollIntervalMs = 300;
const progressIntervalMs = 15_000;

function configuredPort() {
  if (process.env.PORT) {
    return process.env.PORT;
  }
  if (!fs.existsSync(backendEnvironment)) {
    return "3000";
  }
  const portLine = fs.readFileSync(backendEnvironment, "utf8")
    .split(/\r?\n/)
    .find((line) => line.startsWith("PORT="));
  return portLine?.slice("PORT=".length).trim() || "3000";
}

const healthUrl = `http://127.0.0.1:${configuredPort()}/api/health`;
let apiReady = false;
let nextProgressAt = Date.now() + progressIntervalMs;

console.log(`[TGMS] Waiting for the API at ${healthUrl}...`);

while (!apiReady) {
  try {
    const response = await fetch(healthUrl, { signal: AbortSignal.timeout(2_000) });
    if (response.ok) {
      apiReady = true;
      break;
    }
  } catch {
    // The backend is still compiling, migrating, or opening its HTTP port.
  }
  if (!apiReady && Date.now() >= nextProgressAt) {
    console.log("[TGMS] API startup is still in progress; continuing to wait...");
    nextProgressAt = Date.now() + progressIntervalMs;
  }
  await new Promise((resolve) => setTimeout(resolve, pollIntervalMs));
}

console.log("[TGMS] API is ready. Starting Vite...");

const child = spawn(npmCommand, ["run", "dev", "--workspace", "frontend"], {
  cwd: projectRoot,
  env: process.env,
  detached: !isWindows,
  shell: isWindows,
  stdio: "inherit",
});

let stopping = false;

function stop(signal) {
  if (stopping || child.exitCode !== null) {
    return;
  }
  stopping = true;
  if (!isWindows && child.pid) {
    try {
      process.kill(-child.pid, signal);
      return;
    } catch {
      // Fall back to signaling the direct child below.
    }
  }
  child.kill(signal);
}

process.on("SIGINT", () => stop("SIGINT"));
process.on("SIGTERM", () => stop("SIGTERM"));

child.on("error", (error) => {
  console.error(`[TGMS] Unable to start Vite: ${error.message}`);
  process.exitCode = 1;
});

child.on("exit", (code, signal) => {
  if (signal) {
    process.exitCode = signal === "SIGINT" ? 130 : 143;
    return;
  }
  process.exitCode = code ?? 1;
});
