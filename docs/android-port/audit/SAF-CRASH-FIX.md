# SAF "Use this folder" crash — root cause and fix

## Symptom

The app exits immediately after the player selects the game folder and presses
**Use this folder** in the Storage Access Framework (SAF) picker.

## Root cause (verified against AOSP source)

`Intent.ACTION_OPEN_DOCUMENT_TREE` hands back a **tree** URI:

```
content://<authority>/tree/<treeId>          e.g. content://com.android.externalstorage.documents/tree/primary%3AGames
```

A tree URI is **not** a document URI. A `DocumentsProvider`'s `query()` is `final`
and matches the URI against the `UriMatcher` it registers in
`DocumentsProvider.registerAuthority()` (AOSP `DocumentsProvider.java`):

```
document/*                         MATCH_DOCUMENT
document/*/children                MATCH_CHILDREN
tree/*/document/*                  MATCH_DOCUMENT_TREE
tree/*/document/*/children         MATCH_CHILDREN_TREE
```

There is **no** bare `tree/*` pattern. So for the picked tree URI the match falls
to `default: throw new UnsupportedOperationException("Unsupported Uri " + uri)`.
`ContentProvider.Transport` marshals it (`DatabaseUtils.writeExceptionToParcel`,
code 3) and `ContentResolver.query` rethrows it as
`UnsupportedOperationException`.

The first thing `SetupActivity.onActivityResult` did with the picked URI was

```java
String name = SafTreeStager.displayName(getContentResolver(), tree);
```

and `displayName` queried the **bare tree URI**:

```java
resolver.query(treeUri, columns, null, null, null)   // provider: UnsupportedOperationException
```

It caught only `IllegalArgumentException | SecurityException`, so the
`UnsupportedOperationException` escaped `onActivityResult` on the **UI thread** →
uncaught exception → the app dies the instant the player confirms the folder.
That is the reported crash.

A second, latent defect sat on the same misunderstanding:
`SafTreeStager.listChildren` called `DocumentsContract.getDocumentId(directory)`
where `directory` was the picked tree URI. `getDocumentId` accepts only
`document/*` or `tree/*/document/*` shapes and throws `IllegalArgumentException`
otherwise (AOSP `DocumentsContract.java`). The walker swallowed it as "an
unreadable directory", so even without the crash staging would have copied **zero
files** and the screen would have reported an incomplete folder.

Both share one root cause: **the picked tree URI was used as if it were a
document URI.** The correct address is the tree's root document:

```
content://<authority>/tree/<treeId>/document/<treeId>
```

(a tree's root document id is the tree id itself — exactly what
`DocumentFile.fromTreeUri` builds).

## Fix

Smallest change that removes the root cause and preserves the SAF/staging design:

- `SafTreeStager.rootDocumentUri(treeUri)` turns the picked tree URI into its
  root document URI. `scan()` walks from it, and `displayName()` queries it. This
  removes both the crash and the "stages nothing" defect.
- `displayName()` no longer lets a provider exception escape: it falls back to the
  URI's last path segment (a name is cosmetic and must not abort the pick).
- `listChildren()` catches any `RuntimeException` from the provider (a revoked
  grant, an exotic provider) and logs it, instead of only two subtypes.
- `SetupActivity.onActivityResult` wraps the provider-touching work and reports a
  problem + retry on failure, so nothing thrown out of the callback can kill the
  app.
- Structured `Log.i/w/e` diagnostics around every critical stage (picker result,
  remembered grant, staging start/finish/failure, provider refusals), with
  exception class and stack trace and no document contents.
- `SetupActivity` guards its staging callbacks against a destroyed Activity
  (`destroyed` flag set in `onDestroy`), so a first copy that outlives the screen
  does not touch dead views.
- `GameFolderStore.remember` catches `RuntimeException` from
  `takePersistableUriPermission` (not just `SecurityException`) and logs it; the
  run's grant still holds and the next launch re-asks.

No storage permission is added, no persisted permission is removed,
`GameFolderValidator` is untouched, and the SAF/local-staging architecture is
unchanged.

## Files changed

| Path | Change |
|:--|:--|
| `android/app/src/main/java/.../SafTreeStager.java` | root-document URI for walk + display name; never let a provider exception escape; diagnostics |
| `android/app/src/main/java/.../SetupActivity.java` | guard the callback; report failures; destroyed-Activity guard; diagnostics |
| `android/app/src/main/java/.../GameFolderStore.java` | catch `RuntimeException` from the persist request; log |
| `android/app/src/main/java/.../SafTreeUri.java` | **new** — pure URI-shape rule (unit-tested) |
| `android/app/src/main/java/.../SafPickerResult.java` | **new** — pure picker-result decision (unit-tested) |
| `android/app/src/main/res/values/strings.xml` | one string for an unreadable picked folder |

## Regression tests

Pure-JVM tests (no Android types, run by `:app:testDebugUnitTest`):

- `SafTreeUriTest` — pins the defect: `documentId(treeUri)` throws
  `IllegalArgumentException` for a bare tree URI; the root document URI is
  `tree/<id>/document/<id>`; a child's id reads from a document URI under a tree.
- `SafPickerResultTest` — a foreign request is ignored, a cancel or a null URI
  keeps the previous choice, only `RESULT_OK` + URI stages.

Both test the exact rules `SafTreeStager`/`SetupActivity` use (the production
code calls `SafTreeUri`/`SafPickerResult`), so they are not mirrors. They are
**not** proof of compatibility with any real document provider: that is a
device-only path.

## Validation

GitHub Actions only (no local build). See the PR/commit for run links.

## Not claimed

A green CI run proves the Java compiles and the tests pass; it does **not** prove
the crash is gone on a device. The picker, a real `content://` provider and a
multi-GB copy are device-only. The AOSP source cited above is the evidence for the
root cause; a physical-device run of "pick folder → Use this folder" remains the
final acceptance step.
