---
id: B-47
title: "Driver onboarding, which is the one scenario the object store has left"
status: question
priority: P2
size: L
stage: stage-5-the-rest-of-the-kit
blocked_by: [B-52]
---

# B-47 — Driver onboarding, which is the one scenario the object store has left

Research D12 records that bochka is a host and not a dependency, that s3kn left the stack silently,
and that exactly one scenario in this product would give an S3 *client* a reason to exist: the kit's
D1 — a driver uploading a licence, an insurance certificate and a photo of the car, written by
something that is not a browser fetching a public object. D12 lists three ways to go and says the
first is the only one that is an item. This is that item, filed as a **question** because the choice
it exists to force has not been made.

- **If built: the browser uploads through the server, and the server is s3kn's consumer.** A browser
  cannot sign SigV4 without a secret (B-07 found that the hard way), so the driver bundle posts the
  file to `POST /api/driver/documents`, and the server writes it to bochka with s3kn — a real
  authenticated write against the store the tiles are read from. That is the stack shown from both
  ends, and it is the argument for building it at all.
- **If dropped: the stack table says so, with D12 as the reason,** and `s3kn` leaves the research's
  version table rather than staying there as a library the product does not use. That is also a
  legitimate outcome of this item and costs one paragraph.
- **What the screen is, if built:** D1's document list with three states per document — not yet,
  pending, accepted — in the semantic colours the kit reserves (amber pending, green accepted), and a
  light upload field, because the kit says both places that fight that instinct stay light.
- The rejected alternative is a fabricated onboarding with no store behind it — a form that accepts a
  file and forgets it. It photographs identically and demonstrates nothing.
- Deliberately **not** covered: verifying a document. "Accepted" is a flag an operator would set;
  here it is set by nobody, and the screen says *pending* for ever, honestly.

- AC (if built): a driver uploads three documents from the bundle; each is an object in bochka
  written through s3kn, readable back through the server and not by an anonymous `GET`.
- AC (if built): `driver_onboarding` golden against D1, all three document states.
- AC (either way): D12 is closed in the research with the choice and its date, and the version table
  matches the dependency graph.
- Anchors: `docs/research/research-architecture.md`,
  `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/`
