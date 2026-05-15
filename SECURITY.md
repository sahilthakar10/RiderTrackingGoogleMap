# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in the RiderTracking SDK, please report it responsibly.

**Do NOT open a public GitHub issue for security vulnerabilities.**

Instead, email: **sahilthakar10@gmail.com**

Include:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

We will respond within 48 hours and work with you to resolve the issue before any public disclosure.

## API Key Security

- **Never** hardcode API keys in source code
- Use `local.properties` or `BuildConfig` fields
- Restrict API keys in Google Cloud Console (package name + SHA-1)
- Use separate keys for debug and release builds
