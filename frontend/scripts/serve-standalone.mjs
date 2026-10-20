// Runs the `output: "standalone"` build the same way the Docker image does.
// `next build` does not copy static assets into the standalone folder, so do it here.
import { spawn } from "node:child_process";
import { cpSync, existsSync } from "node:fs";

const standalone = ".next/standalone";

if (!existsSync(`${standalone}/server.js`)) {
  console.error("No standalone build found. Run `npm run build` first.");
  process.exit(1);
}

cpSync(".next/static", `${standalone}/.next/static`, { recursive: true });
if (existsSync("public")) {
  cpSync("public", `${standalone}/public`, { recursive: true });
}

const server = spawn(process.execPath, ["server.js"], {
  cwd: standalone,
  stdio: "inherit",
  env: { ...process.env, HOSTNAME: process.env.HOSTNAME ?? "127.0.0.1" },
});
server.on("exit", (code) => process.exit(code ?? 0));
for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.kill(signal));
}
