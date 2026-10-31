#!/usr/bin/env python3
"""Prepare a bounded factory-root ADB development session. No firmware writes.

Standard library only. Dry-run is the default. Neither ID nor STATUS opens UART.
Tokens are confined to captured command input and root/app-private config files.
"""
from __future__ import annotations

import argparse
import base64
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import secrets
import shlex
import struct
import subprocess
import sys
import tempfile
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.kitchenos.connect'
RECEIVER = PACKAGE + '/.control.AccessBootstrapReceiver'
ACTION = PACKAGE + '.CONFIGURE_FACTORY_ACCESS'
BASE_PARENT = '/data/local'
BASE = BASE_PARENT + '/xcr-access'
REFERENCE = {
    'ro.product.device': 'CM0801K_EN',
    'ro.hardware': 'sun50iw10p1',
    'ro.product.board': 'exdroid',
    'ro.build.version.sdk': '29',
    'ro.build.version.incremental': 'eng.songxi.20221008.190443',
    'ro.build.fingerprint': 'Allwinner/CM0801K_EN/CM0801K_EN:10/QP1A.191105.004/eng.songxi.20221008.190443:user/release-keys',
}


class AccessError(Exception):
    """Safe diagnostic: never attach subprocess output, commands or config."""


def digest(data):
    return hashlib.sha256(data).hexdigest()


def require_mutation(apply, acknowledgement, idle):
    if not (apply and acknowledgement and idle):
        raise AccessError('Mutation requires --apply --acknowledge-experimental --confirm-idle.')


def validate_target(serial, props, identity, selinux, *, test_emulator=False):
    if not re.fullmatch(r'[A-Za-z0-9_.:\[\]-]+', serial or ''):
        raise AccessError('An explicit valid ADB serial is required.')
    emulator = bool(re.fullmatch(r'emulator-[0-9]+', serial))
    if test_emulator:
        if not emulator or props.get('ro.kernel.qemu') != '1':
            raise AccessError('Test mode requires an emulator-* serial and ro.kernel.qemu=1.')
        if props.get('ro.build.version.sdk') != '29':
            raise AccessError('The test emulator must run Android SDK 29.')
    elif emulator or any(props.get(k) != v for k, v in REFERENCE.items()):
        raise AccessError('Unsupported device or firmware; the exact C3 reference profile is required.')
    if not re.match(r'^uid=0\(root\)(?:\s|$)', identity.strip()):
        raise AccessError('The selected ADB shell is not UID 0. Factory access is not established.')
    if selinux.strip() != 'Permissive':
        raise AccessError('SELinux compatibility is unverified for this state; no policy will be changed.')
    abi = props.get('ro.product.cpu.abi')
    if abi not in (('armeabi-v7a', 'x86_64') if test_emulator else ('armeabi-v7a',)):
        raise AccessError('Unsupported native ABI.')
    return abi


def check_upgrade(version, signer, installed_version, installed_signer):
    if installed_version is None:
        return
    if signer != installed_signer:
        raise AccessError('Installed application signature differs. Preserve the existing app and data.')
    if version < installed_version:
        raise AccessError('APK downgrade rejected. Preserve the existing app and data.')


def require_install_success(output):
    lines = output.strip().splitlines()
    if not lines or lines[-1].strip() != 'Success' or any('Failure [' in line for line in lines):
        raise AccessError('PackageManager did not confirm installation; existing data must be preserved.')


def session_path(session):
    if not re.fullmatch(r'[0-9a-f]{16}', session or ''):
        raise AccessError('Session must contain exactly 16 lowercase hexadecimal characters.')
    return BASE + '/' + session


