#!/usr/bin/env python3
"""Checks source-level VPN startup invariants without Android SDK or device."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent.parent
VPN = ROOT / "app/src/main/java/br/com/joaolds/dnslocal/vpn/LocalDnsVpnService.kt"


class VpnStartupChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = VPN.read_text(encoding="utf-8")
        cls.start = cls.source.split("override fun onStartCommand(", 1)[1].split(
            "private fun establishTunnel(", 1
        )[0]
        cls.establish = cls.source.split("private fun establishTunnel(", 1)[1].split(
            "private fun pumpPackets(", 1
        )[0]
        cls.stop = cls.source.split("private fun stopTunnel()", 1)[1].split(
            "override fun onRevoke()", 1
        )[0]

    def test_one_shot_background_load(self):
        start = self.start
        self.assertIn("loader = Thread({", start)
        self.assertIn('dns-list-session-loader', start)
        self.assertIn('HaGeZiListStore(File(filesDir, "dns-lists")).load()', start)
        self.assertLess(start.index("loader = Thread({"), start.index("HaGeZiListStore("))
        self.assertLess(start.index("HaGeZiListStore("), start.index("mainHandler.post"))
        self.assertNotIn("postDelayed", start)
        self.assertNotIn("ScheduledExecutor", start)
        self.assertNotIn("Timer(", start)

    def test_stale_start_cannot_establish(self):
        self.assertIn("generation.incrementAndGet()", self.start)
        self.assertIn("generation.get() != epoch", self.start)
        self.assertIn("status != Status.STARTING", self.start)
        self.assertIn("stopSelfResult(startId)", self.start)
        self.assertIn("loader?.interrupt()", self.stop)
        self.assertIn("generation.incrementAndGet()", self.stop)

    def test_vpn_established_only_after_list_load(self):
        self.assertNotIn(".establish()", self.start)
        self.assertIn("mainHandler.post", self.start)
        self.assertIn("establishTunnel(selection, list, epoch)", self.start)
        self.assertIn(".establish()", self.establish)
        self.assertIn("DnsDomainFilter(list)", self.establish)
        self.assertIn(".setBlocking(true)", self.establish)
        self.assertEqual(self.establish.count(".addRoute("), 1)

    def test_no_periodic_polling(self):
        source = self.source
        for name in ("postDelayed(", "scheduleAtFixedRate(", "scheduleWithFixedDelay(",
                     "Thread.sleep(", "Timer(", "while (true) {\n                val list"):
            self.assertNotIn(name, source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
