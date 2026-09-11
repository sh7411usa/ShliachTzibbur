# The `$E1` group encryption protocol

Shliach Tzibbur can encrypt a group's message bodies end-to-end over a
plaintext transport: the Tzibbur server sees ciphertext, sender id, sequence
number, and timestamp for every message, and nothing else. This document
specifies the wire format and the client behavior around it. It is a
description of what this client does, not a server-enforced protocol — the
server has no concept of encryption; it just stores and delivers opaque
message bodies.

## 1. Threat model and non-goals

**In scope:** a passive server (or anyone with server-side access/logs) should
not be able to read message content. A client without the group's current key
should not be able to read message content.

**Explicitly out of scope:**

- **Key exchange.** There is no Diffie-Hellman, no PAKE, no QR code. The
  64-hex-character key is generated on one device and copied to the others by
  the admin, over a channel the group already trusts (in person, a phone call,
  or — as a convenience — an SMS the app can send for you; see §7).
- **Forward secrecy / ratcheting.** One static symmetric key encrypts every
  message until an admin rotates it. Compromise of a key exposes every message
  encrypted under it, past and future, until rotation.
- **Metadata privacy.** Sender identity, group membership, timing, and message
  sequence are visible to the server exactly as in a plaintext group.
- **Authenticity of *which member* sent a message** beyond what the server
  already asserts (the server authenticates the sending device; there's no
  additional per-message signature over the plaintext).

Within that model, `$E1` is a standard authenticated-encryption scheme (AES-GCM)
with one deliberately unusual feature: **the nonce is never transmitted**,
because there is no envelope to put it in — only a chat message. §4 covers why
that's viable and what it costs.

## 2. Wire format

A message body sent to the server is one of:

- **plaintext**, for a non-encrypted group, or a member without the key, or
- a **`$E1:` token**: `"$E1:" + base64(ciphertext ‖ tag)`
  - `base64` is standard RFC 4648, **no padding**.
  - `ciphertext ‖ tag` is the raw output of `AES/256/GCM/NoPadding`: the
    ciphertext followed immediately by the 16-byte (128-bit) authentication
    tag, as `javax.crypto.Cipher` emits it.
  - The plaintext fed to the cipher is **`"!" + messageText`** (UTF-8) — see
    §5 for why.

The server still prepends `"<display name>: "` to whatever body it's given, so
a stored message looks like `"Alice: $E1:qF3z…"`. Clients split on the first
`": "` exactly as they do for plaintext; the token itself is untouched.

The `$E<n>` prefix is a version tag, not a magic number for its own sake: a
future `$E2:` scheme (say, a post-quantum KEM-wrapped key, or a scheme with a
transmitted nonce) can be added as a second implementation of the same
`EncryptionScheme` interface without touching `$E1` traffic or its readers.
Detection (`isCipherText`) matches `^\$E\d+:` generically; only the scheme
registry needs the new entry.

## 3. Keys

- **Algorithm:** AES-256. A key is 32 bytes, represented as 64 lowercase hex
  characters.
- **Generation:** `SecureRandom` — 32 bytes, hex-encoded. Nothing derived from
  a passphrase; there's no KDF in the loop.
- **Storage:** each device keeps its own list of keys for a group in local,
  app-private storage (Preferences DataStore), alongside a designated
  *active* key used for new sends. This is the same protection level the app
  already gives the session's bearer token — app-sandbox + whatever full-disk
  encryption the OS provides, not a hardware-backed keystore wrap (that's a
  possible future hardening step, not done today).
- **Distribution:** entirely out of band. Pasting the full hand-off SMS into
  any key field on the app is fine — the app extracts the standalone 64-hex
  run and discards the rest (`KeyHex.extract`).
- **Multiple keys:** a device remembers every key it's ever been given for a
  group, not just the current one, so it can still read history encrypted
  under a retired key after rotation. Decryption always tries the *active* key
  first, then the rest newest-to-oldest (§6).
