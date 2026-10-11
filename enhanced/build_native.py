from pathlib import Path
import os,subprocess,sys
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import ROOT,SDK
version=os.environ.get('ANDROID_NDK_VERSION','28.2.13676358')
compiler=SDK/'ndk'/version/'toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android32-clang++.cmd'
native=ROOT/'enhanced/native';out=native/'lib/arm64-v8a';out.mkdir(parents=True,exist_ok=True)
flags=['-shared','-fPIC','-O2','-std=c++17','-static-libstdc++','-Wl,-z,max-page-size=16384']
subprocess.run([str(compiler),*flags,str(native/'bridge.cpp'),'-ldl','-o',str(out/'libgboard_local_rime.so')],check=True)
for abi,target in [('arm64-v8a','aarch64'),('x86_64','x86_64')]:
    dest=native/'lib'/abi;dest.mkdir(parents=True,exist_ok=True)
    clang=compiler.with_name(target+'-linux-android32-clang++.cmd')
    subprocess.run([str(clang),*flags,str(native/'voice.cpp'),'-L'+str(dest),'-lsherpa-onnx-c-api','-o',str(dest/'libgboard_local_voice.so')],check=True)
