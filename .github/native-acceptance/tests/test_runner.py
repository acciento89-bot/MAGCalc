import importlib.util, pathlib, subprocess, tempfile, unittest, sys, io, tarfile, json
ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('acceptance',ROOT/'run_acceptance.py');module=importlib.util.module_from_spec(spec);sys.modules[spec.name]=module;spec.loader.exec_module(module)
class Fake:
 def __init__(self,sdk='35',outcome='OK (1 test)',timeout=False,bad_native=False):self.sdk=sdk;self.outcome=outcome;self.timeout=timeout;self.bad_native=bad_native;self.calls=[]
 def __call__(self,args,**kw):
  self.calls.append((args,kw.get('timeout')));s=' '.join(args)
  if 'instrument -w' in s:
   if self.timeout:raise subprocess.TimeoutExpired(args,kw['timeout'],output=b'PARTIAL_NATIVE_START',stderr=b'fixture timeout')
   out=self.outcome
  elif 'ro.build.version.sdk' in s:out=self.sdk
  elif 'ro.kernel.qemu' in s:out='1'
  elif 'dumpsys package de.kamilunavo.magcalc' in s:out='versionCode=6 minSdk=26 targetSdk=36\nversionName=1.1.0'
  elif 'lastanr' in s:out='No ANR has occurred since boot'
  elif 'cat files/vessel-projects.json' in s:out='{"projects":[]}'
  elif 'tar -cf' in s:
   if self.bad_native:out='not tar'
   else:
    buf=io.BytesIO()
    with tarfile.open(fileobj=buf,mode='w') as t:
     data=b'{"state":"PASS"}';info=tarfile.TarInfo('approvedcalc-native-qa/events.jsonl');info.size=len(data);t.addfile(info,io.BytesIO(data))
    return subprocess.CompletedProcess(args,0,buf.getvalue(),b'')
  else:out='Success'
  return subprocess.CompletedProcess(args,0,out.encode(),b'')
class RunnerTests(unittest.TestCase):
 def run_fixture(self,fake):
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d);apks=[]
   for n in ['product.apk','host.apk','test.apk']:(p/n).write_bytes(b'fixture');apks.append(p/n)
   result=module.run_acceptance('adb','emulator-5554',*apks,p/'proof',execute=fake);self.files={f.name:f.read_bytes() for f in (p/'proof').iterdir()};return result
 def test_exact_single_method_own_apks_only_and_no_billing_actions(self):
  f=Fake();r=self.run_fixture(f);self.assertEqual(r['status'],'PASSED');commands=[' '.join(c[0]) for c in f.calls]
  self.assertEqual(sum('instrument -w' in c for c in commands),1);self.assertIn('magProjectAlternativeBaselineRotationColdReloadAndRealShare',next(c for c in commands if 'instrument -w' in c));self.assertFalse(any('brennercalc' in c or 'hydrocalc' in c or 'pm clear' in c for c in commands));self.assertEqual(next(t for c,t in f.calls if 'instrument' in c),300)
 def test_wrong_sdk_never_installs_or_runs(self):
  f=Fake(sdk='36');r=self.run_fixture(f);self.assertEqual(r['status'],'FAILED');self.assertFalse(any('install' in c[0] or 'instrument' in c[0] for c in f.calls))
 def test_failure_keeps_diagnostics_and_nonpass_receipt(self):
  f=Fake(outcome='FAILURES!!! Tests run:1 Failures:1');r=self.run_fixture(f);self.assertEqual(r['status'],'FAILED');self.assertTrue(any('screencap' in c[0] for c in f.calls));self.assertTrue(any('logcat' in c[0] for c in f.calls))
 def test_timeout_is_bounded_and_stops_only_owned_host(self):
  f=Fake(timeout=True);r=self.run_fixture(f);self.assertEqual(r['status'],'FAILED');self.assertTrue(any('force-stop' in c[0] and 'de.kamilunavo.approvedcalcqa' in c[0] for c in f.calls));self.assertTrue(all(t<=300 for c,t in f.calls))
 def test_nonemulator_rejected_before_any_adb_call(self):
  f=Fake()
  with tempfile.TemporaryDirectory() as d:
   r=module.run_acceptance('adb','personal-phone',pathlib.Path('a'),pathlib.Path('b'),pathlib.Path('c'),pathlib.Path(d),execute=f)
  self.assertEqual(r['status'],'FAILED');self.assertEqual(f.calls,[])
 def test_timeout_partial_instrumentation_retained(self):
  self.run_fixture(Fake(timeout=True));self.assertEqual(self.files.get('instrumentation-partial.txt'),b'PARTIAL_NATIVE_START')
 def test_missing_native_evidence_prevents_pass_even_if_instrumentation_says_ok(self):
  self.assertEqual(self.run_fixture(Fake(bad_native=True))['status'],'FAILED')
 def test_all_three_apk_hash_roles_are_retained_even_when_basenames_collide(self):
  r=self.run_fixture(Fake());self.assertEqual(set(r['apkSha256']),{'product','host','test'})
 def test_nonzero_instrumentation_exit_retains_both_streams_and_failure(self):
  class NonzeroInstrument(Fake):
   def __call__(self,args,**kw):
    if 'instrument' in args:
     self.calls.append((args,kw.get('timeout')))
     return subprocess.CompletedProcess(args,1,b'INSTRUMENTATION_FAILED: exact fixture reason',b'fixture adb stderr')
    return super().__call__(args,**kw)
  result=self.run_fixture(NonzeroInstrument())
  self.assertEqual(result['status'],'FAILED')
  self.assertEqual(result['error'],'Native instrumentation command failed')
  self.assertEqual(self.files.get('instrumentation.txt'),b'INSTRUMENTATION_FAILED: exact fixture reason')
  self.assertEqual(self.files.get('instrumentation-stderr.txt'),b'fixture adb stderr')
 def test_wrong_installed_candidate_identity_never_instruments(self):
  for code,name in [('5','1.1.0'),('4','1.1.0'),('60','1.1.0'),('6','1.0.0'),('','1.1.0')]:
   class WrongIdentity(Fake):
    def __call__(self,args,**kw):
     result=super().__call__(args,**kw)
     if 'dumpsys package de.kamilunavo.magcalc' in ' '.join(args):result.stdout=f'versionCode={code} minSdk=26 targetSdk=36\nversionName={name}'.encode()
     return result
   fake=WrongIdentity();result=self.run_fixture(fake)
   self.assertEqual(result['status'],'FAILED');self.assertEqual(result['error'],'Installed candidate identity mismatch')
   self.assertFalse(any('instrument' in args for args,_ in fake.calls))
