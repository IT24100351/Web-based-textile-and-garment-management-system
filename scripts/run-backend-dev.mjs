import { spawn } from "node:child_process";
import fs from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const backendDirectory = path.join(projectRoot, "backend");
const isWindows = process.platform === "win32";
const wrapper = isWindows ? "mvnw.cmd" : "./mvnw";
const targetDirectory = path.join(backendDirectory, "target");

function findInvalidClassArtifact(directory) {
  if (!fs.existsSync(directory)) return false;
  const pending = [directory];
  while (pending.length > 0) {
    const current = pending.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const entryPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        pending.push(entryPath);
      } else if (entry.name.endsWith(".class")) {
        if (/ \d+\.class$/.test(entry.name)) {
          return "duplicate generated class files";
        }

        // Eclipse's compiler can emit a loadable class that throws this error at runtime.
        // Maven may then regard that class as up to date and skip recompiling its source.
        const classBytes = fs.readFileSync(entryPath);
        if (classBytes.includes(Buffer.from("Unresolved compilation problem"))) {
          return "class files containing unresolved compilation errors";
        }
      }
    }
  }
  return false;
}

const cleanRequested = process.env.TGMS_CLEAN_START === "true";
const invalidArtifactReason = findInvalidClassArtifact(targetDirectory);
const shouldClean = cleanRequested || invalidArtifactReason;
const mavenArguments = [
  "-Dmaven.test.skip=true",
  ...(shouldClean ? ["clean"] : []),
  "spring-boot:run",
];

if (invalidArtifactReason) {
  console.warn(`[TGMS] Detected ${invalidArtifactReason}; performing one clean rebuild.`);
} else if (cleanRequested) {
  console.log("[TGMS] Clean backend startup requested.");
} else {
  console.log("[TGMS] Starting backend with incremental compilation.");
}

const child = spawn(wrapper, mavenArguments, {
  cwd: backendDirectory,
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
  console.error(`[TGMS] Unable to start the Maven wrapper: ${error.message}`);
  process.exitCode = 1;
});

child.on("exit", (code, signal) => {
  if (signal) {
    process.exitCode = signal === "SIGINT" ? 130 : 143;
    return;
  }
  process.exitCode = code ?? 1;
});
