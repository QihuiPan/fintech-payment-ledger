import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  Activity,
  ArrowLeftRight,
  CircleDollarSign,
  Landmark,
  Plus,
  RefreshCw,
  Repeat2,
  Search,
  Send,
  ShieldCheck,
  Undo2,
  WalletCards,
} from "lucide-react";
import { ApiError, apiRequest, idempotencyKey } from "./api";
import type { Credentials, FxQuote, Invariants, StatementPage, Transaction, Wallet } from "./types";

type Notice = { tone: "success" | "error" | "info"; message: string };

const userCredentials: Credentials = { username: "wallet-user", password: "wallet-demo" };
const adminCredentials: Credentials = { username: "ledger-admin", password: "admin-demo" };

function minorValue(value: string): number {
  const amount = Number(value);
  if (!Number.isFinite(amount) || amount <= 0) {
    throw new Error("Enter an amount greater than zero.");
  }
  return Math.round(amount * 100);
}

function formatMoney(amountMinor: number, currency: string): string {
  return new Intl.NumberFormat("en-GB", {
    style: "currency",
    currency,
  }).format(amountMinor / 100);
}

function shortId(value: string): string {
  return `${value.slice(0, 8)}…${value.slice(-4)}`;
}

function App() {
  const [credentials, setCredentials] = useState(userCredentials);
  const [walletId, setWalletId] = useState("");
  const [wallet, setWallet] = useState<Wallet>();
  const [statement, setStatement] = useState<StatementPage>();
  const [statementCurrency, setStatementCurrency] = useState("GBP");
  const [invariants, setInvariants] = useState<Invariants>();
  const [quote, setQuote] = useState<FxQuote>();
  const [lastTransaction, setLastTransaction] = useState<Transaction>();
  const [notice, setNotice] = useState<Notice>({ tone: "info", message: "Ready for a ledger operation." });
  const [busy, setBusy] = useState(false);
  const [apiOnline, setApiOnline] = useState<boolean>();

  const [newEmail, setNewEmail] = useState("alex@example.com");
  const [newCurrencies, setNewCurrencies] = useState("GBP, EUR, USD");
  const [depositCurrency, setDepositCurrency] = useState("GBP");
  const [depositAmount, setDepositAmount] = useState("100.00");
  const [providerReference, setProviderReference] = useState("demo-settlement-001");
  const [recipientWalletId, setRecipientWalletId] = useState("");
  const [transferCurrency, setTransferCurrency] = useState("GBP");
  const [transferAmount, setTransferAmount] = useState("10.00");
  const [fxBase, setFxBase] = useState("GBP");
  const [fxQuoteCurrency, setFxQuoteCurrency] = useState("EUR");
  const [fxAmount, setFxAmount] = useState("25.00");
  const [reversalTransactionId, setReversalTransactionId] = useState("");
  const [reversalReason, setReversalReason] = useState("Customer-requested correction");

  const outboxTotal = useMemo(
    () => Object.values(invariants?.unpublishedOutboxByType ?? {}).reduce((sum, count) => sum + count, 0),
    [invariants],
  );

  useEffect(() => {
    fetch("/actuator/health")
      .then((response) => setApiOnline(response.ok))
      .catch(() => setApiOnline(false));
  }, []);

  async function execute<T>(label: string, work: () => Promise<T>): Promise<T | undefined> {
    setBusy(true);
    setNotice({ tone: "info", message: `${label} in progress…` });
    try {
      const result = await work();
      setNotice({ tone: "success", message: `${label} completed.` });
      return result;
    } catch (error) {
      const message = error instanceof ApiError && error.code ? `${error.code}: ${error.message}` : error instanceof Error ? error.message : "Unexpected request failure";
      setNotice({ tone: "error", message });
      return undefined;
    } finally {
      setBusy(false);
    }
  }

  async function loadWallet(targetWalletId = walletId, currency = statementCurrency) {
    if (!targetWalletId.trim()) {
      setNotice({ tone: "error", message: "Enter a wallet ID first." });
      return;
    }
    await execute("Wallet refresh", async () => {
      const [loadedWallet, loadedStatement] = await Promise.all([
        apiRequest<Wallet>(`/api/wallets/${targetWalletId}/balances`, credentials),
        apiRequest<StatementPage>(`/api/wallets/${targetWalletId}/statement?currency=${encodeURIComponent(currency)}&limit=50`, credentials),
      ]);
      setWallet(loadedWallet);
      setStatement(loadedStatement);
      setWalletId(loadedWallet.walletId);
      return loadedWallet;
    });
  }

  async function refreshInvariants() {
    await execute("Invariant check", async () => {
      const result = await apiRequest<Invariants>("/api/admin/invariants", adminCredentials);
      setInvariants(result);
      return result;
    });
  }

  async function handleCreateWallet(event: FormEvent) {
    event.preventDefault();
    const result = await execute("Wallet creation", () =>
      apiRequest<Wallet>("/api/wallets", credentials, {
        method: "POST",
        body: {
          email: newEmail,
          currencies: newCurrencies.split(",").map((currency) => currency.trim().toUpperCase()).filter(Boolean),
        },
      }),
    );
    if (result) {
      setWallet(result);
      setWalletId(result.walletId);
      setStatement(undefined);
    }
  }

  async function handleDeposit(event: FormEvent) {
    event.preventDefault();
    const result = await execute("Deposit", () =>
      apiRequest<Transaction>("/api/deposits", credentials, {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey("deposit") },
        body: { walletId, currency: depositCurrency, amountMinor: minorValue(depositAmount), providerReference },
      }),
    );
    if (result) {
      setLastTransaction(result);
      setReversalTransactionId(result.transactionId);
      await loadWallet(walletId, depositCurrency);
    }
  }

  async function handleTransfer(event: FormEvent) {
    event.preventDefault();
    const result = await execute("Transfer", () =>
      apiRequest<Transaction>("/api/transfers", credentials, {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey("transfer") },
        body: {
          senderWalletId: walletId,
          recipientWalletId,
          currency: transferCurrency,
          amountMinor: minorValue(transferAmount),
        },
      }),
    );
    if (result) {
      setLastTransaction(result);
      setReversalTransactionId(result.transactionId);
      await loadWallet(walletId, transferCurrency);
    }
  }

  async function handleQuote(event: FormEvent) {
    event.preventDefault();
    const result = await execute("FX quote", () =>
      apiRequest<FxQuote>("/api/fx/quotes", credentials, {
        method: "POST",
        body: {
          walletId,
          baseCurrency: fxBase,
          quoteCurrency: fxQuoteCurrency,
          baseAmountMinor: minorValue(fxAmount),
        },
      }),
    );
    if (result) setQuote(result);
  }

  async function handleConvert() {
    if (!quote) return;
    const result = await execute("FX conversion", () =>
      apiRequest<Transaction>("/api/conversions", credentials, {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey("conversion") },
        body: { quoteId: quote.quoteId },
      }),
    );
    if (result) {
      setLastTransaction(result);
      setReversalTransactionId(result.transactionId);
      setQuote(undefined);
      await loadWallet(walletId, fxBase);
    }
  }

  async function handleReversal(event: FormEvent) {
    event.preventDefault();
    const result = await execute("Reversal", () =>
      apiRequest<Transaction>(`/api/transactions/${reversalTransactionId}/reversals`, credentials, {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey("reversal") },
        body: { reason: reversalReason },
      }),
    );
    if (result) {
      setLastTransaction(result);
      await loadWallet();
    }
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#overview" aria-label="Ledger Console home">
          <span className="brand-mark">LC</span>
          <span><strong>Ledger</strong><small>Operations console</small></span>
        </a>
        <nav aria-label="Primary navigation">
          <a href="#overview"><WalletCards size={18} /> Overview</a>
          <a href="#payments"><ArrowLeftRight size={18} /> Payments</a>
          <a href="#foreign-exchange"><Repeat2 size={18} /> Foreign exchange</a>
          <a href="#controls"><ShieldCheck size={18} /> Controls</a>
        </nav>
        <div className="sidebar-note">
          <ShieldCheck size={20} />
          <div><strong>Append-only</strong><span>Corrections use compensating entries.</span></div>
        </div>
      </aside>

      <main>
        <header className="topbar">
          <div>
            <p className="eyebrow">Payment infrastructure portfolio</p>
            <h1>Ledger operations</h1>
          </div>
          <div className={`health ${apiOnline === true ? "online" : apiOnline === false ? "offline" : "checking"}`}>
            <span /> {apiOnline === true ? "API online" : apiOnline === false ? "API unavailable" : "Checking API"}
          </div>
        </header>

        <section className="notice-row" aria-live="polite">
          <div className={`notice ${notice.tone}`}>{notice.message}</div>
          <label className="compact-field">
            <span>API user</span>
            <input value={credentials.username} onChange={(event) => setCredentials({ ...credentials, username: event.target.value })} />
          </label>
          <label className="compact-field">
            <span>Password</span>
            <input type="password" value={credentials.password} onChange={(event) => setCredentials({ ...credentials, password: event.target.value })} />
          </label>
        </section>

        <section id="overview" className="workspace-section">
          <div className="section-heading">
            <div><p className="eyebrow">Account position</p><h2>Wallet overview</h2></div>
            <button className="secondary" type="button" onClick={() => loadWallet()} disabled={busy}><RefreshCw size={16} /> Refresh</button>
          </div>

          <div className="overview-grid">
            <div className="panel wallet-loader">
              <div className="panel-title"><Search size={18} /><h3>Open wallet</h3></div>
              <label><span>Wallet ID</span><input value={walletId} onChange={(event) => setWalletId(event.target.value)} placeholder="Paste a wallet UUID" /></label>
              <button type="button" onClick={() => loadWallet()} disabled={busy}>Load balances</button>
              <div className="divider"><span>or create a demo wallet</span></div>
              <form onSubmit={handleCreateWallet}>
                <label><span>Email</span><input type="email" required value={newEmail} onChange={(event) => setNewEmail(event.target.value)} /></label>
                <label><span>Currencies</span><input required value={newCurrencies} onChange={(event) => setNewCurrencies(event.target.value)} /></label>
                <button className="secondary" disabled={busy}><Plus size={16} /> Create wallet</button>
              </form>
            </div>

            <div className="balance-area">
              <div className="wallet-meta">
                <div><span>Wallet owner</span><strong>{wallet?.email ?? "No wallet selected"}</strong></div>
                <div><span>Status</span><strong>{wallet?.status ?? "—"}</strong></div>
                <div><span>Wallet ID</span><code>{wallet ? shortId(wallet.walletId) : "—"}</code></div>
              </div>
              <div className="balance-grid">
                {wallet?.balances.length ? wallet.balances.map((balance) => (
                  <article className="balance-card" key={balance.accountId}>
                    <div><span className="currency-badge">{balance.currency}</span><span className="version">v{balance.version}</span></div>
                    <p>{formatMoney(balance.availableBalanceMinor, balance.currency)}</p>
                    <span>Available balance</span>
                  </article>
                )) : (
                  <div className="empty-state"><WalletCards size={28} /><p>Load or create a wallet to see its balances.</p></div>
                )}
              </div>
            </div>
          </div>
        </section>

        <section id="payments" className="workspace-section">
          <div className="section-heading"><div><p className="eyebrow">Money movement</p><h2>Payment actions</h2></div></div>
          <div className="action-grid">
            <form className="panel action-panel" onSubmit={handleDeposit}>
              <div className="panel-title"><CircleDollarSign size={18} /><h3>Deposit</h3></div>
              <div className="field-row"><label><span>Currency</span><input maxLength={3} value={depositCurrency} onChange={(event) => setDepositCurrency(event.target.value.toUpperCase())} /></label><label><span>Amount</span><input inputMode="decimal" value={depositAmount} onChange={(event) => setDepositAmount(event.target.value)} /></label></div>
              <label><span>Provider reference</span><input value={providerReference} onChange={(event) => setProviderReference(event.target.value)} /></label>
              <button disabled={busy || !walletId}><Landmark size={16} /> Post deposit</button>
            </form>

            <form className="panel action-panel" onSubmit={handleTransfer}>
              <div className="panel-title"><Send size={18} /><h3>Transfer</h3></div>
              <label><span>Recipient wallet ID</span><input required value={recipientWalletId} onChange={(event) => setRecipientWalletId(event.target.value)} /></label>
              <div className="field-row"><label><span>Currency</span><input maxLength={3} value={transferCurrency} onChange={(event) => setTransferCurrency(event.target.value.toUpperCase())} /></label><label><span>Amount</span><input inputMode="decimal" value={transferAmount} onChange={(event) => setTransferAmount(event.target.value)} /></label></div>
              <button disabled={busy || !walletId}><ArrowLeftRight size={16} /> Send transfer</button>
            </form>

            <form className="panel action-panel" onSubmit={handleReversal}>
              <div className="panel-title"><Undo2 size={18} /><h3>Reverse transaction</h3></div>
              <label><span>Transaction ID</span><input required value={reversalTransactionId} onChange={(event) => setReversalTransactionId(event.target.value)} /></label>
              <label><span>Audit reason</span><input required value={reversalReason} onChange={(event) => setReversalReason(event.target.value)} /></label>
              <button className="danger" disabled={busy}><Undo2 size={16} /> Post reversal</button>
            </form>
          </div>
        </section>

        <section id="foreign-exchange" className="workspace-section">
          <div className="section-heading"><div><p className="eyebrow">Quoted conversion</p><h2>Foreign exchange</h2></div></div>
          <div className="fx-layout">
            <form className="panel fx-form" onSubmit={handleQuote}>
              <div className="panel-title"><Repeat2 size={18} /><h3>Request quote</h3></div>
              <div className="field-row"><label><span>Sell</span><input maxLength={3} value={fxBase} onChange={(event) => setFxBase(event.target.value.toUpperCase())} /></label><label><span>Buy</span><input maxLength={3} value={fxQuoteCurrency} onChange={(event) => setFxQuoteCurrency(event.target.value.toUpperCase())} /></label></div>
              <label><span>Sell amount</span><input inputMode="decimal" value={fxAmount} onChange={(event) => setFxAmount(event.target.value)} /></label>
              <button disabled={busy || !walletId}>Get executable quote</button>
            </form>
            <div className="quote-board">
              {quote ? <>
                <p className="eyebrow">Quote expires {new Date(quote.expiresAt).toLocaleTimeString()}</p>
                <div className="quote-pair"><span>{formatMoney(quote.baseAmountMinor, quote.baseCurrency)}</span><ArrowLeftRight size={22} /><strong>{formatMoney(quote.quoteAmountMinor, quote.quoteCurrency)}</strong></div>
                <dl><div><dt>Rate</dt><dd>{quote.rate}</dd></div><div><dt>Fee</dt><dd>{formatMoney(quote.feeMinor, quote.baseCurrency)}</dd></div><div><dt>Quote ID</dt><dd><code>{shortId(quote.quoteId)}</code></dd></div></dl>
                <button type="button" onClick={handleConvert} disabled={busy}>Execute conversion</button>
              </> : <div className="empty-state"><Repeat2 size={28} /><p>Request a quote to preview the rate, fee, and settlement amount.</p></div>}
            </div>
          </div>
        </section>

        <section id="controls" className="workspace-section">
          <div className="section-heading">
            <div><p className="eyebrow">Safety and observability</p><h2>Ledger controls</h2></div>
            <button className="secondary" type="button" onClick={refreshInvariants} disabled={busy}><Activity size={16} /> Run invariant check</button>
          </div>
          <div className="control-strip">
            <div><span>Unbalanced transactions</span><strong className={invariants?.unbalancedTransactionCount ? "bad" : "good"}>{invariants?.unbalancedTransactionCount ?? "—"}</strong></div>
            <div><span>Negative user balances</span><strong className={invariants?.negativeUserBalanceCount ? "bad" : "good"}>{invariants?.negativeUserBalanceCount ?? "—"}</strong></div>
            <div><span>Pending outbox events</span><strong>{invariants ? outboxTotal : "—"}</strong></div>
            <div><span>Last transaction</span><code title={lastTransaction?.transactionId}>{lastTransaction ? shortId(lastTransaction.transactionId) : "—"}</code></div>
          </div>

          <div className="statement-panel">
            <div className="statement-toolbar">
              <div><p className="eyebrow">Running balance</p><h3>Wallet statement</h3></div>
              <label><span>Currency</span><input maxLength={3} value={statementCurrency} onChange={(event) => setStatementCurrency(event.target.value.toUpperCase())} /></label>
              <button className="secondary" type="button" onClick={() => loadWallet(walletId, statementCurrency)} disabled={busy || !walletId}>Load statement</button>
            </div>
            <div className="table-wrap">
              <table>
                <thead><tr><th>Time</th><th>Type</th><th>Reference</th><th className="numeric">Amount</th><th className="numeric">Running balance</th></tr></thead>
                <tbody>
                  {statement?.items.length ? statement.items.map((item) => (
                    <tr key={item.transactionId}>
                      <td>{new Date(item.createdAt).toLocaleString()}</td><td>{item.type.replaceAll("_", " ")}</td><td><code>{item.reference}</code></td><td className={`numeric ${item.amountMinor < 0 ? "negative" : "positive"}`}>{formatMoney(item.amountMinor, item.currency)}</td><td className="numeric">{formatMoney(item.runningBalanceMinor, item.currency)}</td>
                    </tr>
                  )) : <tr><td colSpan={5} className="table-empty">No statement entries loaded.</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </section>

        <footer>Demo credentials are local defaults only. Change every secret before deployment.</footer>
      </main>
    </div>
  );
}

export default App;
