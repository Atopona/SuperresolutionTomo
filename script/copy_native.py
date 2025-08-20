import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DLL = ROOT / 'native' / 'build' / 'Release' / 'ngx_jni.dll'
TARGET = ROOT / 'common' / 'src' / 'main' / 'resources' / 'assets' / 'super_resolution' / 'natives' / 'win64' / 'ngx_jni.dll'

if not DLL.exists():
    # try MinGW-like output path
    DLL2 = ROOT / 'native' / 'build' / 'ngx_jni.dll'
    if DLL2.exists():
        DLL = DLL2

if not DLL.exists():
    print(f"Native dll not found: {DLL}")
    exit(1)

TARGET.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(DLL, TARGET)
print(f"Copied {DLL} -> {TARGET}")

