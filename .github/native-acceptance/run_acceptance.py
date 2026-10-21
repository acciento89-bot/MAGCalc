"""Single owned API35 cloud emulator; external native host, no store or purchase calls."""
import argparse, datetime, hashlib, io, json, pathlib, re, subprocess, tarfile
PACKAGE='de.kamilunavo.magcalc'
HOST='de.kamilunavo.approvedcalcqa'
METHOD='magProjectAlternativeBaselineRotationColdReloadAndRealShare'

def run_acceptance(adb,serial,product,host,test,proof,execute=subprocess.run):
 proof=pathlib.Path(proof);proof.mkdir(parents=True,exist_ok=True)
 receipt={'status':'STARTED','serial':serial,'method':METHOD,'expectedApi':35,'transactions':False,'targetDataCleared':False,'deviceCreated':False,'deviceCleanupOwner':'GitHub emulator action plus run-cloud.sh EXIT trap'}
 started=False
 def call(args,timeout=15):
  r=execute([str(adb),'-s',serial,*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
  if r.returncode:
   if args[:3]==['shell','am','instrument']:
    (proof/'instrumentation.txt').write_bytes(r.stdout or b'')
    (proof/'instrumentation-stderr.txt').write_bytes(r.stderr or b'')
    raise RuntimeError('Native instrumentation command failed')
   raise RuntimeError('adb command failed: '+args[0])
  return r.stdout
 def capture(name,args):
  try:(proof/name).write_bytes(call(args,10));return True
  except BaseException as e:receipt.setdefault('diagnosticErrors',{})[name]=type(e).__name__;return False
 try:
  if not re.fullmatch(r'emulator-[0-9]+',serial):raise RuntimeError('Disposable emulator serial required')
  if call(['shell','getprop','ro.build.version.sdk']).decode().strip()!='35':raise RuntimeError('Expected official API35 emulator')
  if call(['shell','getprop','ro.kernel.qemu']).decode().strip()!='1':raise RuntimeError('Physical device prohibited')
  anr=call(['shell','dumpsys','activity','lastanr']).decode(errors='replace');(proof/'preflight-anr.txt').write_text(anr)
  if 'com.android.systemui' in anr.lower():raise RuntimeError('ENVIRONMENT_BLOCKED:SystemUI ANR; no dismissal attempted')
  receipt['apkSha256']={role:hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest() for role,p in [('product',product),('host',host),('test',test)]}
  for apk in [product,host,test]:call(['install','-r',str(apk)],90)
  info=call(['shell','dumpsys','package',PACKAGE]).decode(errors='replace');(proof/'installed-identity.txt').write_text(info)
  if not re.search(r'\bversionCode=6\b',info) or not re.search(r'\bversionName=1\.1\.0\b',info):raise RuntimeError('Installed candidate identity mismatch')
  started=True
  result=call(['shell','am','instrument','-w','-r','-e','class',HOST+'.ApprovedCalcNativeTest#'+METHOD,HOST+'.test/androidx.test.runner.AndroidJUnitRunner'],300).decode(errors='replace')
  (proof/'instrumentation.txt').write_text(result)
  if 'OK (1 test)' not in result or any(x in result for x in ['FAILURES','INSTRUMENTATION_FAILED','Process crashed']):raise RuntimeError('Native acceptance failed; see instrumentation.txt')
  archive=call(['exec-out','run-as',PACKAGE,'cat','files/vessel-projects.json'])
  data=json.loads(archive);assert isinstance(data.get('projects'),list),'Missing actual archive projects'
  (proof/'actual-vessel-projects.json').write_bytes(archive);receipt['archiveSha256']=hashlib.sha256(archive).hexdigest();receipt['status']='PASSED'
 except BaseException as error:
  receipt['status']='FAILED';receipt['error']=str(error)
  if started and isinstance(error,subprocess.TimeoutExpired):
   if error.stdout:(proof/'instrumentation-partial.txt').write_bytes(error.stdout)
   if error.stderr:(proof/'instrumentation-partial-stderr.txt').write_bytes(error.stderr)
 finally:
  # No adb access at all for rejected physical/nonowned serials.
  if re.fullmatch(r'emulator-[0-9]+',serial):
   capture('final-screen.png',['exec-out','screencap','-p']);capture('window.txt',['shell','dumpsys','window','windows']);capture('last-anr.txt',['shell','dumpsys','activity','lastanr']);capture('crash-log.txt',['logcat','-d','-b','crash']);capture('logcat.txt',['logcat','-d','-t','2000'])
   try:
    call(['shell','uiautomator','dump','/sdcard/mag-acceptance-final.xml'],10)
    capture('final-ui.xml',['exec-out','cat','/sdcard/mag-acceptance-final.xml'])
   except BaseException as e:receipt.setdefault('diagnosticErrors',{})['final-ui.xml']=type(e).__name__
   try:
    packed=call(['exec-out','run-as',HOST,'tar','-cf','-','-C','files','approvedcalc-native-qa'],10)
    with tarfile.open(fileobj=io.BytesIO(packed)) as archive:
     for member in archive.getmembers():
      path=pathlib.PurePosixPath(member.name)
      if member.isfile() and path.parent==pathlib.PurePosixPath('approvedcalc-native-qa') and path.suffix in {'.png','.xml','.json','.jsonl','.txt'} and member.size<=10*1024*1024:(proof/path.name).write_bytes(archive.extractfile(member).read())
   except BaseException as e:receipt.setdefault('diagnosticErrors',{})['nativeEvidence']=type(e).__name__
   if receipt['status']=='PASSED' and (not (proof/'events.jsonl').is_file() or 'nativeEvidence' in receipt.get('diagnosticErrors',{})):
    receipt['status']='FAILED';receipt['error']='Native evidence extraction incomplete'
   if started:
    try:call(['shell','am','force-stop',HOST],10)
    except BaseException as e:receipt.setdefault('diagnosticErrors',{})['hostStop']=type(e).__name__
  receipt['finishedUTC']=datetime.datetime.now(datetime.timezone.utc).isoformat();(proof/'result.json').write_text(json.dumps(receipt,indent=2)+'\n')
 return receipt

def main():
 p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',default='emulator-5554');p.add_argument('--product',type=pathlib.Path,required=True);p.add_argument('--host',type=pathlib.Path,required=True);p.add_argument('--test',type=pathlib.Path,required=True);p.add_argument('--proof',type=pathlib.Path,required=True);a=p.parse_args()
 result=run_acceptance(a.adb,a.serial,a.product,a.host,a.test,a.proof);print(json.dumps(result,indent=2));raise SystemExit(0 if result['status']=='PASSED' else 1)
if __name__=='__main__':main()
