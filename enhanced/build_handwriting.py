"""Build the pinned, offline Zinnia recognizer for the phone and AVD."""
import sys,subprocess
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import ROOT,SDK
source=ROOT/'enhanced/native';vendor=source/'third_party/zinnia'
toolchain=SDK/'ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin'
for abi,target in [('arm64-v8a','aarch64'),('x86_64','x86_64')]:
 out=source/'lib'/abi;out.mkdir(parents=True,exist_ok=True)
 subprocess.run([str(toolchain/(target+'-linux-android32-clang++.cmd')),'-shared','-fPIC','-O2','-std=c++11','-static-libstdc++','-DHAVE_CONFIG_H','-Wl,-z,max-page-size=16384','-I'+str(vendor),str(source/'handwriting.cpp'),*[str(vendor/n) for n in ['character.cpp','recognizer.cpp','feature.cpp','sexp.cpp']],'-o',str(out/'libgboard_local_handwriting.so')],check=True)