- **Rotation:** an admin action. It generates a new key, appends it to
  everyone's-eventually list (once they receive it), makes it the new active
  key on the rotating admin's device, and emits a `$KEY_CHANGED` service
  message so other clients know to prompt their user for the new key before
  they can decrypt anything past that point. The admin is warned, before
  confirming, that members are locked out until they have the new key.

## 4. Nonce derivation, and the seq-anticipation problem

GCM requires a 96-bit nonce that is **never reused** under the same key. A
normal protocol transmits the nonce alongside the ciphertext. Shliach Tzibbur
has no field to put one in — only a chat message body — and doesn't want to
spend wire budget (bodies share the server's 1000-character limit) on a nonce
the reader could derive instead. So the nonce is *computed*, not sent:

```
nonce = SHA-256("STZ/E1 " + seq + " " + senderId)[0:12]
```

`seq` is the message's per-group sequence number, assigned by the **server**,
not the client — and that's the catch: **the sender doesn't know its own
message's seq before the server accepts it.** The client has to *anticipate*
one:

```
anticipatedSeq = (highest seq seen locally) + 1 + (other not-yet-confirmed
                  sends already queued ahead of this one)
```

That guess is usually right, but two things can make the server's real seq
differ from it: another member's message lands first (their seq takes the
slot this client guessed), or a batch of the sender's own outbox drains in an
order it didn't predict.

**On decrypt**, the receiver *does* know the real seq (it's the message's own
seq field). It tries that seq's nonce first, and if the AEAD tag doesn't
verify, it retries at seq ± 1, ± 2, ± 3 — seven candidate nonces per key —
before giving up on that key and moving to the next one. In practice this
window comfortably absorbs ordinary send races; a message whose real seq
landed more than 3 away from every sender's guess (which would need a
three-message pile-up mid-send) shows up "not decryptable" until the reader
manually supplies more keys to retry against (still bounded by the same ±3
search).

**The cost of this design:** the nonce depends only on `(seq, senderId)`, not
on any per-send randomness. If the *same sender* manages to encrypt two
different messages that both anticipate the *same* seq (e.g. two offline sends
queued back-to-back before either round-trips), they'd reuse a nonce under the
same key — the classic GCM failure mode (XOR-recoverable keystream, and a
forgery primitive against that nonce). The client mitigates this by spacing
concurrent outbox rows' anticipated seqs apart (`+1, +2, +3, …` per queued
send) rather than eliminating the risk outright; it is a known, accepted
trade-off of not transmitting a nonce, not an oversight.

## 5. The known-plaintext marker

Every plaintext is wrapped as `"!" + messageText` before encryption. On
decrypt, after the GCM tag verifies, the client additionally checks the first
decrypted byte is `!` before accepting the result and stripping it.

This is *not* load-bearing for authenticity — GCM's tag already proves the
ciphertext wasn't tampered with and was encrypted under this exact key and
nonce. Its job is disambiguation during the §4 search: without it, a wrong
`(key, seq-offset)` pair could occasionally still pass the 128-bit tag check
by chance (astronomically unlikely, but the marker makes the failure mode
"reject" instead of "silently return garbage" for free), and — more
practically — it keeps the scheme's plaintext shape unambiguous if a future
`$E2` reuses the same tag-then-check pattern with a weaker AEAD.

## 6. Decryption algorithm, end to end

For a stored message body `m` with sequence `seq` from `senderId`:

1. If `m` doesn't match `^\$E\d+:`, it's plain. (If the group is marked
   encrypted and this message's seq is at or after the seq that turned
   encryption on, it's flagged **insecure** in the UI rather than treated as
   silently fine.)
