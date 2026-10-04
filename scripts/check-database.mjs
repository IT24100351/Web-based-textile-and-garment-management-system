import { spawnSync } from "node:child_process";
import fs from "node:fs";
import { parseEnv } from "node:util";

const environment = parseEnv(fs.readFileSync(process.argv[2] ?? "backend/.env", "utf8"));
const url = environment.DB_URL;

if (!url?.startsWith("jdbc:mysql://")) {
  console.error("[TGMS] Error: DB_URL must be a MySQL JDBC URL in backend/.env.");
  process.exit(1);
}

const database = new URL(url.slice("jdbc:".length));
const result = spawnSync("mysql", [
  "--protocol=tcp",
  `--host=${database.hostname}`,
  `--port=${database.port || "3306"}`,
  `--user=${environment.DB_USERNAME}`,
  `--database=${database.pathname.slice(1)}`,
  "--connect-timeout=5",
  "--batch",
  "--skip-column-names",
  "-e", "SELECT 1",
], {
  env: { ...process.env, MYSQL_PWD: environment.DB_PASSWORD },
  encoding: "utf8",
});

if (result.error?.code === "ENOENT") {
  console.warn("[TGMS] MySQL client is unavailable; database login will be checked by the API.");
} else if (result.status !== 0) {
  const message = result.stderr?.trim().split("\n").at(-1) || result.error?.message || "Unknown error";
  console.error(`[TGMS] Database login failed: ${message}`);
  console.error("[TGMS] Check that the database and tgms_user account exist, then set DB_PASSWORD in backend/.env to that account's password. See README.md > Database setup.");
  process.exit(1);
} else {
  console.log("[TGMS] Database login check passed.");
}
