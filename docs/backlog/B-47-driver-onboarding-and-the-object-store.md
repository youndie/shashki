---
id: B-47
title: "Driver onboarding, which is the one scenario the object store has left"
status: done
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

## What it turned out to be

**Built, which closes D12 with the first of its three options and puts the one snapshot coordinate in
this build.** `io.github.youndie:s3-client:0.1.0-SNAPSHOT` is in `:server`'s graph — s3kn publishes
no release, the catalog pin says so in a comment, and the research's §1 table is amended at the row
that said no snapshot resolves here. The store is now shown from both ends: a browser fetching tiles
over public ranged HTTP, and a server signing a `PutObject` for a licence.

**The hop is the feature's shape, not an implementation detail.** B-07 learned from the read side
that a browser cannot sign SigV4 without holding the key that signs it; the write side inherits it
exactly. So the bundle posts to `POST /api/driver/documents/{kind}` and this server is the store's
only client. A presigned upload URL removes nothing — it needs the same credentials here and puts the
store's hostname in front of a browser.

**The routes take no driver id at all**, which is what the rest of the driver's surface would look
like if it had been written after [B-52](B-52-driver-routes-behind-the-token.md) rather than before
it. The key is `drivers/<subject>/<KIND>`; there is no path segment or body field for anybody to put
somebody else's id in, and the read-back is behind the same token — an object store serving a licence
to an anonymous `GET` is the hole B-52 shut, in a new place. That refusal is asserted against the
live stand rather than assumed: the bootstrap script creates the bucket and publishes *no* policy,
and `DocumentsAgainstBochkaTest` demands a 403/401 for a reader with no token. "We did not make it
public" is a statement about our intent, not about the bucket.

**Three uploads, read back byte-identical, with the size taken from the store's own listing.** That
size is the only fact this screen can state about a file it cannot draw, and it comes from
`ListObjectsV2` rather than from what the client thought it sent.

**`ACCEPTED` is in the protocol, in the kit's colours, and written by nothing.** Nobody reviews a
document here, so a driver who uploads all three sees three amber rows for ever. The rejected
alternative — a form that accepts a file and forgets it — photographs identically and demonstrates
nothing; this one photographs identically to a product that reviews documents and is honest about
not being one.

**The finding nobody was looking for: B-48's rule was too broad by one word.** That item ended with
"accent-coloured *text* is a control's label; figures take the foreground brush". The line this item
added to the shift screen *is* a control's label, so the rule permitted the accent — and the light
golden measured **2.11:1, the same number, the same amber, the same white** that the rule was written
to stop. The accent reads because it is a **surface** carrying black ink, not because the thing
wearing it is a control. The line is drawn in the foreground brush and the research is amended where
the rule is stated. What made it visible is that the light variant is a fixture now rather than an
intention: the picture refused a rule a careful reader would have applied correctly.