2. Otherwise pick the scheme by prefix (`$E1` → `AesGcmSeqScheme`).
3. For each known key, active key first then the rest newest → oldest, try
   nonces for `seq, seq-1, seq+1, seq-2, seq+2, seq-3, seq+3` (skipping any
   that go below 1). The first one whose GCM tag verifies *and* whose
   decrypted plaintext starts with `!` wins.
4. No candidate works → **undecryptable**. The UI shows a badge; tapping it
   offers to add another key and re-runs step 3 against every undecryptable
   message in the thread.

Sending is the mirror image: if the group's active key exists, the client
encrypts with `anticipatedSeq` per §4 and puts the token in the outbox in
place of the plaintext body (the *local* pending-bubble text stays plaintext
for the sender's own UI; only the wire body is ciphertext). If the group is
marked encrypted and there is *no* active key on this device, sending is
refused client-side — there is no path to send plaintext into an encrypted
group by accident.

## 7. Group lifecycle as in-band messages

There is no server-side "this group is encrypted" flag. The client infers it
entirely from the message stream, so a plaintext client sees readable text at
every step and a Shliach Tzibbur client that missed the transition can still
reconstruct the correct state from history:

- **Turning encryption on** posts a plaintext message: a human sentence
  ("🔒 Encryption was turned on for this group using Shliach Tzibbur…", with a
  link to the app) plus a machine marker, `#ShliachTzibbur/enc-on`. Any client
  that sees this marker **from a group admin** flips its local "encrypted"
  flag for the group and remembers the seq it happened at (plaintext before
  that point is not retroactively flagged insecure). A non-admin sender's
  enc-on/enc-off message is ignored outright — otherwise any member could
  flip everyone else's client into (or out of) sending plaintext.
- **Seeing ciphertext** (`$E1:` token) with no prior enc-on is a fallback
  trigger for the same flag, in case the enc-on message itself hasn't synced
  yet.
- **A brand-new encrypted group defers the announcement**: since nobody can
  post at all below 3 members, the enc-on message isn't sent until the admin's
  client observes the group has reached 3 members — avoiding a confusing
  "encryption is on" notice before there's anyone to read it. The admin sees a
  local "starts at 3 members" note in the meantime.
- **Turning it off**, and **rotating the key**, are the same pattern — a
  readable sentence plus `#ShliachTzibbur/enc-off` or `#ShliachTzibbur/key-changed`,
  admin-gated the same way.

## 8. Everything else rides inside the plaintext, transparently

Replies (`RE:<seq> …`), reactions (a reply whose body is only emoji), polls
(`$POLL:` + `RE:<seq>:<n|END>` votes), and pin control (`$PIN:`/`$UNPIN:<seq>`)
are all just conventions inside the *message text* — the same string that gets
wrapped in `"!"` and encrypted. None of them need to know encryption exists:
the client always decrypts first, then parses those markers against the
decrypted text. In an encrypted group this means a poll's question and options,
a vote, or which message got pinned are exactly as protected as an ordinary
message; the only thing that's necessarily plaintext is the control messages
in §7, because they're the bootstrap signal that has to be readable before any
key is in play.

## 9. Practical limits this imposes

- **Message length.** The server caps a body at 1000 characters. A token is
  `4 + base64(len(msg)+17)` chars — base64 costs ~4/3, plus the 16-byte tag and
  the marker byte — so an encrypted group's effective plaintext budget is
  meaningfully smaller than 1000 (roughly 730 ASCII characters, less for
  multi-byte UTF-8). The composer computes the exact projected length live
  (`MessageCrypto.projectedCipherLength`) so the user sees the real number, not
  an estimate, and Send is disabled over the limit.
- **Search** inside an encrypted group only matches messages the searching
  device can currently decrypt.
- **A message an admin didn't verify sent the enc-on/off/key-changed marker is
  inert** — by design (§7) — which means a legitimate transition can be
  briefly invisible to a client that hasn't yet loaded the sender's role
  (member list refreshes on opening the thread; the fallback in step 2 of §7
  self-heals this within one real ciphertext message).
