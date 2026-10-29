"""Run once from Task Scheduler to write the native HKCU outside MSIX redirection."""
import argparse
import json
from pathlib import Path
import subprocess
import winreg

parser=argparse.ArgumentParser()
parser.add_argument('--rime',type=Path,required=True)
parser.add_argument('--status',type=Path,required=True)
parser.add_argument('--set-default',action='store_true')
args=parser.parse_args()
try:
    if not (args.rime/'build/rime_ice.schema.yaml').exists():
        raise ValueError('Deployed Rime profile not found')
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER,r'Software\Rime\Weasel') as key:
        winreg.SetValueEx(key,'RimeUserDir',0,winreg.REG_SZ,str(args.rime))
        winreg.SetValueEx(key,'Profile',0,winreg.REG_SZ,'hans')
        winreg.SetValueEx(key,'Hant',0,winreg.REG_DWORD,0)
    if args.set_default:
        command="Set-WinDefaultInputMethodOverride -InputTip '0804:{A3F4CDED-B1E9-41EE-9CA6-7B4D0DE6CB0A}{3D02CAB6-2B8E-4781-BA20-1C9267529467}'"
        subprocess.run([r'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe','-NoProfile','-NonInteractive','-Command',command],check=True,timeout=15,creationflags=subprocess.CREATE_NO_WINDOW,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    args.status.write_text(json.dumps({'success':True,'rime':str(args.rime),'set_default':args.set_default}),encoding='utf-8')
except Exception as error:
    args.status.write_text(json.dumps({'success':False,'error_type':type(error).__name__}),encoding='utf-8')
    raise SystemExit(1)
