# Sanitized Source Snapshot

Source provenance:
- Server files: provided VPS source export after V17; no .env, DB dumps, private spool or logs.
- Raspberry Pi print-agent source: the previously tested V15.1 polarity package.
- Raspberry Pi print-bridge source and service: V4 package used for the print-polling architecture.
- Pi kiosk startup script is **not** included: it was not in the verified source bundles.

Public-repository safety changes compared with the VPS snapshot:
- Personal TEST-tenant email in V9TestMenuSeed was replaced by the optional CASHREGISTER_TEST_SEED_EMAIL environment variable.
- CASHREGISTER_V9_SEED_TEST defaults to false in the public Compose example.
- No live credentials are included. Test/service env files are examples only.

The host-local Codigle cloud translation bridge is documented separately; the Java V13 translation adapter may remain unconfigured when the host-side translation timer handles jobs. This is a portfolio snapshot, not a one-command production deployment.

No real-money payments, certification, or tax compliance are included.
