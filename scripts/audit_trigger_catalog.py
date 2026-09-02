from pathlib import Path
import re

model = Path("domain/src/main/java/com/nexaflow/domain/models/Automation.kt").read_text()
enum_body = re.search(r"enum class TriggerType \{(.*?)\n\}", model, re.S).group(1)
enums = re.findall(r"^\s*([A-Z][A-Z0-9_]*)\s*(?:,|$)", enum_body, re.M)
builder = Path("feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerEditorCard.kt").read_text()
options_body = re.search(r"val triggerTypeOptions = listOf\((.*?)\n\)", builder, re.S).group(1)
options = re.findall(r"TriggerType\.([A-Z0-9_]+)", options_body)
categories = re.findall(r"TriggerType\.([A-Z0-9_]+) to TriggerCategory", builder)
print(f"enum_count={len(enums)}")
print(f"option_count={len(options)}")
print("missing_from_picker=" + ",".join(sorted(set(enums) - set(options))))
print("missing_category=" + ",".join(sorted(set(enums) - set(categories))))
print("duplicate_options=" + ",".join(sorted({x for x in options if options.count(x) > 1})))
