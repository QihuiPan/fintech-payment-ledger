export type Credentials = {
  username: string;
  password: string;
};

export type Balance = {
  accountId: string;
  currency: string;
  postedBalanceMinor: number;
  availableBalanceMinor: number;
  version: number;
};

export type Wallet = {
  walletId: string;
  userId: string;
  email: string;
  status: string;
  balances: Balance[];
};

export type Entry = {
  entryId: string;
  accountId: string;
  accountCode: string;
  currency: string;
  amountMinor: number;
};

export type Transaction = {
  transactionId: string;
  type: string;
  status: string;
  reference: string;
  reversesTransactionId?: string;
  createdAt: string;
  entries: Entry[];
};

export type StatementItem = {
  transactionId: string;
  type: string;
  reference: string;
  currency: string;
  amountMinor: number;
  runningBalanceMinor: number;
  createdAt: string;
};

export type StatementPage = {
  walletId: string;
  currency: string;
  items: StatementItem[];
  nextCursor?: string;
};

export type FxQuote = {
  quoteId: string;
  walletId: string;
  baseCurrency: string;
  quoteCurrency: string;
  baseAmountMinor: number;
  quoteAmountMinor: number;
  feeMinor: number;
  rate: number;
  expiresAt: string;
};

export type Invariants = {
  unbalancedTransactionCount: number;
  negativeUserBalanceCount: number;
  unpublishedOutboxByType: Record<string, number>;
};
