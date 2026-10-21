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

## Optional MySQL 8+

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
