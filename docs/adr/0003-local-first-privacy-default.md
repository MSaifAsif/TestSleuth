# ADR 0003: Local-First Privacy Default

## Status

Accepted

## Context

The roadmap requires that users can generate useful reports without accounts, source upload, cloud services, or production instrumentation.

## Decision

The default product mode writes local artifacts only. Report generation and event export must work without network access.

Future telemetry, hosted dashboards, or organization-wide analysis must be optional and must not be required for the open-source local workflow.

## Consequences

The report format, event schema, and baseline comparison storage need stable local file formats. Privacy and redaction behavior must be designed before collectors start recording environment, property, source, or configuration values.

