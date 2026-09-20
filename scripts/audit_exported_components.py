"""Release gate: audit every source manifest and the merged release manifest.

Only the two reviewed UI entry points are public. Every other exported component
must have its exact known platform signature permission, never an arbitrary string.
"""
import argparse
import re
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
# Launcher / share-sheet UI entry points. WearMainActivity is the watch
# companion launcher (android.hardware.type.watch devices only).
PUBLIC = {
    "com.nexaflow.app.MainActivity",
    "com.nexaflow.app.SaveBackupActivity",
    "com.nexaflow.wear.WearMainActivity",
}
GATES = {
    "com.nexaflow.core.engine.SmsReceiver": "android.permission.BROADCAST_SMS",
    "com.nexaflow.core.engine.SmsConsentReceiver": "android.permission.BROADCAST_SMS",
    "com.nexaflow.core.engine.NexaCallScreeningService": "android.permission.BIND_SCREENING_SERVICE",
    "com.nexaflow.core.engine.AppTriggerAccessibilityService": "android.permission.BIND_ACCESSIBILITY_SERVICE",
    "com.nexaflow.core.engine.NotificationListener": "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
    "rikka.shizuku.ShizukuProvider": "android.permission.INTERACT_ACROSS_USERS_FULL",
    "androidx.work.impl.background.systemjob.SystemJobService": "android.permission.BIND_JOB_SERVICE",
    "androidx.work.impl.diagnostics.DiagnosticsReceiver": "android.permission.DUMP",
    "androidx.profileinstaller.ProfileInstallReceiver": "android.permission.DUMP",
}
GATES.update({f"com.nexaflow.feature.widgets.TaskTile{i}Service": "android.permission.BIND_QUICK_SETTINGS_TILE" for i in range(1, 9)})

# Wear Data Layer listeners: Play Services delivers MESSAGE_RECEIVED/DATA_CHANGED
# to these by contract, only for the same signed package on paired devices.
# No platform permission exists for this surface; a custom permission would
# block GMS delivery entirely. Commands are namespaced under /nexaflow/ and
# processed by the owning app's own automation engine.
REVIEWED_NO_PERMISSION = {
    "com.nexaflow.app.wear.WearCommandListenerService",
    "com.nexaflow.wear.data.WearDataListenerService",
}

def audit(path, namespace=None):
    root = ET.parse(path).getroot()
    package = namespace or root.get("package", "")
    errors = []
    app = root.find("application")
    if app is None:
        return errors
    for node in app:
        if node.tag not in {"activity", "activity-alias", "receiver", "service", "provider"}:
            continue
        exported = node.get(ANDROID + "exported")
        if exported == "false":
            continue
        if exported != "true" and node.find("intent-filter") is None:
            continue
        name = node.get(ANDROID + "name", "")
        if name.startswith("."):
            name = package + name
        elif "." not in name:
            name = package + "." + name
        permission = node.get(ANDROID + "permission", app.get(ANDROID + "permission"))
        if name in PUBLIC and node.tag == "activity":
            continue
        if name in REVIEWED_NO_PERMISSION and node.tag == "service" and permission is None:
            continue
        provider_override = node.tag == "provider" and any(
            node.get(ANDROID + key, permission) != GATES.get(name)
            for key in ("readPermission", "writePermission")
        )
        if name not in GATES or permission != GATES[name] or provider_override:
            errors.append(f"{path}: unreviewed export {node.tag} {name} permission={permission!r}")
    return errors


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--merged", type=Path, required=True)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    errors = []
    manifests = []
    for module in (repo / "app", repo / "core", repo / "feature", repo / "wear"):
        for path in module.rglob("AndroidManifest.xml"):
            relative = path.relative_to(repo)
            if "build" in relative.parts or "src" not in relative.parts:
                continue
            # Test-only fixtures are not packaged in production.
            if any(p in relative.parts for p in ("test", "androidTest", "testFixtures")):
                continue
            manifests.append(path)
            build = path.parents[2] / "build.gradle.kts"
            match = re.search(r'namespace\s*=\s*"([^"]+)"', build.read_text(encoding="utf-8"))
            errors.extend(audit(path, match.group(1) if match else None))
    if not args.merged.is_file():
        errors.append(f"Merged release manifest missing: {args.merged}; build :app:processReleaseManifest first")
    else:
        errors.extend(audit(args.merged))
    if errors:
        raise SystemExit("\n".join(errors))
    print(f"PASS: {len(manifests)} production source manifests and merged release manifest")

if __name__ == "__main__":
    main()
