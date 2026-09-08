#!/usr/bin/env python3
"""Rewrites the conditions-not-satisfied execution messages in every locale
of core/execution to name triggers/conditions explicitly (v3.59.4)."""
import glob
import os

NEW_VALUES = {
    "execution_conditions_not_satisfied_end_behavior_completed": {
        "": "Triggers or conditions are not satisfied. Only the end behavior (when the task ends) was completed.",
        "ar": "المحفزات أو الشروط غير متطابقة. تم تنفيذ سلوك النهاية فقط (عند انتهاء المهمة).",
    },
    "execution_conditions_not_satisfied_no_end_behavior": {
        "": "Triggers or conditions of the task are not satisfied, so it cannot run now. No end behavior is configured.",
        "ar": "محفزات المهمة أو شروطها غير متطابقة، لذلك لا يمكن تشغيلها الآن. لم يتم تكوين سلوك نهاية.",
    },
    "execution_conditions_not_satisfied_end_behavior_failed": {
        "": "Triggers or conditions of the task are not satisfied, so it cannot run now. The end behavior could not be completed.",
        "ar": "محفزات المهمة أو شروطها غير متطابقة، لذلك لا يمكن تشغيلها الآن. تعذر إكمال سلوك النهاية.",
    },
}


def locale_of(path):
    name = os.path.basename(os.path.dirname(path))
    return "" if name == "values" else name.replace("values-", "", 1)


def main():
    total = 0
    for path in glob.glob("core/execution/src/main/res/values*/strings.xml"):
        loc = locale_of(path)
        with open(path, encoding="utf-8") as f:
            lines = f.readlines()
        changed = False
        for i, line in enumerate(lines):
            for key, table in NEW_VALUES.items():
                if f'name="{key}"' in line:
                    lines[i] = (
                        f'    <string name="{key}">{table.get(loc) or table[""]}</string>\n'
                    )
                    changed = True
                    total += 1
        if changed:
            with open(path, "w", encoding="utf-8", newline="") as f:
                f.writelines(lines)
            print("updated", path)
    print("TOTAL", total)


if __name__ == "__main__":
    main()
