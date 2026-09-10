"""Create the source-linked, four-page payment-ledger portfolio."""

from pathlib import Path
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.platypus import Paragraph


ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "QihuiPan_FinTech_Ledger_Portfolio.pdf"
REPO = "https://github.com/QihuiPan/fintech-payment-ledger"
RELEASE = REPO + "/releases/tag/v0.3.0"
CI = REPO + "/actions/runs/34045594508"
CONTAINER = REPO + "/actions/runs/34046058660"
BASE = REPO + "/blob/v0.3.0/"
LATEST = REPO + "/blob/c0edf44821ad6e35e7b0b93a3e53e2ddd566426b/"
W, H = A4
M = 46
CW = W - 2 * M
INK = colors.HexColor("#111827")
BLUE = colors.HexColor("#102cfa")
MUTED = colors.HexColor("#536070")
LINE = colors.HexColor("#d9dee6")
PALE = colors.HexColor("#f1f4fa")
NAVY = colors.HexColor("#111f35")
WHITE = colors.white

pdfmetrics.registerFont(TTFont("Portfolio", "C:/Windows/Fonts/segoeui.ttf"))
pdfmetrics.registerFont(TTFont("PortfolioBold", "C:/Windows/Fonts/segoeuib.ttf"))
pdfmetrics.registerFont(TTFont("PortfolioMono", "C:/Windows/Fonts/consola.ttf"))
pdfmetrics.registerFontFamily("Portfolio", normal="Portfolio", bold="PortfolioBold")

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
c = canvas.Canvas(str(OUTPUT), pagesize=A4, pageCompression=1)
c.setTitle("Qihui Pan | FinTech Payment Ledger - Engineering Portfolio")
c.setAuthor("Qihui Pan")
c.setSubject("Double-entry payment accounting, reliability, architecture, and verification")
c.setCreator("Portfolio document generator")


def rect(x, top, width, height, fill, stroke=None):
    c.setFillColor(fill)
    c.setStrokeColor(stroke or fill)
    c.rect(x, H - top - height, width, height, fill=1, stroke=bool(stroke))


def rule(top, x=M, width=CW, color=LINE):
    c.setStrokeColor(color)
    c.setLineWidth(0.6)
    c.line(x, H - top, x + width, H - top)


def line(text, x, top, size=11, font="Portfolio", color=INK):
    c.setFont(font, size)
    c.setFillColor(color)
    c.drawString(x, H - top - size, text)


def right(text, x, top, size=11, font="Portfolio", color=INK):
    c.setFont(font, size)
    c.setFillColor(color)
    c.drawRightString(x, H - top - size, text)


def para(text, x, top, width, size=10.5, leading=16, color=INK, bold=False):
    style = ParagraphStyle(
        "p", fontName="PortfolioBold" if bold else "Portfolio",
        fontSize=size, leading=leading, textColor=color, alignment=TA_LEFT,
        spaceAfter=0, allowWidows=0, allowOrphans=0,
    )
    p = Paragraph(text, style)
    _, height = p.wrap(width, H)
    if top + height > H - 52:
        raise ValueError(f"Text would overlap footer: {text[:70]}")
    p.drawOn(c, x, H - top - height)
    return top + height


def link(label, url, x, top, size=9):
    return para(f'<link href="{escape(url)}" color="#102cfa"><u>{escape(label)}</u></link>',
                x, top, CW, size=size, leading=13)


def eyebrow(text, top):
    line(text.upper(), M, top, 9, "PortfolioMono", BLUE)


def header(page, label):
    line("QIHUI PAN", M, 25, 9, "PortfolioBold")
    right("ENGINEERING PORTFOLIO / " + label.upper(), W-M, 25, 8, "PortfolioMono", MUTED)
    rule(47)
    rule(H-42)
    line("FinTech Payment Ledger", M, H-31, 8, color=MUTED)
    right(f"SEPTEMBER 2026     {page:02d} / 04", W-M, H-31, 8, "PortfolioMono", MUTED)


def title(text, top=88, size=31):
    return para(text, M, top, CW, size=size, leading=size*1.12, bold=True)


# Page 1: Project story and an exact, illustrative posting.
header(1, "Project overview")
eyebrow("Selected project / FinTech infrastructure", 69)
title("Every movement.<br/>Accounted for.", 92, 42)
line("FinTech Payment Ledger", M, 202, 16, "PortfolioBold")
para("A self-hosted wallet and payment system that makes money movement "
     "explainable, repeatable, and auditable.", M, 234, CW, size=14, leading=21)
para("An independent engineering project covering the accounting core, REST API, "
     "React operations console, provider-event simulation, and repeatable deployment.",
     M, 289, CW, color=MUTED)

