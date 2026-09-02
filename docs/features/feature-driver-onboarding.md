---
id: feature-driver-onboarding
title: Driver onboarding, and the object store as a client
type: feature
status: active
owner: unassigned
involved_services:
  - shashki-server
client_entries:
  - screen-driver-onboarding
api:
  - endpoint-driver
tags: [driver, storage]
---

# Driver onboarding, and the object store as a client

## 1. Overview

A driver hands over three documents before they drive: a licence, an insurance certificate and a
photo of the car. The bundle posts each file to this product's server, and the server writes it to
bochka with **s3kn** — a real authenticated write against the same store the map's tiles are read
from, which is the reason this feature exists at all rather than being a form.

**The store is reached from both ends and only one of them is public.** Tiles are a browser fetching
an object over ranged HTTP with no credentials; a document is a signed `PutObject` from a process
that holds a secret. Research [D12](../research/research-architecture.md) is where that pair is
recorded and where the choice to build this was made.

## 2. Business rules

* **The bytes travel one hop further than a reader expects.** A browser cannot sign SigV4 without
  holding the key that signs it — [B-07](../backlog/B-07-serve-pmtiles-from-bochka.md) found that
  from the other side — so the file goes to the server and the server is the store's only client.
  Presigned upload URLs would remove the hop and are not built: they need the same credentials on the
  server anyway, and they would put the store's hostname in front of a browser.
* **Three kinds, one object each.** The key is `drivers/<driverId>/<KIND>`; a second upload of the
  same kind replaces the first, because a driver correcting a blurred photo is the ordinary case and
  a version history is a product nobody asked for.
* **2 MiB is the limit and it is enforced by reading, not by trusting.** The route reads one byte
  past the limit and refuses if it arrives; `Content-Length` is a claim.
* **`ACCEPTED` is drawn and never produced.** Nothing in this product reviews a document. A state a
  human would have to set is in the protocol and in the kit's colours, and no code path writes it —
  the screen says *pending* for ever, which is honest, and a green tick after an upload would be the
  fabricated onboarding this feature was written instead of.
* **Whose documents these are is the token's, not the request's.** The routes take no driver id at
  all; the subject is the identity, exactly as on the rest of the driver's surface
  ([B-52](../backlog/B-52-driver-routes-behind-the-token.md)).

## 3. What happens with no store configured

`NoDocumentStore` is bound when `SHASHKI_DOCUMENTS_ENDPOINT` is unset, and every call throws
`NoStoreConfiguredException` → **503**. That is the demo configuration: `docker compose up` brings
bochka with it, and a server run without one says so rather than pretending the upload worked.

## 4. Anchors

| What | Where |
|---|---|
| The port | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/documents/domain/DocumentStore.kt` |
| s3kn behind it | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/documents/data/S3DocumentStore.kt` |
| The routes | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/documents/DocumentRouting.kt` |
| Against a live bochka | `server/src/test/kotlin/io/github/youndie/shashki/server/feature/documents/DocumentsAgainstBochkaTest.kt` |
| The screen | [screen-driver-onboarding](../screens/screen-driver-onboarding.md) |