def parse_config(text, *, now=None, allow_expired=False):
    if len(text) > 4096 or '\x00' in text:
        raise AccessError('Invalid root-private session configuration.')
    result = {}
    for line in text.splitlines():
        key, sep, value = line.partition('=')
        if not sep or key in result or not value:
            raise AccessError('Invalid root-private session configuration.')
        result[key] = value
    if set(result) != {'version','uid','socket','token','bridge','expires'}:
        raise AccessError('Invalid root-private session configuration.')
    match = re.fullmatch(r'xcr\.access\.([1-9][0-9]{4})\.([0-9a-f]{16})', result['socket'])
    if (result['version'] != '1' or not match or match[1] != result['uid']
            or not re.fullmatch(r'[0-9a-f]{64}', result['token'])
            or result['bridge'] != session_path(match[2]) + '/bridge'
            or not re.fullmatch(r'[1-9][0-9]{0,11}', result['expires'])):
        raise AccessError('Invalid root-private session configuration.')
    if now is None: now = int(time.time())
    if not allow_expired and not now < int(result['expires']) <= now + 14400:
        raise AccessError('Session expired or its lease exceeds four hours. Prepare a new session.')
    return result


def broadcast_result(output):
    results = re.findall(r'(?m)^Broadcast completed: result=(-?[0-9]+)(?:,.*)?$', output.strip())
    if len(results) != 1: raise AccessError('The app broadcast result was missing or ambiguous.')
    return int(results[0])


def require_broadcast_success(output):
    result = broadcast_result(output)
    if result != 200:
        if result == 409:
            raise AccessError('The app reports active or uncertain control. Confirm physical idle before retrying.')
        raise AccessError('The app did not confirm factory access. No successful provisioning is claimed.')


def check_ready(output, socket, expires):
    expected = r'XCR_ACCESS_READY pid=[1-9][0-9]* socket=' + re.escape(socket) + r' expires=' + str(expires)
    if not re.fullmatch(expected, output.strip()):
        raise AccessError('Broker readiness was not confirmed. No working session is claimed.')


class Adb:
    def __init__(self, executable, serial, *, runner=subprocess.run):
        if not serial or not re.fullmatch(r'[A-Za-z0-9_.:\[\]-]+', serial):
            raise AccessError('An explicit valid ADB serial is required.')
        self.executable, self.serial, self.runner = str(executable), serial, runner

    def run(self, *args, input=None, allow_failure=False, timeout=45):
        try:
            result = self.runner([self.executable, '-s', self.serial, *map(str,args)],
                                 input=input, capture_output=True, timeout=timeout, check=False)
        except (OSError, subprocess.SubprocessError):
            raise AccessError('ADB invocation failed or timed out; inspect the selected connection.') from None
        if result.returncode and not allow_failure:
            raise AccessError('ADB operation failed; no success is claimed. Device output was withheld.')
        return result.stdout

    def shell(self, *args, **kwargs):
        return self.run('shell', 'sh', '-c', shlex.quote(shlex.join(map(str,args))), **kwargs).decode('utf-8', 'replace').strip()

    def script(self, script, *, input=None):
        return self.run('shell', 'sh', '-c', shlex.quote(script), input=input).decode('utf-8','replace').strip()


