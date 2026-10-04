import fs from "node:fs";
import net from "node:net";
import path from "node:path";
import tls from "node:tls";
import { parseEnv } from "node:util";

const environmentPath = path.resolve(process.argv[2] ?? "backend/.env");
const timeoutMs = 10_000;

function fail(message) {
  console.error(`[TGMS] SMTP check failed: ${message}`);
  process.exitCode = 1;
}

function responseReader(socket) {
  let buffer = "";
  let responseLines = [];
  const pending = [];

  function rejectPending(error) {
    while (pending.length > 0) {
      pending.shift().reject(error);
    }
  }

  socket.setEncoding("utf8");
  socket.setTimeout(timeoutMs);
  socket.on("timeout", () => socket.destroy(new Error("SMTP connection timed out.")));
  socket.on("error", rejectPending);
  socket.on("data", (chunk) => {
    buffer += chunk;
    while (buffer.includes("\n")) {
      const lineEnd = buffer.indexOf("\n");
      const line = buffer.slice(0, lineEnd).replace(/\r$/, "");
      buffer = buffer.slice(lineEnd + 1);
      responseLines.push(line);
      if (/^\d{3} /.test(line) && pending.length > 0) {
        const response = responseLines;
        responseLines = [];
        pending.shift().resolve(response);
      }
    }
  });

  return () => new Promise((resolve, reject) => pending.push({ resolve, reject }));
}

function responseCode(response) {
  return Number.parseInt(response.at(-1)?.slice(0, 3) ?? "", 10);
}

async function sendCommand(socket, readResponse, command, expectedCode) {
  socket.write(`${command}\r\n`);
  const response = await readResponse();
  const code = responseCode(response);
  if (code !== expectedCode) {
    throw new Error(`SMTP command was rejected with response code ${code || "unknown"}.`);
  }
  return response;
}

async function checkSmtp(environment) {
  if (environment.SMTP_ENABLED?.trim().toLowerCase() !== "true") {
    console.log("[TGMS] SMTP is disabled; skipping the authentication check.");
    return;
  }

  const host = environment.SMTP_HOST?.trim();
  const port = Number.parseInt(environment.SMTP_PORT ?? "587", 10);
  const username = environment.SMTP_USERNAME?.trim();
  const password = environment.SMTP_PASSWORD ?? "";
  const fromAddress = environment.MAIL_FROM?.trim();

  if (!host || !Number.isInteger(port) || !username || !password || !fromAddress) {
    throw new Error("SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, and MAIL_FROM are required.");
  }
  if (username.toLowerCase() !== fromAddress.toLowerCase()) {
    throw new Error("MAIL_FROM must match SMTP_USERNAME unless the provider authorizes an alias.");
  }

  let socket = net.connect({ host, port });
  try {
    let readResponse = responseReader(socket);
    const greeting = await readResponse();
    if (responseCode(greeting) !== 220) {
      throw new Error("The SMTP server did not provide a valid greeting.");
    }

    await sendCommand(socket, readResponse, "EHLO localhost", 250);
    await sendCommand(socket, readResponse, "STARTTLS", 220);
    socket.removeAllListeners();

    socket = tls.connect({ socket, servername: host });
    await new Promise((resolve, reject) => {
      socket.once("secureConnect", resolve);
      socket.once("error", reject);
    });
    readResponse = responseReader(socket);

    await sendCommand(socket, readResponse, "EHLO localhost", 250);
    await sendCommand(socket, readResponse, "AUTH LOGIN", 334);
    await sendCommand(
      socket,
      readResponse,
      Buffer.from(username).toString("base64"),
      334,
    );

    socket.write(`${Buffer.from(password).toString("base64")}\r\n`);
    const authenticationResponse = await readResponse();
    const authenticationCode = responseCode(authenticationResponse);
    if (authenticationCode === 535) {
      throw new Error(
        "Gmail rejected the credentials (535). Create a new Google App Password for SMTP_USERNAME and update SMTP_PASSWORD.",
      );
    }
    if (authenticationCode !== 235) {
      throw new Error(
        `The SMTP server rejected authentication with response code ${authenticationCode || "unknown"}.`,
      );
    }

    console.log("[TGMS] SMTP authentication check passed.");
    socket.write("QUIT\r\n");
  } finally {
    socket.destroy();
  }
}

try {
  if (!fs.existsSync(environmentPath)) {
    throw new Error(`Environment file not found: ${environmentPath}`);
  }
  await checkSmtp(parseEnv(fs.readFileSync(environmentPath, "utf8")));
} catch (error) {
  fail(error instanceof Error ? error.message : "Unknown SMTP error.");
}
