"""Offline packaging and pinned-controller integrity checks (Python standard library)."""
from pathlib import Path
import hashlib
import json
import re
import sys

root=Path(__file__).resolve().parents[1]
ignored={'.git','.gradle','build','node_modules','__pycache__'}
files=[p for p in root.rglob('*') if p.is_file() and not ignored.intersection(p.relative_to(root).parts)]
errors=[]
manifest_path=root/'docs/images/manifest.json'
approved_images={}
if manifest_path.is_file():
    manifest=json.loads(manifest_path.read_text(encoding='utf-8'))
    for entry in manifest['screenshots']:
        name=entry['file']
        if Path(name).name!=name or not name.endswith('.png'):
            errors.append('invalid screenshot manifest path')
        else: approved_images['docs/images/'+name]=entry['sha256']
    for name in approved_images:
        if not (root/name).is_file(): errors.append(name+': missing documented screenshot')
for p in files:
    relative=p.relative_to(root).as_posix()
    if relative in approved_images:
        if hashlib.sha256(p.read_bytes()).hexdigest()!=approved_images[relative]: errors.append(relative+': screenshot checksum mismatch')
        continue
    if p.suffix.lower() in {'.apk','.aab','.ab','.db','.img','.jks','.keystore','.pem','.key','.glb','.png','.jpg','.webp'}:
        errors.append(relative+': excluded artifact type')
    if p.name in {'local.properties','google-services.json'} or p.name.startswith('.env'):
        errors.append(relative+': local configuration file')
    if p.stat().st_size>5*1024*1024:
        errors.append(relative+': unexpectedly large file')
    if p.suffix in {'.jar','.so'}: continue
    content=p.read_text(encoding='utf-8',errors='replace')
    for label,pattern in [
        ('private key',r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
        ('GitHub token',r'gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}'),
        ('Google API key',r'AIza[A-Za-z0-9_-]{30,}'),
        ('AWS access key',r'AKIA[A-Z0-9]{16}'),
        ('JWT-shaped token',r'eyJ[A-Za-z0-9_-]{20,}\.eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}'),
        ('private user path',r'(?i)[CD]:[\\/]+Users[\\/]+(?!example|your-user)[\w.-]+')]:
        if re.search(pattern,content): errors.append(relative+': '+label)

profile_path=root/'common/control-profile.json'
profile=json.loads(profile_path.read_text(encoding='utf-8'))
sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
for name,key in [(profile['sourcePath'],'sourceSha256'),
                 ('machine/src/main/cpp/control_profile.h','headerSha256'),
                 ('machine/src/main/jniLibs/armeabi-v7a/libkitchenos_bridge.so','armSha256'),
                 ('machine/src/main/jniLibs/x86_64/libkitchenos_bridge.so','x64Sha256')]:
    if sha(root/name)!=profile[key]: errors.append('controller pin mismatch: '+name)
if profile_path.read_bytes()!=(root/'backend/integrations/control-profile.json').read_bytes():
    errors.append('backend device profile differs')
if sha(profile_path) not in (root/'machine/src/main/java/com/kitchenos/connect/control/ControlProfile.java').read_text(encoding='utf-8'):
    errors.append('generated Java profile is stale')
if "getOrElse('')" not in (root/'machine/build.gradle').read_text():
    errors.append('cloud API must default to disabled')
for name in ['README.md','LICENSE','CONTRIBUTING.md','THIRD_PARTY_NOTICES.md','docs/BUILDING.md','docs/STATUS.md','licenses/Apache-2.0.txt']:
    if not (root/name).is_file(): errors.append('missing documentation: '+name)
if errors:
    print('\n'.join(errors))
    sys.exit(1)
print(f'PASS: {len(files)} publication files; controller source, header, binaries and shared contracts match.')
print('No forbidden artifact types or configured secret patterns found. This is a packaging check, not a full security audit.')