def private_temp_directory(path):
    """Constrain secret staging to the current Windows user/System or POSIX owner."""
    path=Path(path)
    if path.is_symlink() or not path.is_dir(): raise AccessError('Private host staging directory is unavailable.')
    if os.name != 'nt':
        path.chmod(0o700)
        return
    import ctypes
    from ctypes import wintypes as w
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    security=ctypes.WinDLL('advapi32',use_last_error=True)
    kernel.GetCurrentProcess.restype=w.HANDLE
    kernel.CloseHandle.argtypes=[w.HANDLE]
    kernel.LocalFree.argtypes=[w.HLOCAL]; kernel.LocalFree.restype=w.HLOCAL
    security.OpenProcessToken.argtypes=[w.HANDLE,w.DWORD,ctypes.POINTER(w.HANDLE)]
    security.GetTokenInformation.argtypes=[w.HANDLE,w.DWORD,ctypes.c_void_p,w.DWORD,ctypes.POINTER(w.DWORD)]
    security.ConvertSidToStringSidW.argtypes=[ctypes.c_void_p,ctypes.POINTER(w.LPWSTR)]
    security.ConvertStringSecurityDescriptorToSecurityDescriptorW.argtypes=[w.LPCWSTR,w.DWORD,ctypes.POINTER(ctypes.c_void_p),ctypes.POINTER(w.DWORD)]
    security.SetFileSecurityW.argtypes=[w.LPCWSTR,w.DWORD,ctypes.c_void_p]
    token=w.HANDLE(); sid_text=w.LPWSTR(); descriptor=ctypes.c_void_p()
    try:
        if not security.OpenProcessToken(kernel.GetCurrentProcess(),8,ctypes.byref(token)): raise AccessError('Cannot secure host staging directory.')
        needed=w.DWORD()
        security.GetTokenInformation(token,1,None,0,ctypes.byref(needed))
        if not needed.value: raise AccessError('Cannot secure host staging directory.')
        info=ctypes.create_string_buffer(needed.value)
        if not security.GetTokenInformation(token,1,info,needed.value,ctypes.byref(needed)): raise AccessError('Cannot secure host staging directory.')
        sid=ctypes.cast(info,ctypes.POINTER(ctypes.c_void_p))[0]
        if not security.ConvertSidToStringSidW(sid,ctypes.byref(sid_text)): raise AccessError('Cannot secure host staging directory.')
        sddl='D:P(A;OICI;FA;;;SY)(A;OICI;FA;;;'+sid_text.value+')'
        if not security.ConvertStringSecurityDescriptorToSecurityDescriptorW(sddl,1,ctypes.byref(descriptor),None): raise AccessError('Cannot secure host staging directory.')
        if not security.SetFileSecurityW(str(path),0x80000004,descriptor): raise AccessError('Cannot secure host staging directory.')
    finally:
        if descriptor: kernel.LocalFree(descriptor)
        if sid_text: kernel.LocalFree(ctypes.cast(sid_text,w.HLOCAL))
        if token: kernel.CloseHandle(token)


@dataclass
class Apk:
    path: Path
    version: int
    signer: str
    sha256: str
    access: bytes
    bridge: bytes


class Sdk:
    def __init__(self, sdk, java, build_tools='34.0.0'):
        self.tools = Path(sdk) / 'build-tools' / build_tools
        self.java = str(java)

    def _run(self, args):
        try:
            result = subprocess.run(args, capture_output=True, timeout=60, check=False)
        except (OSError, subprocess.SubprocessError):
            raise AccessError('Android SDK verification tool could not run.') from None
        if result.returncode:
            raise AccessError('Android SDK APK verification failed. Tool output was withheld.')
        return result.stdout.decode('utf-8','replace')

    def metadata(self, path):
        signature = self._run([self.java, '-jar', str(self.tools/'lib/apksigner.jar'), 'verify', '--verbose', '--print-certs', str(path)])
        signers = re.findall(r'Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})', signature)
        if len(signers) != 1:
            raise AccessError('Exactly one verified APK signer is required.')
        aapt = self.tools / ('aapt2.exe' if os.name == 'nt' else 'aapt2')
        badging = self._run([str(aapt), 'dump', 'badging', str(path)])
        package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)'", badging, re.M)
        minimum = re.search(r"^sdkVersion:'([0-9]+)'", badging, re.M)
        if not package or package[1] != PACKAGE or not 1 <= int(package[2]) <= 2147483647 or not minimum or int(minimum[1]) > 29:
            raise AccessError('APK package, version or minimum Android version is incompatible.')
        return int(package[2]), signers[0].lower()

    def apk(self, path, abi):
        path = Path(path).resolve()
        if not path.is_file() or path.stat().st_size > 268435456:
            raise AccessError('A local owner-built APK of bounded size is required.')
        version, signer = self.metadata(path)
        try:
            with zipfile.ZipFile(path) as archive:
                files = []
                for leaf in ('libcooking_robot_access.so', 'libkitchenos_bridge.so'):
                    name = 'lib/' + abi + '/' + leaf
                    if archive.namelist().count(name) != 1 or not 20 <= archive.getinfo(name).file_size <= 16777216:
                        raise AccessError('APK must include exactly one bounded broker and bridge for the selected ABI.')
                    data = archive.read(name)
                    elf_class, machine = (1,40) if abi == 'armeabi-v7a' else (2,62)
                    if data[:6] != b'\x7fELF' + bytes([elf_class,1]) or struct.unpack_from('<H',data,18)[0] != machine:
                        raise AccessError('APK native artifact architecture is incompatible.')
                    files.append(data)
        except (OSError, zipfile.BadZipFile, KeyError):
            raise AccessError('Could not verify the APK native artifacts.') from None
        profile = json.loads((ROOT/'common/control-profile.json').read_text(encoding='utf-8'))
        if digest(files[1]) != profile['armSha256' if abi == 'armeabi-v7a' else 'x64Sha256']:
            raise AccessError('APK bridge does not match the repository controller contract.')
        return Apk(path,version,signer,digest(path.read_bytes()),*files)


