"""Fail-closed receipt for exact cloud-owned AVD and serial, no device operations."""
import argparse,json,pathlib,re

def verify_cleanup(avds,devices,avd,serial,avd_rc,device_rc):
 return avd_rc==0 and device_rc==0 and avd not in avds.splitlines() and not any(line.split() and line.split()[0]==serial for line in devices.splitlines())

def main():
 p=argparse.ArgumentParser();p.add_argument('--proof',type=pathlib.Path,required=True);p.add_argument('--avd',required=True);p.add_argument('--serial',required=True);p.add_argument('--avd-rc',type=int,required=True);p.add_argument('--device-rc',type=int,required=True);a=p.parse_args()
 avds=(a.proof/'remaining-avds.txt').read_text();devices=(a.proof/'remaining-devices.txt').read_text();ok=verify_cleanup(avds,devices,a.avd,a.serial,a.avd_rc,a.device_rc)
 (a.proof/'cleanup.json').write_text(json.dumps({'deviceDeletedVerified':ok,'ownedAvd':a.avd,'ownedSerial':a.serial,'avdInventoryExit':a.avd_rc,'deviceInventoryExit':a.device_rc},indent=2)+'\n');raise SystemExit(0 if ok else 1)
if __name__=='__main__':main()
