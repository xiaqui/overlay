# Development guidelines

These instructions apply to `xiaqui/overlay`. Read `TASK.md` for current scope and `docs/SPEC_JA.md`, `docs/ISSUE4_JA.md`, and relevant research or validation documents for product behavior.

- Confirm `origin`, fetch the latest `main`, and inspect open PRs before editing. Continue corrections on the existing PR and do not mix unrelated changes.
- Preserve Android 6 compatibility unless an explicit, evidence-backed decision changes the minimum version. Do not equate compilation with device acceptance.
- Use a branch and PR for behavioral, service/window, permission, build, workflow, or multi-file changes. Do not merge unless the current task authorizes it.
- Verify the exact PR head SHA, relevant tests/build, CI, review threads, and documented device matrix. P0/P1 findings block merge; fix P2 or document a reasoned disposition.
- Keep source/automated verification separate from visual and device testing. Record untested OS/device combinations explicitly.
- Do not commit screenshots, logs, identifiers, credentials, signing material, private URLs, or personal data unless intentionally public test fixtures.
- Report branch, commit/head SHA, PR, tests/CI, review state, artifact/delivery state when applicable, and remaining device checks.

