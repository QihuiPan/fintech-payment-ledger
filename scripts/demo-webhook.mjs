import { createHmac, randomUUID } from "node:crypto";

const walletId = process.argv[2];
if (!walletId) {
  console.error("Usage: node scripts/demo-webhook.mjs <wallet-id> [amount-minor] [currency]");
  process.exit(1);
}

const amountMinor = Number(process.argv[3] ?? "5000");
const currency = (process.argv[4] ?? "GBP").toUpperCase();
const secret = process.env.PROVIDER_WEBHOOK_SECRET ?? "local-demo-secret";
const eventId = `demo-event-${randomUUID()}`;
const timestamp = Math.floor(Date.now() / 1000).toString();
const body = JSON.stringify({
  type: "deposit.succeeded",
  walletId,
  currency,
  amountMinor,
  providerReference: `provider-${eventId}`,
});
const signature = createHmac("sha256", secret).update(`${timestamp}.${body}`).digest("hex");

const response = await fetch("http://localhost:8080/api/provider/webhooks", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "X-Provider-Event-Id": eventId,
    "X-Provider-Timestamp": timestamp,
    "X-Provider-Signature": `sha256=${signature}`,
  },
  body,
});

console.log(response.status, await response.text());
