# Releasing

Two audiences: cutting a release, which is a tag; and publishing it to Maven Central, which is
permanent and therefore manual.

## One-time setup

Only the repository owner can do this part. It is needed once, not per release.

### 1. A Central Portal account and the namespace

Sign in at [central.sonatype.com](https://central.sonatype.com) **with GitHub**. Signing in that
way verifies the `io.github.<your-username>` namespace automatically, because the account you
signed in with is the proof. Check under *Namespaces* that `io.github.ialakey` is listed and
verified.

Then *View Account* → *Generate User Token*. It gives a username and a password; they are not
your portal login and can be regenerated at any time.

### 2. A signing key

Central will not accept an unsigned artifact.

```bash
gpg --full-generate-key          # RSA, 4096 bits, and a passphrase you keep
gpg --list-secret-keys --keyid-format=long
```

Publish the public half, so Central can check the signatures:

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```

Export the private half for CI. Treat the output as a credential:

```bash
gpg --armor --export-secret-keys <KEY_ID>
```

### 3. Four repository secrets

```bash
gh secret set CENTRAL_TOKEN_USERNAME    # from the user token
gh secret set CENTRAL_TOKEN_PASSWORD    # from the user token
gh secret set GPG_PRIVATE_KEY           # the armoured private key, whole block
gh secret set GPG_PASSPHRASE            # the passphrase on that key
```

Each command prompts for the value, so nothing lands in your shell history.

## Cutting a release

Versions are plain, never snapshots: Central refuses a snapshot, and JitPack treats a version as
immutable, so a broken one has to be superseded rather than repaired.

```bash
# 1. Bump the version in the four poms and in CHANGELOG.md.
# 2. Check it builds the way CI will.
./mvnw -B -ntp clean verify

# 3. Tag it. The release workflow builds, tests and attaches the jars.
git tag -a vX.Y.Z -m "marketcsgo4j X.Y.Z"
git push origin vX.Y.Z
```

Before calling it done, check that JitPack resolved the tag — CI can be green while the install
snippet in the README resolves to nothing:

```bash
curl -s https://jitpack.io/api/builds/com.github.ialakey/marketcsgo4j/vX.Y.Z
```

`"status": "ok"` is what you want.

## Publishing to Maven Central

Run the **publish to maven central** workflow manually, with the tag as its input:

```bash
gh workflow run publish.yml -f tag=vX.Y.Z
```

It refuses a snapshot, runs the tests again, signs everything and uploads. `autoPublish` is on, so
the version goes live rather than waiting in a staging area.

It is manual on purpose. A GitHub release can be deleted; a version on Maven Central cannot.

To publish from a developer machine instead, the same profile works with the token in
`~/.m2/settings.xml` under the server id `central`:

```bash
./mvnw -P central deploy
```
