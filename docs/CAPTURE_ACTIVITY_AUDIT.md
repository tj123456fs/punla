# CaptureActivity input audit

Phase 2 read-only audit of `planning/CaptureActivity.kt` and its exported SEND intent filter. This document records gaps only; no capture behavior was changed.

## Current safeguards
- Only `Intent.ACTION_SEND` is accepted; other actions fail through the existing guarded path.
- Shared text is truncated to 20,000 characters before persistence.
- The fallback subject is capped at 200 characters.
- Attachment URIs must use the `content://` scheme.
- Attachment copies are streamed with an 8 MB hard cap, so oversized files are rejected during copy.
- Failed copies delete the partially written app-private file.
- Empty shares are rejected when both text and attachment are absent.
- Exceptions are converted to a user-visible Toast and the activity finishes cleanly.

## MIME enforcement gaps
The manifest advertises these SEND types:
- `text/*`
- `image/*`
- `application/pdf`
- `application/json`

Runtime attachment handling does not re-check that allowlist. `CaptureAttachments.copy()` accepts any readable `content://` URI that reaches the activity, uses `ContentResolver.getType()` only to choose a filename extension, and falls back to `.bin` when the type is unknown. It does not inspect file signatures or verify that the provider-reported MIME matches the actual bytes.

This means an explicit intent, an inaccurate provider MIME, or another dispatch path can store content outside the manifest-advertised types. That is a validation gap, but fixing it would be a behavior change and is deferred.

## Size-limit gaps
The per-attachment 8 MB limit is enforced correctly by streamed byte counting, so the implementation does not rely on an untrusted metadata size.

There is no cumulative Inbox attachment-size cap at capture time. Repeated valid 8 MB shares can grow the app-private Inbox directory over time. Backup code has its own separate limits, but that does not constrain live Inbox storage.

Shared text is silently truncated at 20,000 characters. The current flow does not tell the user that truncation occurred.

## Malformed-content handling
Punla does not parse shared attachment bytes during capture, so malformed PDF/image/JSON content is generally stored as raw bytes rather than validated for internal structure.

Unreadable URIs, invalid URI schemes, missing streams, oversized streams, and other copy exceptions are caught by the activity. Partial copied files are deleted before exit.

Unknown or misleading MIME can still produce a `.bin` file or an extension that does not match the real content. Later opening relies on the stored extension to derive a MIME type, so content/MIME mismatch can surface only when another app tries to open the attachment.

## Deferred follow-up
A future hardening pass can decide whether to:
- enforce the manifest MIME allowlist again at runtime,
- reject null/unknown MIME instead of storing `.bin`,
- add lightweight signature validation for PDF/image/JSON types,
- add a cumulative Inbox storage cap,
- warn when shared text was truncated.

Those choices are intentionally not implemented in Phase 2 because they can reject inputs that Punla currently accepts.
