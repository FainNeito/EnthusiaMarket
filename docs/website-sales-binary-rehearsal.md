# Planned old/new binary rollback rehearsal

Status: design only. No old binary or complete downgrade has been tested. This document does not authorize a server or production operation.

## Pins and isolation

- Select the actual target older build and capture its exact source commit, dependency/runtime versions, artifact digest and build evidence. Production metadata observed on 2026-11-03 named `EnthusiaMarket-1.0.52.jar` (3,984,922 bytes) and reported version 1.0.52 at startup. The official `v1.0.52` release tag points to `14351db4dc416138341a11d0e4e27602f207221b`, and its published asset has the same name and size, with GitHub-reported SHA-256 `1c7dee48dbe060a826a00ca85d828b656bd690e88387e9a49da031e7425d0483`. This identifies a candidate, not the actual installed file's hash or source provenance.
- The official v1.0.52 source contains migrations through V029, records history directly, and has no shop-history outbox reader. Fork main `7177a0b4c1111af2ecb4e626f046230c56622e45` is a separate later reference with a version-1 outbox reader. Verify both binaries independently; do not assume either behavior applies to an unverified installed artifact.
- Build the candidate from the reviewed exact source tree using its normal build. Capture its artifact digest and runtime versions. Stop if either artifact cannot be tied to source or the binaries cannot run on the selected fixture runtime.
- Use disposable database files and fresh directories created solely for the rehearsal. No production snapshots, player accounts, shared TEST worlds, external listeners or real payment providers. Preserve the complete fixture, logs and both immutable input records as evidence.

## Outbox compatibility

1. With the candidate, create one version-1 and one attributed version-3 pending record using normal writer code, then stop all fixture writers. Save their original byte hashes and recording IDs.
2. On separate disposable copies, run each verified older target. For the later version-1-outbox target, verify version 1 reaches its repository with its original ID and payload and observe version 3 quarantine and preserved bytes. For v1.0.52, verify whether both pending records remain untouched when the actual binary runs, rather than assuming it has an outbox reader. A rename to `.corrupt` or a silently unconsumed file is not recovery.
3. On a fresh copy of the original version-3 input, run the compatible candidate against a migrated disposable database. Simulate a committed transaction followed by a lost acknowledgment and restart; assert one receipt and one public projection with the original identity. Confirm the pending record is removed only after a successful replay. Preserve any failing or ambiguous input and stop.
4. On a separate copy after the compatible drain, exercise each older binary using its own actual transaction path and migration runner. The later outbox target should process a newly generated version-1 input; v1.0.52 instead writes history directly. Verify successful writes and reads and that existing receipts and archive remain intact. After re-upgrade, establish how to identify and reconcile transactions created during the legacy interval without inventing event identities or claiming complete website coverage. Check guild eligibility and unknown historical identity. Check both SQLite and the actual supported production database engine before describing database downgrade as compatible.

## Restore and acceptance limits

Run the producer and the website consumer together against disposable stores after both reviewed implementations exist. Exercise website poller and signed-ingestion concurrency, a producer restore with ID reuse, stable event identity, stale workers, generation rotation, duplicate replay, and preserved incomplete coverage. Verify a stopped and fenced transition, independently checked backups, explicit reconciliation, and a second restart. A passing unit test or successful plugin startup alone does not prove these conditions. Retain source pins, artifact digests, exact commands, logs, row counts, hashes and failure evidence; never publish sample player or transaction data.