class Installer:
    def __init__(self, adb, sdk, *, test_emulator=False, clock=time.time):
        self.adb, self.sdk, self.test_emulator, self.clock = adb, sdk, test_emulator, clock

    def inspect(self):
        if self.adb.run('get-state').decode().strip() != 'device':
            raise AccessError('The selected target is not authorized and ready.')
        props = {k:self.adb.shell('getprop',k) for k in [*REFERENCE,'ro.product.cpu.abi','ro.kernel.qemu']}
        abi = validate_target(self.adb.serial,props,self.adb.shell('id'),self.adb.shell('getenforce'),test_emulator=self.test_emulator)
        return {'serial':self.adb.serial,'abi':abi,'sdk':29,'factoryRootAdb':True,
                'testEmulator':self.test_emulator,'physicalValidation':False,'persistentRoot':False}

    def installed(self, *, metadata=True):
        uid_output = self.adb.shell('cmd','package','list','packages','--user','0','-U',PACKAGE)
        matches = re.findall(r'(?m)^package:'+re.escape(PACKAGE)+r' uid:([0-9]+)\s*$',uid_output)
        if not matches: return None
        if len(matches) != 1 or not 10000 <= int(matches[0]) < 100000:
            raise AccessError('The installed user-0 application UID is ambiguous.')
        output = self.adb.shell('pm','path','--user','0',PACKAGE)
        lines = output.splitlines()
        if len(lines) != 1 or not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~-]+/base\.apk',lines[0]):
            raise AccessError('Installed APK path is unsupported or ambiguous.')
        data = self.adb.run('exec-out','cat',lines[0][8:])
        if not data or len(data) > 268435456:
            raise AccessError('Installed APK readback is invalid.')
        apk_path=lines[0][8:]
        if any(part in ('.','..') for part in apk_path.split('/')): raise AccessError('Installed APK path is unsafe.')
        info = {'uid':int(matches[0]),'sha256':digest(data),'apkPath':apk_path}
        if metadata:
            with tempfile.TemporaryDirectory(prefix='xcr-apk-verify-') as folder:
                path = Path(folder)/'installed.apk'; path.write_bytes(data)
                info['version'],info['signer'] = self.sdk.metadata(path)
        return info

    def prepare(self, apk_path):
        report = self.inspect()
        apk = self.sdk.apk(apk_path,report['abi'])
        installed = self.installed()
        check_upgrade(apk.version,apk.signer,installed['version'] if installed else None,installed['signer'] if installed else None)
        report.update({'apkSha256':apk.sha256,'apkVersion':apk.version,'apkSignerSha256':apk.signer,
                       'accessSha256':digest(apk.access),'bridgeSha256':digest(apk.bridge),
                       'existingInstallation':installed is not None,'dryRun':True})
        return report,apk

    def _stat(self, path, mode, kind):
        value = self.adb.shell('stat','-c','%u:%g:%a:%F',path)
        if value != '0:0:'+mode+':'+kind:
            raise AccessError('Root-private path ownership, mode or file type is incompatible.')

    def _trusted(self, path, kind, *, private_parent=False, executable=False):
        value=self.adb.shell('stat','-c','%u:%g:%a:%F',path)
        match=re.fullmatch(r'([0-9]+):([0-9]+):([0-7]{3,4}):'+re.escape(kind),value)
        if not match: raise AccessError('Trusted path is missing, a symlink or has an incompatible file type.')
        uid,gid,mode=int(match[1]),int(match[2]),int(match[3],8)
        if private_parent:
            ok=uid==0 and gid==0 and not mode & 0o022
        else:
            # PackageManager's /data/app ancestors may be system:system 0771.
            # Only trusted root/system can rename these paths; apps cannot.
            ok=uid in (0,1000) and gid in (0,1000) and not mode & 0o002
            if kind=='regular file': ok=ok and not mode & 0o022
        if not ok or executable and not mode & 0o111:
            raise AccessError('Trusted path is writable by an untrusted identity or cannot execute.')

    def _parent(self):
        self._trusted('/data','directory')
        self._trusted(BASE_PARENT,'directory',private_parent=True)

    def _broker(self, installed, abi, expected):
        library='arm' if abi=='armeabi-v7a' else 'x86_64'
        path=str(PurePosixPath(installed['apkPath']).parent/'lib'/library/'libcooking_robot_access.so')
        parts=PurePosixPath(path).parts
        for count in range(2,len(parts)):
            self._trusted(str(PurePosixPath(*parts[:count])),'directory')
        self._trusted(path,'regular file',executable=True)
        if self._hash(path)!=expected: raise AccessError('Installed broker readback differs from the verified APK.')
        return path

    def _private_tree(self, directory):
        self._parent()
        self._stat(BASE,'700','directory')
        self._stat(directory,'700','directory')

    def _broadcast(self, *extras, require_success=True):
        output = self.adb.shell('am','broadcast','--user','0','--receiver-foreground','-a',ACTION,'-n',RECEIVER,*extras)
        if require_success: require_broadcast_success(output)
        return broadcast_result(output)

    def _now(self):
        value = self.adb.shell('date','+%s')
        if not re.fullmatch(r'[1-9][0-9]{0,11}',value): raise AccessError('Selected device Unix clock is unavailable.')
        return int(value)

    def _config(self, session, *, allow_expired=False):
        directory = session_path(session)
        self._private_tree(directory)
        self._stat(directory+'/config','600','regular file')
        config = parse_config(self.adb.shell('cat',directory+'/config'),now=self._now(),allow_expired=allow_expired)
        if config['bridge'] != directory+'/bridge':
            raise AccessError('Root configuration belongs to another session.')
        return config

    def _hash(self, path):
        value = self.adb.shell('sha256sum',path)
        match = re.fullmatch(r'([0-9a-f]{64})\s+[^\r\n]+',value)
        if not match: raise AccessError('Native artifact readback could not be verified.')
        return match[1]

    def apply(self, apk_path, *, apply=False, acknowledgement=False, idle=False, lease=3600, record=None):
        require_mutation(apply,acknowledgement,idle)
        if not 60 <= lease <= 14400: raise AccessError('Lease must be between 60 and 14400 seconds.')
        report,apk = self.prepare(apk_path)
        self._parent()
        # Recheck exact bytes immediately before normal PackageManager installation.
        if digest(apk.path.read_bytes()) != apk.sha256: raise AccessError('Local APK changed after verification.')
        existing=self.installed()
        check_upgrade(apk.version,apk.signer,existing['version'] if existing else None,existing['signer'] if existing else None)
        if existing is None or existing['sha256'] != apk.sha256:
            if existing is not None:
                # A normal package update restarts its process. Require the old app's
                # active/uncertain-session guard before allowing that restart.
                self._broadcast('--ez','clear','true')
            require_install_success(self.adb.run('install','--no-streaming','--user','0','-r',apk.path,timeout=120).decode('utf-8','replace'))
        installed = self.installed()
        if not installed or installed['sha256'] != apk.sha256 or installed['version'] != apk.version or installed['signer'] != apk.signer:
            raise AccessError('Installed APK readback differs from the verified owner APK.')
        broker=self._broker(installed,report['abi'],digest(apk.access))
        uid = installed['uid']; session = secrets.token_hex(8); directory = session_path(session)
        expires = self._now() + lease
        socket = 'xcr.access.'+str(uid)+'.'+session
        token = secrets.token_hex(32)
        report.update({'dryRun':False,'session':session,'uid':uid,'socket':socket,'expiresAtSeconds':expires,
                       'appAuthorizationVerified':False,'opensUart':False})
        if record is not None: record(report)
        # Never chmod an existing base into acceptance. An insecure/symlink base is rejected.
        self.adb.script('umask 077; if [ ! -e '+BASE+' ] && [ ! -L '+BASE+' ]; then mkdir '+BASE+' || exit 1; fi')
        self._stat(BASE,'700','directory')
        self.adb.shell('mkdir','-m','700',directory)
        self._private_tree(directory)
        config = ('version=1\nuid='+str(uid)+'\nsocket='+socket+'\ntoken='+token+
                  '\nbridge='+directory+'/bridge\nexpires='+str(expires)+'\n').encode()
        with tempfile.TemporaryDirectory(prefix='xcr-private-stage-') as folder:
            private_temp_directory(folder)
            for name,data,mode in [('bridge',apk.bridge,'700'),('config',config,'600')]:
                path = directory+'/'+name
                local=Path(folder)/name
                with local.open('xb') as stream: stream.write(data)
                if os.name!='nt': local.chmod(0o600)
                # adb push uses its binary sync protocol, unlike Windows adb shell
                # stdin, which can truncate ELF bytes at the first Ctrl-Z.
                self.adb.run('push',local,path)
                self.adb.shell('chmod',mode,path)
                self._stat(path,mode,'regular file')
                if self._hash(path) != digest(data): raise AccessError('Staged artifact readback differs.')
        # Revalidate the immutable PackageManager path immediately before execution.
        broker=self._broker(installed,report['abi'],digest(apk.access))
        output = self.adb.shell(broker,'--config',directory+'/config','--detach')
        check_ready(output,socket,expires)
        app_config = {'schemaVersion':1,'socket':socket,'token':token,'expiresAtSeconds':expires}
        encoded = base64.b64encode(json.dumps(app_config,separators=(',',':')).encode()).decode('ascii')
        try:
            self._broadcast('--es','config',encoded)
        except AccessError:
            # A lost ADB reply can follow a successful write. Use the same guarded,
            # expected-socket revocation as explicit stop; never kill an active run.
            try: self.stop(report,apply=True,acknowledgement=True,idle=True)
            except AccessError: pass
            raise AccessError('App provisioning was not confirmed. Session record retained; guarded cleanup may require leaving the control screen and retrying stop. No working session is claimed.') from None
        report['appAuthorizationVerified']=True
        if record is not None: record(report)
        self.verify(report)
        return report

    def _integrity(self, state, *, allow_expired=False):
        self.inspect()
        if state.get('serial') != self.adb.serial: raise AccessError('Session record belongs to another ADB target.')
        session = state.get('session'); directory = session_path(session)
        config = self._config(session,allow_expired=allow_expired)
        installed = self.installed()
        if not installed or installed['uid'] != state.get('uid') or config['uid'] != str(installed['uid']):
            raise AccessError('Installed application UID no longer matches session authorization.')
        for key in ('sha256','version','signer'):
            state_key = {'sha256':'apkSha256','version':'apkVersion','signer':'apkSignerSha256'}[key]
            if installed[key] != state.get(state_key): raise AccessError('Installed application changed; prepare a new session.')
        broker=self._broker(installed,state['abi'],state['accessSha256'])
        for name,key in [('bridge','bridgeSha256')]:
            self._stat(directory+'/'+name,'700','regular file')
            if self._hash(directory+'/'+name) != state.get(key): raise AccessError('Session native artifact changed.')
        if config['socket'] != state.get('socket') or int(config['expires']) != state.get('expiresAtSeconds'):
            raise AccessError('Session record and root configuration disagree.')
        return config,broker

    def verify(self, state):
        config,broker = self._integrity(state)
        session=state['session']; directory=session_path(session)
        status = self.adb.shell(broker,'--status','--config',directory+'/config')
        if status != 'XCR1 OK STATUS uid=0 expires='+config['expires']:
            raise AccessError('Root-only backend STATUS did not confirm an active lease.')
        self._broadcast('--ez','status','true','--es','socket',config['socket'])
        return {'serial':self.adb.serial,'session':session,'verified':True,'appAuthorizationVerified':True,
                'opensUart':False,'physicalIdleVerified':False,'persistentRoot':False}

    def stop(self, state, *, apply=False, acknowledgement=False, idle=False):
        require_mutation(apply,acknowledgement,idle)
        config,broker=self._integrity(state,allow_expired=True)
        directory = session_path(state['session'])
        # App guard must allow revocation before any broker shutdown is requested.
        if state.get('appAuthorizationVerified') is True:
            self._broadcast('--ez','clear','true','--es','socket',config['socket'])
        else:
            # A configuration broadcast may have succeeded before its reply was
            # lost. A matching config still needs the idle guard before shutdown.
            code=self._broadcast('--ez','status','true','--es','socket',config['socket'],require_success=False)
            if code in (200,503):
                self._broadcast('--ez','clear','true','--es','socket',config['socket'])
            elif code not in (404,409):
                raise AccessError('App configuration state is unresolved; backend shutdown was withheld.')
        result=self.adb.shell(broker,'--stop','--config',directory+'/config')
        if result not in ('XCR1 OK SHUTDOWN uid=0 expires='+config['expires'],
                          'XCR_ACCESS_ALREADY_STOPPED socket='+config['socket']):
            raise AccessError('Backend shutdown was not confirmed. Session record retained.')
        self.adb.shell('rm','-f',directory+'/config')
        return {'serial':self.adb.serial,'session':state['session'],'revocationRequested':True,
                'physicalIdleVerified':False,'persistentRoot':False,'remainingFiles':'Root-private executable artifacts retained.'}


