import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

spec = importlib.util.spec_from_file_location("audit_exports", Path(__file__).resolve().parents[1] / "audit_exported_components.py")
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)

class ExportAuditTest(unittest.TestCase):
    def check(self, component):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "AndroidManifest.xml"
            path.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.nexaflow.app"><application>' + component + '</application></manifest>')
            return audit.audit(path)

    def test_arbitrary_permission_does_not_authorize_export(self):
        self.assertTrue(self.check('<service android:name="Unknown" android:exported="true" android:permission="android.permission.INTERNET"/>'))

    def test_known_component_requires_exact_gate(self):
        self.assertTrue(self.check('<receiver android:name="com.nexaflow.core.engine.SmsReceiver" android:exported="true" android:permission="android.permission.INTERNET"/>'))
        self.assertFalse(self.check('<receiver android:name="com.nexaflow.core.engine.SmsReceiver" android:exported="true" android:permission="android.permission.BROADCAST_SMS"/>'))

    def test_provider_cannot_weaken_read_gate(self):
        self.assertTrue(self.check('<provider android:name="rikka.shizuku.ShizukuProvider" android:exported="true" android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" android:readPermission="android.permission.INTERNET"/>'))

    def test_implicit_export_and_activity_alias_are_audited(self):
        self.assertTrue(self.check('<receiver android:name="Unknown"><intent-filter/></receiver>'))
        self.assertTrue(self.check('<activity-alias android:name=".MainActivity" android:exported="true"/>'))

if __name__ == "__main__":
    unittest.main()
