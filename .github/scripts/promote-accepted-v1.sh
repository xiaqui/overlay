#!/usr/bin/env bash
set -euo pipefail

# Publish the exact device-accepted bytes. This job never restores a private key.
test "$GITHUB_EVENT_NAME" = push
test "$GITHUB_REF" = refs/heads/main
test "$GITHUB_REPOSITORY" = xiaqui/overlay
accepted_source=9418f6c492a380b655bc188d74956dbac1ef31e8
accepted_run=45182762592
accepted_artifact=14821503345
expected_apk=dbf7f614da5dea30bd778a749f148b8ee99b20251fe36c6dcab1966b815dcf19
expected_zip=0dd547ae2dc42c35be82747e2f2934d8859d01ec96199e487f49086f022b3bd4
expected_cert=dcfd2c8122274ec4148473c96b1853989c8d22e58e445d6ec4889ad5b34dffb2
tag=overlay-v1.0.0
name=OverlayApps-v1.0.0.apk
tools="$ANDROID_HOME/build-tools/35.0.0"

# Fail before creating anything if publication already exists. Never replace assets.
gh release list --repo "$GITHUB_REPOSITORY" --limit 100 --json tagName > "$RUNNER_TEMP/releases.json"
if jq -e --arg tag "$tag" 'any(.[]; .tagName == $tag)' "$RUNNER_TEMP/releases.json" >/dev/null; then
  echo 'The stable Release already exists; refusing a duplicate or replacement.' >&2
  exit 1
fi
gh api "repos/$GITHUB_REPOSITORY/actions/runs/$accepted_run" > "$RUNNER_TEMP/accepted-run.json"
jq -e --arg sha "$accepted_source" '.conclusion == "success" and .head_sha == $sha' "$RUNNER_TEMP/accepted-run.json" >/dev/null
gh api "repos/$GITHUB_REPOSITORY/actions/artifacts/$accepted_artifact" > "$RUNNER_TEMP/accepted-artifact.json"
jq -e --arg sha "$accepted_source" --arg digest "sha256:$expected_zip" \
  '.expired == false and .workflow_run.head_sha == $sha and .digest == $digest' "$RUNNER_TEMP/accepted-artifact.json" >/dev/null
gh api "repos/$GITHUB_REPOSITORY/actions/artifacts/$accepted_artifact/zip" > "$RUNNER_TEMP/accepted.zip"
echo "$expected_zip  $RUNNER_TEMP/accepted.zip" | sha256sum -c -
mkdir -p "$RUNNER_TEMP/accepted" delivery
unzip -q "$RUNNER_TEMP/accepted.zip" -d "$RUNNER_TEMP/accepted"
grep -qx "source_sha=$accepted_source" "$RUNNER_TEMP/accepted/PROVENANCE.txt"
grep -qx "run_id=$accepted_run" "$RUNNER_TEMP/accepted/PROVENANCE.txt"
cp "$RUNNER_TEMP/accepted/OverlayApps-v1.0.0-preview.apk" "delivery/$name"
test "$(stat -c %s "delivery/$name")" = 691553
echo "$expected_apk  delivery/$name" | sha256sum -c -

# Squash merges need the original source fetched separately. Compare every APK input.
git fetch --no-tags origin "$accepted_source"
git diff --exit-code "$accepted_source" "$GITHUB_SHA" -- \
  app/src/main app/build.gradle.kts build.gradle.kts settings.gradle.kts gradle.properties
"$tools/apksigner" verify --verbose --print-certs --min-sdk-version 23 "delivery/$name" > delivery/APK-VERIFICATION.txt
grep -qx "Signer #1 certificate SHA-256 digest: $expected_cert" delivery/APK-VERIFICATION.txt
grep -q 'Verified using v1 scheme (JAR signing): true' delivery/APK-VERIFICATION.txt
grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' delivery/APK-VERIFICATION.txt
"$tools/aapt" dump badging "delivery/$name" > delivery/PACKAGE.txt
grep -q "package: name='com.xiaqui.overlay'" delivery/PACKAGE.txt
grep -q "versionCode='12'" delivery/PACKAGE.txt
grep -q "versionName='1.0.0'" delivery/PACKAGE.txt
grep -q "sdkVersion:'23'" delivery/PACKAGE.txt
grep -q "targetSdkVersion:'25'" delivery/PACKAGE.txt
grep -q "application-label:'Overlay Apps'" delivery/PACKAGE.txt
if grep -q 'application-debuggable' delivery/PACKAGE.txt; then exit 1; fi
if "$tools/aapt" dump permissions "delivery/$name" | grep -q 'android.permission.INTERNET'; then exit 1; fi
printf '%s\n' "$accepted_source" > delivery/SOURCE_SHA.txt
printf 'accepted_source_sha=%s\naccepted_run_id=%s\nrelease_main_sha=%s\npromotion_run_id=%s\n' \
  "$accepted_source" "$accepted_run" "$GITHUB_SHA" "$GITHUB_RUN_ID" > delivery/PROVENANCE.txt
(cd delivery && sha256sum "$name" > SHA256SUMS)
cat > "$RUNNER_TEMP/release-notes.md" <<EOF
Overlay Apps v1.0.0 正式版（versionCode 12、Android 6〜10対象）。

使用状況アクセスの任意の許可誘導を共通化し、表示名・見出しを整理、Recents注意文をマニュアルへ移動しました。既存設定、角丸5状態、描画機能、固定xiaqui署名鍵との互換性を維持しています。角丸配置の新仕様は保留のままです。

2026-11-24のユーザー実機検収合格を受け、検収済みAPKのバイトを再ビルドせず昇格しました。

- APK source: $accepted_source
- Accepted signing run: $accepted_run
- Release main/tag commit: $GITHUB_SHA
- APK SHA-256: $expected_apk
- Signer SHA-256: $expected_cert

CI: API23/26/28/29の全試験、API24は既存の限定suite。Android 14全機能保証ではありません。AppDate実機での取得操作は未確認です。
EOF
gh release create "$tag" --repo "$GITHUB_REPOSITORY" --target "$GITHUB_SHA" --draft \
  --title 'Overlay Apps v1.0.0' --notes-file "$RUNNER_TEMP/release-notes.md"
gh release upload "$tag" --repo "$GITHUB_REPOSITORY" \
  "delivery/$name" delivery/SHA256SUMS delivery/SOURCE_SHA.txt delivery/PROVENANCE.txt delivery/PACKAGE.txt delivery/APK-VERIFICATION.txt
mkdir "$RUNNER_TEMP/retrieved"
gh release download "$tag" --repo "$GITHUB_REPOSITORY" --pattern "$name" --dir "$RUNNER_TEMP/retrieved"
cmp "delivery/$name" "$RUNNER_TEMP/retrieved/$name"
(cd "$RUNNER_TEMP/retrieved" && sha256sum -c "$GITHUB_WORKSPACE/delivery/SHA256SUMS")
"$tools/apksigner" verify --min-sdk-version 23 "$RUNNER_TEMP/retrieved/$name"
gh release edit "$tag" --repo "$GITHUB_REPOSITORY" --draft=false --prerelease=false --latest
gh release view "$tag" --repo "$GITHUB_REPOSITORY" --json url,isDraft,isPrerelease,assets > "$RUNNER_TEMP/published.json"
jq -e --arg name "$name" '.isDraft == false and .isPrerelease == false and any(.assets[]; .name == $name and .size == 691553)' "$RUNNER_TEMP/published.json" >/dev/null
echo "Release: https://github.com/$GITHUB_REPOSITORY/releases/tag/$tag" >> "$GITHUB_STEP_SUMMARY"
