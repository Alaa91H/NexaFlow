"""Generate factual enum/picker inventory; runtime claims remain separately tested."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from audit_catalog_and_releases import enum_values

ROOT = Path(__file__).resolve().parents[1]
model = (ROOT / "domain/src/main/java/com/nexaflow/domain/models/Automation.kt").read_text(encoding="utf-8")
builder = ROOT / "feature/automation-builder/src/main"
strings = {n.attrib["name"]: "".join(n.itertext()) for n in ET.parse(builder / "res/values/strings.xml").getroot() if n.tag == "string"}
actions = (builder / "java/com/nexaflow/feature/builder/AutomationBuilderScreen.kt").read_text(encoding="utf-8")
labels = {kind: strings.get(label, label) for label, kind in re.findall(r"ActionOption\(R.string.(\w+),.*?ActionType.(\w+)", actions)}
triggers = enum_values(model, "TriggerType")
action_types = enum_values(model, "ActionType")
lines = ["# Capability catalog", "", "Generated from source by `python scripts/generate_capability_catalog.py`. This inventory counts enum entries, not equivalent competitor blocks or device-certified capabilities.", "", f"**{len(triggers)} trigger entries; {len(action_types)} action entries.** Two triggers have restricted creation paths. The SENSOR entry has 12 configuration modes; the eight DATA actions each offer several operations. See [configuration](CONFIGURATION.md) and [validation](VALIDATION.md).", "", "## Triggers", "", "| Enum | Creation path |", "| --- | --- |"]
for kind in triggers:
    path = "Legacy saved-task compatibility" if kind == "CONNECTIVITY" else "Plugin configuration flow" if kind == "PLUGIN_EVENT" else "General builder picker"
    lines.append(f"| `{kind}` | {path} |")
lines += ["", "## Actions", "", "Availability depends on permissions, capabilities, Android version and hardware. Settings-opening actions open system UI; their presence does not mean the app can silently change that setting. Elevated actions require a supported provider. Registry and catalog parity tests check dispatch/picker coverage, not every device outcome.", "", "| Enum | Builder label |", "| --- | --- |"]
for kind in action_types:
    lines.append(f"| `{kind}` | {labels.get(kind, kind).replace('|', '/')} |")
(ROOT / "docs/CAPABILITY_CATALOG.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"Generated {len(triggers)} triggers and {len(action_types)} actions")