class CleanupTests(unittest.TestCase):
 def test_cleanup_inventory_failure_is_not_treated_as_device_absence(self):
  spec=importlib.util.spec_from_file_location('cleanup',ROOT/'verify_cleanup.py');cleanup=importlib.util.module_from_spec(spec);spec.loader.exec_module(cleanup)
  self.assertFalse(cleanup.verify_cleanup('', '', 'owned','emulator-5554',124,0))
  self.assertFalse(cleanup.verify_cleanup('', '', 'owned','emulator-5554',0,1))
  self.assertFalse(cleanup.verify_cleanup('owned\n', 'List of devices attached\n','owned','emulator-5554',0,0))
  self.assertFalse(cleanup.verify_cleanup('', 'emulator-5554\tdevice\n','owned','emulator-5554',0,0))
  self.assertTrue(cleanup.verify_cleanup('', 'List of devices attached\n','owned','emulator-5554',0,0))
class SourceTests(unittest.TestCase):
 def test_host_queries_only_actual_target_and_no_network_permission(self):
  s=(ROOT/'host/app/src/main/AndroidManifest.xml').read_text();self.assertIn('android:name="de.kamilunavo.magcalc"',s);self.assertNotIn('de.kamilunavo.brennercalc',s);self.assertNotIn('android.permission.INTERNET',s)

 def test_manual_only_readonly_cloud_workflow(self):
  s=(ROOT.parents[0]/'workflows/android-native-acceptance.yml').read_text();events=s.split('on:\n',1)[1].split('permissions:',1)[0];self.assertEqual(events.strip(),'workflow_dispatch:');self.assertIn('contents: read',s);self.assertNotIn('id-token:',s);self.assertIn('timeout-minutes: 20',s);self.assertIn('api-level: 35',s);self.assertIn('target: google_apis',s);self.assertIn('arch: x86_64',s);self.assertIn('if: always()',s);self.assertNotIn('secrets.',s)
 def test_selected_method_body_has_exact_external_lineage(self):
  import hashlib,json
  lineage=__import__('json').loads((ROOT/'lineage.json').read_text());s=(ROOT/'host/app/src/androidTest/java/de/kamilunavo/approvedcalcqa/ApprovedCalcNativeTest.java').read_text();start=s.index('    @Test public void magProjectAlternativeBaselineRotationColdReloadAndRealShare()');end=s.index('\n    private void launch',start);self.assertEqual(hashlib.sha256(s[start:end].encode()).hexdigest(),lineage['method_sha256']);self.assertEqual(s.count('@Test public void'),1);self.assertNotIn('de.kamilunavo.brennercalc',s);self.assertNotIn('de.kamilunavo.hydrocalc',s)
if __name__=='__main__':unittest.main()
