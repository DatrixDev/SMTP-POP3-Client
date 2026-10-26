#!/usr/bin/env python3
"""End-to-end SMTP -> POP3 smoke test against the running LOCAL classroom server.

Prerequisites: MySQL and Java server running, a registered LAN user.
Set TEST_MAIL_USER and TEST_MAIL_PASSWORD, e.g. alice and her password.
This test sends a unique message to the same user's @localhost mailbox,
retrieves it via POP3, and asserts its subject/body. It never deletes mail.
"""
import os
import poplib
import smtplib
import sys
import uuid
from email.message import EmailMessage
from email.parser import BytesParser
from email import policy

host = os.getenv("TEST_MAIL_HOST", "127.0.0.1")
smtp_port = int(os.getenv("TEST_SMTP_PORT", "2525"))
pop_port = int(os.getenv("TEST_POP3_PORT", "2110"))
user = os.getenv("TEST_MAIL_USER", "")
password = os.getenv("TEST_MAIL_PASSWORD", "")
if not user or not password:
    sys.exit("Set TEST_MAIL_USER and TEST_MAIL_PASSWORD for an existing local LAN account.")

address = user if "@" in user else user + "@localhost"
token = "smoke-" + uuid.uuid4().hex
message = EmailMessage()
message["From"] = address
message["To"] = address
message["Subject"] = token
message.set_content("SMTP POP3 integration test: " + token)

with smtplib.SMTP(host, smtp_port, timeout=15) as smtp:
    smtp.ehlo()
    smtp.login(address, password)
    smtp.send_message(message)

pop = poplib.POP3(host, pop_port, timeout=15)
try:
    pop.user(address)
    pop.pass_(password)
    count, _ = pop.stat()
    found = False
    for number in range(count, max(0, count - 30), -1):
        _, lines, _ = pop.retr(number)
        mail = BytesParser(policy=policy.default).parsebytes(b"\r\n".join(lines))
        if str(mail["Subject"]) == token:
            body = mail.get_body(preferencelist=("plain",))
            if body is None or token not in body.get_content():
                raise AssertionError("POP3 message body mismatch")
            found = True
            break
    if not found:
        raise AssertionError("Message not found among latest 30 POP3 messages")
    print("PASS: SMTP AUTH -> DATA -> POP3 USER/PASS -> RETR")
finally:
    pop.quit()
