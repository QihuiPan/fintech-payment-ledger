# Security Policy

## Supported versions

Security fixes are applied to the latest release and the `main` branch.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability. Use GitHub's private vulnerability reporting feature on this repository and include:

- the affected version or commit;
- the endpoint, component, or configuration involved;
- reproduction steps or a proof of concept;
- the expected security impact;
- any suggested mitigation.

Please avoid accessing data that does not belong to you, disrupting a running service, or publishing the report before a fix is available.

## Deployment expectations

The default Docker Compose configuration is a loopback-only evaluation environment. Before exposing the service to a network:

- use `compose.production.yaml` and replace every credential;
- terminate TLS at a trusted reverse proxy;
- restrict database connectivity to the application network;
- keep the PostgreSQL application role non-superuser and least-privileged;
- replace local Basic authentication with an external identity provider for multi-user deployments;
- connect the transactional outbox to a durable broker and monitor delivery age;
- configure backups, restore tests, secret rotation, and centralized security logs.

The repository demonstrates payment-ledger engineering controls. It is not a certified or regulated payment product.
