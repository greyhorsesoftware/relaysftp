# Security

RelaySFTP is an FTP, SFTP and S3 client. It handles SSH private keys, passwords and S3 secret
keys, and stores saved credentials in the operating system keychain (macOS Keychain, Windows
Credential Manager, Linux Secret Service). Nothing is written to its own data files in
plaintext.

## Reporting a vulnerability

Please do not open a public issue for security problems. Use GitHub's private vulnerability
reporting on this repository ("Report a vulnerability" under the Security tab). You will get a
reply within a week.

In scope: anything that exposes credentials or file contents, weakens host-key or TLS
verification, or lets a remote server make the client do something the user did not ask for.
