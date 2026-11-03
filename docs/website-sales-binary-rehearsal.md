# Planned old/new binary rollback rehearsal

Status: design only. No old binary or complete downgrade has been tested. This document does not authorize a server or production operation.

## Pins and isolation

- Select the actual target older build and capture its exact source commit, dependency/runtime versions, artifact digest and build evidence. Fork main `7177a0b4c1111af2ecb4e626f046230c56622e45` is a reference baseline, not proof of the installed production version.
- Build the candidate from the reviewed exact source tree using its normal build. Capture its artifact digest and runtime versions. Stop if either artifact cannot be tied to source or the binaries cannot run on the selected fixture runtime.
- Use disposable database files and fresh directories created solely for the rehearsal. No production snapshots, player accounts, shared TEST worlds, external listeners or real payment providers. Preserve the complete fixture, logs and both immutable input records as evidence.

## Outbox compatibility

1. With the candidate, create one version-1 and one attributed version-3 pending record using normal writer code, then stop all fixture writers. Save their original byte hashes and recording IDs.
2. On a separate disposable copy only, run the real older binary's outbox reader against each record. Verify the version-1 record reaches its mock repository with the original ID and payload. Verify the version-3 record is not delivered, and record the actual quarantine behavior and unchanged record bytes. A rename to `.corrupt` is not a recovery strategy.
3. On a fresh copy of the original version-3 input, run the compatible candidate against a migrated disposable database. Simulate a committed transaction followed by a lost acknowledgment and restart; assert one receipt and one public projection with the original identity. Confirm the pending record is removed only after a successful replay. Preserve any failing or ambiguous input and stop.
4. On a separate copy after the compatible drain, exercise the actual older binary with a newly generated version-1 input. Verify a successful write and read using its own repository and migration runner; verify the existing receipts and newer archive remain intact. Check both SQLite and the actual supported production database engine before describing database downgrade as compatible.

## Restore and acceptance limits

Run the producer and the website consumer together against disposable stores after both reviewed implementations exist. Exercise website poller and signed-ingestion concurrency, a producer restore with ID reuse, stable event identity, stale workers, generation rotation, duplicate replay, and preserved incomplete coverage. Verify a stopped and fenced transition, independently checked backups, explicit reconciliation, and a second restart. A passing unit test or successful plugin startup alone does not prove these conditions. Retain source pins, artifact digests, exact commands, logs, row counts, hashes and failure evidence; never publish sample player or transaction data.