STATE_KEYS = {'serial','abi','sdk','factoryRootAdb','testEmulator','physicalValidation','persistentRoot',
              'apkSha256','apkVersion','apkSignerSha256','accessSha256','bridgeSha256','existingInstallation',
              'dryRun','session','uid','socket','expiresAtSeconds','appAuthorizationVerified','opensUart'}


def read_state(path):
    try:
        def unique(pairs):
            result={}
            for key,value in pairs:
                if key in result: raise ValueError()
                result[key]=value
            return result
        if Path(path).stat().st_size > 65536: raise ValueError()
        data = json.loads(Path(path).read_text(encoding='utf-8'),object_pairs_hook=unique)
        if not isinstance(data,dict) or set(data) != STATE_KEYS: raise ValueError()
        session_path(data['session'])
        for key in ('factoryRootAdb','testEmulator','physicalValidation','persistentRoot','existingInstallation','dryRun','appAuthorizationVerified','opensUart'):
            if type(data[key]) is not bool: raise ValueError()
        if not data['factoryRootAdb'] or any(data[k] for k in ('dryRun','persistentRoot','physicalValidation','opensUart')): raise ValueError()
        for key in ('uid','sdk','apkVersion','expiresAtSeconds'):
            if type(data[key]) is not int: raise ValueError()
        if not 10000 <= data['uid'] < 100000 or data['sdk'] != 29 or not 1 <= data['apkVersion'] <= 2147483647 or data['expiresAtSeconds'] < 1: raise ValueError()
        if data['socket'] != 'xcr.access.'+str(data['uid'])+'.'+data['session']: raise ValueError()
        if data['abi'] not in (('armeabi-v7a','x86_64') if data['testEmulator'] else ('armeabi-v7a',)): raise ValueError()
        if not isinstance(data['serial'],str) or not re.fullmatch(r'[A-Za-z0-9_.:\[\]-]+',data['serial']): raise ValueError()
        for key in ('apkSha256','apkSignerSha256','accessSha256','bridgeSha256'):
            if not isinstance(data[key],str) or not re.fullmatch(r'[0-9a-f]{64}',data[key]): raise ValueError()
        return data
    except (OSError, ValueError, TypeError):
        raise AccessError('Invalid public session record. Use the record produced by apply.') from None


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['inspect','prepare','apply','verify','stop'])
    parser.add_argument('--adb',required=True,type=Path)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--sdk',type=Path)
    parser.add_argument('--java',type=Path)
    parser.add_argument('--build-tools',default='34.0.0')
    parser.add_argument('--apk',type=Path)
    parser.add_argument('--state',type=Path,help='Token-free session record (new output for apply; input for verify/stop).')
    parser.add_argument('--lease-seconds',type=int,default=3600)
    parser.add_argument('--apply',action='store_true',help='Permit explicit package/config/session mutations.')
    parser.add_argument('--acknowledge-experimental',action='store_true')
    parser.add_argument('--confirm-idle',action='store_true')
    parser.add_argument('--test-emulator',action='store_true',help='Only emulator-* plus qemu=1; never relax a physical device.')
    args = parser.parse_args(argv)
    try:
        if args.command != 'inspect' and (args.sdk is None or args.java is None):
            raise AccessError('--sdk and --java are required for APK verification.')
        installer = Installer(Adb(args.adb,args.serial),Sdk(args.sdk,args.java,args.build_tools) if args.sdk else None,test_emulator=args.test_emulator)
        if args.command == 'inspect': result = installer.inspect()
        elif args.command in ('prepare','apply'):
            if args.apk is None: raise AccessError('--apk is required.')
            if args.command == 'prepare' or not args.apply:
                result,_ = installer.prepare(args.apk)
                result['nextStep'] = 'Apply requires a new --state path and all three mutation acknowledgements.'
            else:
                require_mutation(args.apply,args.acknowledge_experimental,args.confirm_idle)
                if args.state is None or args.state.exists(): raise AccessError('Use a new --state output file; existing records are preserved.')
                # Reserve a new record before mutations; preserve failure as a visible checkpoint.
                with args.state.open('x',encoding='utf-8') as stream:
                    def record(value):
                        stream.seek(0); stream.truncate(); stream.write(json.dumps(value,indent=2)+'\n'); stream.flush(); os.fsync(stream.fileno())
                    result = installer.apply(args.apk,apply=args.apply,acknowledgement=args.acknowledge_experimental,idle=args.confirm_idle,lease=args.lease_seconds,record=record)
        else:
            if args.state is None: raise AccessError('--state is required.')
            state=read_state(args.state)
            if state['serial'] != args.serial: raise AccessError('Session record belongs to another ADB target.')
            if bool(state.get('testEmulator')) != args.test_emulator: raise AccessError('Explicit emulator mode must match the session record.')
            if args.command == 'verify': result=installer.verify(state)
            elif not args.apply:
                result={'dryRun':True,'session':state['session'],'nextStep':'Stop requires all three mutation acknowledgements.'}
            else: result=installer.stop(state,apply=args.apply,acknowledgement=args.acknowledge_experimental,idle=args.confirm_idle)
        print(json.dumps(result,indent=2))
        return 0
    except AccessError as error:
        print('ERROR: '+str(error),file=sys.stderr); return 2
    except (OSError, ValueError, KeyError, zipfile.BadZipFile):
        print('ERROR: Local input or session verification failed. Sensitive details were withheld.',file=sys.stderr); return 2


if __name__ == '__main__': raise SystemExit(main())
