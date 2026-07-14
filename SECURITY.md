# Security Policy

## Supported Versions

TestSleuth has not published a stable release yet. Until the first public release, security fixes will be made on the `main` branch.

After stable releases begin, this file will be updated with the supported version policy.

## Reporting a Vulnerability

Please do not report suspected security vulnerabilities in public issues.

For now, report vulnerabilities by creating a private security advisory in GitHub if available for this repository. If private advisories are not enabled, contact the repository owner directly and include:

- Affected version, commit, or branch.
- A clear description of the issue.
- Steps to reproduce.
- Potential impact.
- Any suggested mitigation.

## Security Scope

TestSleuth is designed to run locally against test suites and build systems. Security-sensitive areas include:

- Local report contents.
- Environment variables and system properties.
- Build metadata.
- Source paths and test names.
- Future collector output from frameworks, containers, databases, and CI systems.

The project should avoid uploading source code, test data, or reports by default.

## Disclosure

Maintainers will acknowledge valid reports as soon as practical, investigate the issue, and coordinate a fix before public disclosure.