rule(342)
metrics = [("12", "Release tests passed"), ("4", "CI jobs passed"), ("2", "Image architectures"), ("1", "Startup command")]
for i, (value, label) in enumerate(metrics):
    x = M + i * CW / 4
    line(value, x, 353, 29, "PortfolioBold")
    line(label, x, 391, 8.5, color=MUTED)
line("Verified v0.3.0 release baseline; source links on page 3.", M, 415, 8.5, color=MUTED)

rect(M, 449, CW, 178, NAVY)
line("TRANSFER / GBP 25.00", M+20, 466, 11, "PortfolioBold", WHITE)
right("ILLUSTRATIVE POSTING", W-M-20, 469, 8, "PortfolioMono", colors.HexColor("#b9c7de"))
rule(497, M+20, CW-40, colors.HexColor("#41506a"))
line("Sender wallet", M+20, 512, 11, color=WHITE)
right("-2,500 GBP", W-M-20, 509, 15, "PortfolioMono", WHITE)
line("Recipient wallet", M+20, 545, 11, color=WHITE)
right("+2,500 GBP", W-M-20, 542, 15, "PortfolioMono", WHITE)
rule(575, M+20, CW-40, colors.HexColor("#41506a"))
line("Sum per currency", M+20, 591, 10, color=WHITE)
right("0", W-M-20, 582, 25, "PortfolioMono", colors.HexColor("#a9bbff"))
line("Amounts shown in signed integer minor units. No money is created by this transfer.", M, 637, 8.5, color=MUTED)

eyebrow("Technology", 678)
para("Java 21 / Spring Boot / PostgreSQL / Spring JDBC / Flyway<br/>"
     "React / TypeScript / Docker Compose / GitHub Actions", M, 701, CW, size=10, leading=16)
link("Public repository: github.com/QihuiPan/fintech-payment-ledger", REPO, M, 752)
c.showPage()

# Page 2: Architecture and the design choices it supports.
header(2, "Architecture")
eyebrow("01 / Design", 69)
title("Correctness at the<br/>posting boundary.", 93)
para("Repeated requests, concurrent spending, and reversals share one set of "
     "accounting rules. Each posting is enclosed in a database transaction.", M, 179, CW, color=MUTED)

node_w = (CW-30)/3
nodes = [
    ("INPUTS", "Console + provider", "React wallet operations<br/>Signed webhook events"),
    ("APPLICATION", "API + services", "Ownership and validation<br/>Idempotency and row locks"),
    ("DURABLE STATE", "PostgreSQL", "Entries and balances<br/>Inbox, outbox and audit"),
]
for i, (label, name, detail) in enumerate(nodes):
    x=M+i*(node_w+15)
    rect(x, 234, node_w, 126, PALE)
    line(label, x+12, 247, 8, "PortfolioMono", BLUE)
    para(name, x+12, 273, node_w-24, size=12, leading=16, bold=True)
    para(detail, x+12, 305, node_w-24, size=9, leading=14, color=MUTED)
    if i<2:
        line(">", x+node_w+3, 280, 13, "PortfolioMono", BLUE)
rect(M, 374, CW, 49, BLUE)
para("<b>One atomic commit:</b> ledger entries + balance projections + outbox event",
     M+15, 389, CW-30, size=10, leading=15, color=WHITE)

decisions = [
    ("01", "Use integers for money", "Signed minor units avoid binary floating-point amounts. "
     "FX rates use fixed precision; every currency leg balances independently."),
    ("02", "Check funds after acquiring account locks", "Stable account ordering prevents lock inversion. "
     "The available-balance check runs inside the posting transaction."),
    ("03", "Make retries and corrections explicit", "A stored request fingerprint identifies a valid replay. "
     "Reversals append opposite entries and preserve the original record."),
]
top=452
for number, heading, body in decisions:
    rule(top)
    line(number, M, top+13, 10, "PortfolioMono", BLUE)
    para(heading, M+35, top+11, CW-35, size=11, leading=16, bold=True)
    para(body, M+35, top+34, CW-35, size=10, leading=15, color=MUTED)
    top+=89
link("Architecture and deposit sequence", BASE+"docs/architecture.md", M, 738)
link("PostgreSQL append-only and deferred balancing guards", BASE+"src/main/resources/db/migration/postgresql/V2__ledger_database_guards.sql", M, 758)
c.showPage()

# Page 3: Verification with historical evidence scoped to its release.
header(3, "Verification")
eyebrow("02 / Engineering evidence", 69)
title("Evidence you can inspect.", 93, 30)
para("The v0.3.0 baseline passed 12 automated tests and all four CI jobs. "
     "The following cases are linked to the exact released source.", M, 151, CW, color=MUTED)
