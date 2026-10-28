#!/usr/bin/env python3
"""Structural preflight checks; these DO NOT compile or emulate Android."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app/src/main"
NS = "{http://schemas.android.com/apk/res/android}"


def content(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


class AndroidPreflight(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.service = content("app/src/main/java/br/com/joaolds/dnslocal/vpn/LocalDnsVpnService.kt")
        cls.activity = content("app/src/main/java/br/com/joaolds/dnslocal/MainActivity.kt")
        cls.layout = ET.parse(APP / "res/layout/activity_main.xml").getroot()
        cls.strings = ET.parse(APP / "res/values/strings.xml").getroot()
        cls.manifest = ET.parse(APP / "AndroidManifest.xml").getroot()

    def test_vpn_is_restricted_to_dns_ipv4(self):
        s = self.service
        self.assertIn(".setBlocking(true)", s)
        self.assertIn(".addDnsServer(DNS_IP)", s)
        self.assertIn(".addRoute(DNS_IP, 32)", s)
        self.assertEqual(s.count(".addRoute("), 1, "Unexpected additional route")
        self.assertIn(".allowFamily(OsConstants.AF_INET6)", s)
        self.assertNotIn(".addRoute(\"0.0.0.0\", 0)", s)

    def test_protected_sockets_and_bounded_queue(self):
        s = self.service
        self.assertIn("protect(socket)", s)
        self.assertIn("ArrayBlockingQueue(48)", s)
        self.assertIn("generation.incrementAndGet()", s)
        self.assertIn("override fun onRevoke()", s)

    def test_vpn_permissions_and_consent(self):
        manifest = self.manifest
        app = manifest.find("application")
        self.assertIsNotNone(app)
        service = app.find("service")
        self.assertIsNotNone(service)
        self.assertEqual(service.attrib.get(NS + "permission"), "android.permission.BIND_VPN_SERVICE")
        self.assertEqual(service.attrib.get(NS + "exported"), "true")
        self.assertEqual(service.attrib.get(NS + "foregroundServiceType"), "specialUse")
        intents = service.findall("./intent-filter/action")
        self.assertIn("android.net.VpnService", [a.attrib.get(NS + "name") for a in intents])
        self.assertIn("VpnService.prepare(this)", self.activity)

    def test_ui_ids_and_strings_exist(self):
        layout_xml = content("app/src/main/res/layout/activity_main.xml")
        xml_ids = set(re.findall(r"@\+id/([A-Za-z_][A-Za-z_0-9]*)", layout_xml))
        referenced_ids = set(re.findall(r"R\.id\.([A-Za-z_][A-Za-z_0-9]*)", self.activity))
        self.assertFalse(referenced_ids - xml_ids, f"Missing IDs: {referenced_ids - xml_ids}")
        all_strings = {x.get("name") for x in self.strings.findall("string")}
        references = set(re.findall(r"@string/([A-Za-z_][A-Za-z_0-9]*)", layout_xml))
        references.update(re.findall(r"R\.string\.([A-Za-z_][A-Za-z_0-9]*)", self.service + self.activity))
        self.assertFalse(references - all_strings, f"Missing string keys: {references - all_strings}")

    def test_blocking_is_opt_in_and_secondary_is_opt_out(self):
        s = self.service
        a = self.activity
        self.assertIn("EXTRA_BLOCK_ENABLED", s)
        self.assertIn("DnsDomainFilter(list)", s)
        self.assertIn("selection.resolve(request, server, secondary, secondaryEnabled)", s)
        self.assertIn("if (secondaryEnabled)", s)
        self.assertIn("if (selection.backupEnabled)", a)

    def test_blocklist_update_is_explicit_and_fail_safe(self):
        a = self.activity
        store = content("app/src/main/java/br/com/joaolds/dnslocal/blocklist/HaGeZiListStore.kt")
        self.assertIn("updateList.setOnClickListener", a)
        self.assertIn("StandardCopyOption.ATOMIC_MOVE", store)
        self.assertIn("temp.delete()", store)


if __name__ == "__main__":
    unittest.main(verbosity=2)
