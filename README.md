# SMTP–POP3 Client

Java 17 + Maven multi-module desktop mail client. Based on *Advanced Network Programming – Principles and Techniques*, Chapter 7.3: SMTP with JavaMail (Listing 7.3) and POP3 with JavaMail (Listing 7.4). The book's examples have been adapted for modern authenticated TLS connections.

## Modules

- `common/`: account and email data models.
- `mail-service/`: SMTP sending, POP3 inbox retrieval, optional MySQL local archive.
- `client/`: Java Swing + FlatLaf desktop UI.
- `database/schema.sql`: MySQL 8+ database schema.

No custom SMTP or POP3 server is required. Gmail or another compatible provider hosts the actual mailboxes.

## Requirements

JDK 17, Maven 3.9+, network access, a provider account that supports SMTP AUTH and POP3. For Gmail, app passwords require account eligibility and POP access must be supported/enabled. **Do not use your normal account password or commit secrets.**

## Run on Windows PowerShell

```powershell
git clone https://github.com/DatrixDev/SMTP-POP3-Client.git
cd SMTP-POP3-Client
mvn clean install
mvn -pl client -am exec:java -Dexec.mainClass=vn.edu.mailclient.client.Main
```

If Maven tries to run exec on all modules, run instead:

```powershell
mvn clean install
cd client
mvn exec:java
```

In the UI enter your email, app password, SMTP and POP3 host/port. Defaults are Gmail SMTP 587 STARTTLS and POP3 995 SSL. Sending and receiving use live provider servers; no simulation.

## MySQL 8.4 with Docker (recommended)

From the repository root, copy the environment template, then edit the two passwords in `.env`:

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

The MySQL container is published on `127.0.0.1:3307` (configurable using `MYSQL_PORT` in `.env`). The database schema in `database/schema.sql` is imported automatically **only when the database volume is initialized for the first time**. To apply later schema changes, run SQL migrations; don't delete the volume unless you intend to lose data.

For the Java application launched on the Windows host, set environment variables in the same PowerShell session:

```powershell
$env:MAIL_DB_URL="jdbc:mysql://127.0.0.1:3307/smtp_pop3_client?useUnicode=true&characterEncoding=UTF-8"
$env:MAIL_DB_USER="mailapp"
$env:MAIL_DB_PASSWORD="YOUR_MYSQL_PASSWORD_FROM_DOT_ENV"
cd client
mvn exec:java
```

If you change `MYSQL_PORT`, update the JDBC URL accordingly. Do not put mail credentials in `.env`; the MySQL password is separate from the email app password.

To stop the container without deleting data: `docker compose down`. To inspect logs: `docker compose logs -f mysql`.

## Optional MySQL 8+ (without Docker)

Run `database/schema.sql` in MySQL Workbench, then configure environment variables before starting the app:

```powershell
$env:MAIL_DB_URL="jdbc:mysql://localhost:3306/smtp_pop3_client?useUnicode=true&characterEncoding=UTF-8"
$env:MAIL_DB_USER="root"
$env:MAIL_DB_PASSWORD="YOUR_LOCAL_MYSQL_PASSWORD"
```

When `MAIL_DB_URL` is absent, the app still sends and receives mail, without a local archive. MySQL failures are shown separately from SMTP/POP3 results. Mail credentials are held in memory for the session, never saved to MySQL. Avoid using real sensitive mailboxes for demonstrations because the optional archive stores email bodies.

## Scope and limitations

- SMTP: send plain-text UTF-8 messages to one or more recipients.
- POP3: download inbox list and read message text in a split view. Multipart plain-text messages are supported; HTML is shown as source text.
- MySQL: optional local mail history. Current imports append rows on each refresh (deduplication is not yet implemented).
- No attachment upload/download, OAuth2 login, offline archive browsing, or automatic sync.
- POP3 may be unavailable for certain providers/accounts. If a provider disallows password-based login, OAuth2 would need to be implemented separately.

Never commit app passwords, tokens, or database credentials.


## LAN UDP mail (experimental)

The project now includes a `server` Maven module and a JavaFX **Mail nội bộ LAN** page. SMTP and POP3 remain independent; IMAP is optional.

1. Copy `.env.example` to `.env`, fill in Docker MySQL credentials and Gmail App Password. Ensure `MAIL_DB_URL` uses the same published port as `MYSQL_PORT`. Do not commit `.env`.
2. Run `docker compose up -d` from the repository root.
3. Run `mvn clean install` from the repository root.
4. In terminal A, run `cd server` then `mvn exec:java`. The server initializes `lan_users` and `lan_messages` in MySQL.
5. In terminal B, run `cd client` then `mvn exec:java`.
6. In JavaFX select **Mail nội bộ LAN**; register two different usernames, log in and exchange messages. Use another client instance to test the recipient.
7. For two computers on a trusted private LAN, set `LAN_BIND=0.0.0.0` in the server's `.env`, allow UDP port 2023 in the firewall, and set the client Server IP to the server computer's private IPv4 address.

**Important security limitation:** The UDP LAN mode is a course demonstration, not an Internet mail server. Its traffic, including login passwords, is **not encrypted**. Do not reuse real passwords, expose UDP port 2023 to the public Internet, or deploy on an untrusted network. Gmail App Password is used only by SMTP/POP3/IMAP clients and is not transmitted to the LAN server.

The `ImapService.sinceUid(...)` API supports incremental reads by UID, but persistent cursor storage, scheduled polling, HTML WebView rendering and attachments are not yet wired into the desktop UI. Manual IMAP refresh remains available.


## Local SMTP + POP3 Server with Desktop Dashboard (course demo)

The `server` module now also runs actual loopback SMTP and POP3 listeners, **in addition to** the older experimental UDP LAN service:

- SMTP: `127.0.0.1:2525` (authenticated SMTP AUTH LOGIN; local `@localhost` addresses only).
- POP3: `127.0.0.1:2110` (USER/PASS, STAT, LIST, UIDL, RETR, TOP, DELE, RSET).
- UDP LAN: `127.0.0.1:2023` (legacy feature).
- MySQL: `lan_users` for accounts, `local_mail` for SMTP messages.
- Server Dashboard: Swing desktop window with accounts, mail list, message viewer and logs.

From project root, start MySQL (`docker compose up -d`) and build (`mvn clean package -DskipTests`). Then run `cd server; mvn exec:java`. Keep it running. Start a separate terminal in `client` and run `mvn exec:java`.

In the **Server Dashboard**, create `alice` and `bob` with passwords. In Client settings select **Dùng localhost**, enter `alice@localhost` and Alice's password, then send to `bob@localhost`. Open a second Client with Bob's credentials and select **POP3 (bài lab)** to refresh inbox. The server Dashboard shows stored messages in MySQL.

For Gmail, use **Dùng Gmail** and your Gmail address and eligible App Password. Gmail mail is processed directly by Google's servers; it is not stored in the local `local_mail` table.

**Security and scope:** SMTP/POP3 local listeners bind to loopback only and do not use TLS; do not expose them outside your own machine. This is a limited educational protocol implementation, not a production mail server. It does not relay Internet email, offer STARTTLS, support all SMTP/POP3 extensions or MIME attachments management. Run with a desktop session for the Swing dashboard. Gmail and local modes are independent.