rows = [
    ("Retry semantics", "The same deposit and key return the original transaction ID. "
     "Changing the amount while reusing the key is rejected.", "Payment flow integration test",
     BASE+"src/test/java/com/portfolio/ledger/PaymentFlowIntegrationTest.java"),
    ("Concurrent spending", "Two GBP 8.00 transfers compete for a GBP 10.00 balance. "
     "Exactly one succeeds and GBP 2.00 remains.", "Concurrent spend integration test",
     BASE+"src/test/java/com/portfolio/ledger/ConcurrentSpendIntegrationTest.java"),
    ("Accounting rules", "1,000 randomized balanced examples are accepted. An "
     "unbalanced currency leg is rejected; FX balances separately in each currency.", "Ledger math tests",
     BASE+"src/test/java/com/portfolio/ledger/domain/LedgerMathTest.java"),
    ("HTTP access controls", "Ownership checks and administrator overrides are covered, "
     "along with metrics permissions and a disabled default H2 console.", "HTTP security integration test",
     BASE+"src/test/java/com/portfolio/ledger/ApiSecurityIntegrationTest.java"),
]
top=211
for heading, body, label, url in rows:
    rule(top)
    para(heading, M, top+13, 118, size=11, leading=16, bold=True)
    para(body, M+141, top+11, CW-141, size=10, leading=15, color=MUTED)
    link(label, url, M+141, top+64, size=8.5)
    top+=97

rect(M, 616, CW, 99, PALE)
line("BUILD AND DELIVERY", M+16, 628, 8, "PortfolioMono", BLUE)
para("Backend tests, frontend build, PostgreSQL migration/readiness, and image "
     "build passed. The release workflow published AMD64 and ARM64 images.",
     M+16, 650, CW-32, size=10, leading=15)
link("CI run", CI, M+16, 690)
link("Container release run", CONTAINER, M+103, 690)
link("v0.3.0 release", RELEASE, M+260, 690)
para("Evidence scope: core integration tests use H2; the PostgreSQL job checks startup and migrations. "
     "These results are not throughput, availability, or certification claims. "
     "Baseline commit: 7e5b2f9. Portfolio reviewed: 11 September 2026.",
     M, 736, CW, size=8.5, leading=12, color=MUTED)
c.showPage()

# Page 4: Hands-on evaluation, current expansion, and explicit boundaries.
header(4, "Evaluation")
eyebrow("03 / Use and extend", 69)
title("A project you can run.", 93, 32)
para("Docker Compose packages the English console, API, PostgreSQL, migrations, "
     "health checks, and persistent storage into a local evaluation environment.", M, 153, CW, color=MUTED)
rect(M, 211, CW, 112, NAVY)
line("QUICK START / DOCKER + COMPOSE V2", M+16, 225, 8, "PortfolioMono", colors.HexColor("#b9c7de"))
commands = ["git clone https://github.com/QihuiPan/fintech-payment-ledger.git", "cd fintech-payment-ledger", "docker compose up --build --wait"]
for i, command in enumerate(commands):
    line(command, M+16, 249+i*20, 8.5, "PortfolioMono", WHITE)

line("A short evaluation path", M, 345, 13, "PortfolioBold")
demo = [
    "Open localhost:8080 and select Create demo workspace.",
    "Inspect the seeded GBP 100.00 deposit and its balanced entries.",
    "Transfer funds, execute an FX quote, and reverse a transaction.",
    "Review the running statement and the administrator invariant check.",
]
for i, item in enumerate(demo):
    line(f"0{i+1}", M, 378+i*26, 9, "PortfolioMono", BLUE)
    para(item, M+28, 375+i*26, CW-28, size=10, leading=15)

rule(492)
line("Latest extension: trace-linked telemetry", M, 510, 13, "PortfolioBold")
para("After v0.3.0, the main branch added optional OpenTelemetry export, HTTP "
     "and worker signals, correlated request logs, and Prometheus exemplars. "
     "These additions connect operational symptoms with request traces.",
     M, 539, CW, size=10, leading=15, color=MUTED)
link("Inspect the observability integration at c0edf44", LATEST+"docs/observability-platform.md", M, 592)

rule(622)
line("Next engineering steps", M, 638, 13, "PortfolioBold")
para("The current application is a runnable reference with a simulated provider and "
     "a configurable in-memory identity directory. Further work includes external identity, "
     "a durable outbox relay, coordinated multi-worker processing, and measured load and "
     "recovery testing before real-money service operation.", M, 665, CW, size=10, leading=15, color=MUTED)
link("Deployment guide", REPO+"/blob/main/docs/deployment.md", M, 743)
link("Threat model and scope boundaries", REPO+"/blob/main/docs/threat-model.md", M+155, 743)
c.save()
print(f"Created {OUTPUT}")
