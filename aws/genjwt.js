#!/usr/bin/env node
// Genera un JWT HS256 de prueba firmado con la misma JWT_SECRET que usa el
// backend (users/bff/authorizer). Uso:
//   node genjwt.js <JWT_SECRET> <ROLE> <SUB>
// Ejemplo:
//   node genjwt.js "b9dbd25c..." "CLIENTE" "cliente@NicolazAura.onmicrosoft.com"
const crypto = require("crypto");

const secret = process.argv[2];
const role = process.argv[3];
const sub = process.argv[4];
if (!secret || !role || !sub) {
  console.error("uso: node genjwt.js <JWT_SECRET> <ROLE> <SUB>");
  process.exit(1);
}

const now = Math.floor(Date.now() / 1000);
const enc = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
const header = enc({ alg: "HS256" });
const payload = enc({
  sub,
  role,
  iss: "pedidos360-users",
  aud: "pedidos360-bff",
  iat: now,
  exp: now + 86400,
});
const sig = crypto.createHmac("sha256", secret).update(header + "." + payload).digest("base64url");
console.log(header + "." + payload + "." + sig);