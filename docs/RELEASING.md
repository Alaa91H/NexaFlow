# Release procedure

1. Integrate against the latest remote main without discarding local/user changes. Review semantic merges, not only conflict markers.
2. Complete relevant regression tests and the repository quality gates. Update the English changelog, configuration reference, catalog and validation record.
3. Push the reviewed commit. Inspect the CI run for that exact SHA; resolve failures and repeat before tagging.
4. Release tags are created explicitly; the repository does not create a tag merely because a `main` CI run passes. Verify the intended version tag does not already exist, then create an annotated tag on the validated commit and push it. Never move an existing published tag to hide a fix.
5. The tag workflow requires production signing credentials, verifies the APK certificate against the configured keystore and runs packaging/alignment checks. The build job depends on lint, so release publication cannot bypass that job.
6. The workflow creates the GitHub release from the matching changelog section and attaches **only** the two versioned APKs (phone and Wear OS). The R8 mapping and the SBOM stay attached to the workflow run as artifacts; reruns prune any other release asset, so the download page never accumulates build output. Inspect the tag run and release assets, then report their actual status and links.
7. Publishing the release triggers `release-announce.yml`, which posts the release body and both APKs to the Telegram channel (`@NexaFlowApp`) and to the community releases topic (`t.me/NexaFlowCommunity/4`) through `@NexaFlowBuildBot`. The targets are repository secrets: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHANNEL_CHAT_ID`, `TELEGRAM_COMMUNITY_CHAT_ID` and `TELEGRAM_COMMUNITY_TOPIC_ID`. If an announcement has to be repeated, run the workflow manually with the existing tag; a rerun re-sends the message.

Release pages are a download surface, not an artifact store. Anything that belongs to the build (mapping, SBOM, checksums, bundles) is published as a workflow artifact, never as a release attachment.

`git describe` supplies version names; numeric version codes come from `buildSrc` version logic. Do not hand-edit APK metadata. Local ad-hoc builds can use debug signing; those artifacts are not substitutes for a production-signed release.

CI currently supplies JVM/Robolectric tests and static/package checks. It does not prove a physical device matrix. Record instrumentation and manual results separately. If signing, CI or publication fails, report the failure and do not claim a successful release.
