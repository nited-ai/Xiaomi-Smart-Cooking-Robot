"""Host policy tests. Fake ADB only: no device, root shell or UART access."""
import importlib.util
import base64
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

SOURCE = Path(__file__).resolve().parents[1] / 'factory_access.py'
spec = importlib.util.spec_from_file_location('factory_access', SOURCE)
fa = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = fa
spec.loader.exec_module(fa)


def properties():
    return dict(fa.REFERENCE, **{'ro.product.cpu.abi': 'armeabi-v7a', 'ro.kernel.qemu': '0'})


class PolicyTests(unittest.TestCase):
    def test_reference_root_is_accepted(self):
        self.assertEqual(fa.validate_target('192.0.2.1:5555', properties(), 'uid=0(root) gid=0(root)', 'Permissive'), 'armeabi-v7a')

    def test_every_reference_field_is_required(self):
        for name in fa.REFERENCE:
            values = properties(); values[name] = 'different'
            with self.subTest(name=name), self.assertRaises(fa.AccessError):
                fa.validate_target('robot:5555', values, 'uid=0(root)', 'Permissive')

    def test_shell_root_text_must_be_actual_identity(self):
        for identity in ['uid=2000(shell)', 'error uid=0(root)', '', 'uid=0(rooted)']:
            with self.subTest(identity=identity), self.assertRaises(fa.AccessError):
                fa.validate_target('robot:5555', properties(), identity, 'Permissive')

    def test_enforcing_policy_is_not_silently_changed(self):
        with self.assertRaisesRegex(fa.AccessError, 'SELinux'):
            fa.validate_target('robot:5555', properties(), 'uid=0(root)', 'Enforcing')

    def test_emulator_flag_cannot_relax_a_physical_target(self):
        for serial, qemu in [('robot:5555', '1'), ('emulator-5554', '0')]:
            values = properties(); values['ro.kernel.qemu'] = qemu
            with self.subTest(serial=serial), self.assertRaises(fa.AccessError):
                fa.validate_target(serial, values, 'uid=0(root)', 'Permissive', test_emulator=True)

    def test_only_explicit_verified_emulator_accepts_x64(self):
        values = properties(); values.update({'ro.product.device':'generic_x86_64', 'ro.product.cpu.abi':'x86_64', 'ro.kernel.qemu':'1'})
        self.assertEqual(fa.validate_target('emulator-5554', values, 'uid=0(root)', 'Permissive', test_emulator=True), 'x86_64')
        with self.assertRaises(fa.AccessError):
            fa.validate_target('emulator-5554', values, 'uid=0(root)', 'Permissive')

    def test_signature_conflict_and_downgrade_are_rejected(self):
        for installed_version, installed_signer in [(192, 'a'*64), (190, 'b'*64)]:
            with self.subTest(version=installed_version), self.assertRaises(fa.AccessError):
                fa.check_upgrade(191, 'a'*64, installed_version, installed_signer)

    def test_matching_upgrade_or_fresh_install_is_allowed(self):
        fa.check_upgrade(191, 'a'*64, 190, 'a'*64)
        fa.check_upgrade(191, 'a'*64, None, None)

    def test_install_accepts_adb_progress_only_with_final_success(self):
        fa.require_install_success('Performing Push Install\n1 file pushed\nSuccess\n')
        for output in ['Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]', 'Success\nFailure [ERROR]', 'No install result', 'Success-ish']:
            with self.subTest(output=output), self.assertRaises(fa.AccessError):
                fa.require_install_success(output)

    def test_mutation_needs_all_three_acknowledgements(self):
        for bits in [(False,True,True), (True,False,True), (True,True,False)]:
            with self.subTest(bits=bits), self.assertRaises(fa.AccessError):
                fa.require_mutation(*bits)
        fa.require_mutation(True, True, True)

    def test_broadcast_result_must_be_exact_success(self):
        fa.require_broadcast_success('Broadcast completed: result=200\n')
        for output in ['Broadcast completed: result=409', 'Broadcast completed: result=400', 'Broadcast completed: result=0', 'result=2000', 'result=200\nresult=409']:
            with self.subTest(output=output), self.assertRaises(fa.AccessError):
                fa.require_broadcast_success(output)

    def test_expired_configuration_can_only_be_loaded_for_revocation(self):
        valid='version=1\nuid=10001\nsocket=xcr.access.10001.0123456789abcdef\ntoken='+'a'*64+'\nbridge=/data/local/xcr-access/0123456789abcdef/bridge\nexpires=18000\n'
        with self.assertRaises(fa.AccessError): fa.parse_config(valid,now=19000)
        self.assertEqual(fa.parse_config(valid,now=19000,allow_expired=True)['expires'],'18000')

    def test_config_rejects_duplicate_keys_and_noncanonical_fields(self):
        valid = 'version=1\nuid=10001\nsocket=xcr.access.10001.0123456789abcdef\ntoken='+'a'*64+'\nbridge=/data/local/xcr-access/0123456789abcdef/bridge\nexpires=20000\n'
        self.assertEqual(fa.parse_config(valid, now=19000)['uid'], '10001')
        for invalid in [valid+'uid=10001\n', valid.replace('token='+'a'*64, 'token=BAD'), valid.replace('/bridge', '/../bridge'), valid.replace('expires=20000', 'expires=18000')]:
            with self.subTest(kind=invalid[-20:]), self.assertRaises(fa.AccessError):
                fa.parse_config(invalid, now=19000)

    def test_invalid_session_cannot_become_shell_path(self):
        for value in ['../escape', 'a;id', 'A'*16, '', 'a'*17]:
            with self.subTest(value=value), self.assertRaises(fa.AccessError): fa.session_path(value)


class AdbTests(unittest.TestCase):
    def test_every_adb_command_selects_explicit_target(self):
        calls=[]
        def runner(command, **kwargs):
            calls.append(command); return subprocess.CompletedProcess(command,0,b'device\n',b'')
        adb=fa.Adb('adb.exe','robot:5555', runner=runner)
        self.assertEqual(adb.run('get-state'), b'device\n')
        self.assertEqual(calls, [['adb.exe','-s','robot:5555','get-state']])

    def test_remote_shell_quoting_preserves_a_single_command(self):
        calls=[]
        def runner(command, **kwargs):
            calls.append(command); return subprocess.CompletedProcess(command,0,b'',b'')
        adb=fa.Adb('adb','robot:5555',runner=runner)
        adb.shell('stat','-c','%u:%a:%F','/data/local/xcr-access')
        import shlex
        remote=shlex.split(' '.join(calls[-1][4:]))
        self.assertEqual(remote[:2],['sh','-c'])
        self.assertEqual(shlex.split(remote[2]),['stat','-c','%u:%a:%F','/data/local/xcr-access'])

    def test_adb_failures_never_echo_secret_arguments_or_output(self):
        secret='a'*64
        def runner(command, **kwargs):
            return subprocess.CompletedProcess(command,1,secret.encode(),secret.encode())
        adb=fa.Adb('adb','robot:5555',runner=runner)
        with self.assertRaises(fa.AccessError) as caught: adb.run('shell','am','broadcast','--es','config',secret)
        self.assertNotIn(secret,str(caught.exception))

    def test_readiness_output_has_no_secret_and_is_checked(self):
        fa.check_ready('XCR_ACCESS_READY pid=99 socket=xcr.access.10001.0123456789abcdef expires=20000', 'xcr.access.10001.0123456789abcdef',20000)
        with self.assertRaises(fa.AccessError):
            fa.check_ready('XCR_ACCESS_READY pid=99 socket=wrong expires=20000','xcr.access.10001.0123456789abcdef',20000)


class FakeSdk:
    def __init__(self, apk_path): self.path=apk_path; self.bridge=b'bridge'
    def metadata(self,path):
        body=Path(path).read_bytes()
        return {'owner-apk':(191,'a'*64),'older-apk':(190,'a'*64),'other-apk':(190,'b'*64)}[body.decode()]
    def apk(self,path,abi):
        version,signer=self.metadata(path)
        return fa.Apk(Path(path),version,signer,fa.digest(Path(path).read_bytes()),b'broker',self.bridge)


class FakeAdb:
    serial='robot:5555'
    def __init__(self):
        self.calls=[]; self.files={}; self.modes={}; self.dirs={
            '/data':'1000:771:directory','/data/local':'0:751:directory',
            '/data/app':'1000:771:directory','/data/app/robot':'1000:755:directory',
            '/data/app/robot/lib':'1000:755:directory','/data/app/robot/lib/arm':'1000:755:directory',
        }; self.apk=None
        self.clock=19000; self.live=False; self.config_result=200; self.clear_result=200
        self.app_config=None; self.tamper=False
        self.config_reply_lost=False
        self.broker=b'broker'
    def run(self,*args,**kwargs):
        self.calls.append(('run',args))
        if args==('get-state',): return b'device\n'
        if args[:1]==('install',): self.apk=Path(args[-1]).read_bytes(); return b'Success\n'
        if args[:2]==('exec-out','cat'): return self.apk
        if args[:1]==('push',):
            self.files[args[-1]]=Path(args[-2]).read_bytes(); self.modes[args[-1]]='644'; return b'1 file pushed\n'
        raise AssertionError(('unexpected ADB',args))
    def shell(self,*args,**kwargs):
        self.calls.append(('shell',args))
        if args[:1]==('getprop',): return properties()[args[1]]
        if args==('id',): return 'uid=0(root) gid=0(root)'
        if args==('getenforce',): return 'Permissive'
        if args==('date','+%s'): return str(self.clock)
        if args[:1]==('cat',): return self.files[args[-1]].decode()
        if args[:4]==('cmd','package','list','packages'): return 'package:com.kitchenos.connect uid:10001' if self.apk else ''
        if args[:2]==('pm','path'): return 'package:/data/app/robot/base.apk' if self.apk else ''
        if args[:1]==('stat',):
            path=args[-1]
            if path in self.dirs: value=self.dirs[path]
            elif path.endswith('/libcooking_robot_access.so'): value='1000:755:regular file'
            else: value='0:'+self.modes[path]+':regular file'
            if '%g' in args[2]:
                fields=value.split(':')
                if len(fields)==4: return value
                uid,mode,kind=fields; return uid+':'+uid+':'+mode+':'+kind
            return value
        if args[:1]==('mkdir',): self.dirs[args[-1]]='0:700:directory'; return ''
        if args[:1]==('chmod',): self.modes[args[-1]]=args[1]; return ''
        if args[:1]==('sha256sum',):
            data=(self.broker if args[-1].endswith('/libcooking_robot_access.so') else self.files[args[-1]])+(b'tampered' if self.tamper and args[-1].endswith('/bridge') else b'')
            return fa.digest(data)+'  '+args[-1]
        if args[0].endswith(('/access','/libcooking_robot_access.so')):
            config=fa.parse_config(self.files[args[args.index('--config')+1]].decode(),now=self.clock,allow_expired='--stop' in args)
            if '--detach' in args:
                self.live=True
                return 'XCR_ACCESS_READY pid=99 socket='+config['socket']+' expires='+config['expires']
            if '--status' in args:
                return ('XCR1 OK STATUS uid=0 expires='+config['expires']) if self.live else 'XCR1 ERROR UNAVAILABLE'
            if '--stop' in args:
                if not self.live: return 'XCR_ACCESS_ALREADY_STOPPED socket='+config['socket']
                self.live=False; return 'XCR1 OK SHUTDOWN uid=0 expires='+config['expires']
        if args[:2]==('am','broadcast'):
            if 'config' in args:
                code=self.config_result
                if code==200: self.app_config=json.loads(base64.b64decode(args[-1]))
                if self.config_reply_lost:
                    self.config_reply_lost=False
                    raise fa.AccessError('ADB reply lost.')
            elif 'clear' in args:
                code=self.clear_result
                if 'socket' in args and self.app_config and self.app_config['socket']!=args[args.index('socket')+1]: code=409
                if code==200: self.app_config=None
            else:
                code=404 if self.app_config is None else 200 if self.live else 503
                if 'socket' in args and self.app_config and self.app_config['socket']!=args[args.index('socket')+1]: code=409
            return 'Broadcast completed: result='+str(code)
        if args[:2]==('rm','-f'): self.files.pop(args[-1],None); return ''
        raise AssertionError(('unexpected shell',args))
    def script(self,script,*,input=None):
        self.calls.append(('script',script))
        if input is None:
            if fa.BASE not in self.dirs: self.dirs[fa.BASE]='0:700:directory'
        else:
            import shlex
            words=shlex.split(script); path=words[words.index('>')+1]
            if path in self.files: raise fa.AccessError('Existing file refused.')
            self.files[path]=input; self.modes[path]=words[words.index('chmod')+1]
        return ''


class LifecycleTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.apk=Path(self.temp.name)/'owner.apk'; self.apk.write_bytes(b'owner-apk')
        self.adb=FakeAdb(); self.sdk=FakeSdk(self.apk)
        self.installer=fa.Installer(self.adb,self.sdk,clock=lambda:19000)

    def apply(self):
        return self.installer.apply(self.apk,apply=True,acknowledgement=True,idle=True)

    def test_preparation_is_read_only_even_for_existing_install(self):
        self.adb.apk=b'older-apk'
        report,_=self.installer.prepare(self.apk)
        self.assertTrue(report['dryRun'])
        self.assertFalse(self.adb.files)
        self.assertFalse(any(call[0]=='script' or call[1][0]=='install' for call in self.adb.calls))

    def test_no_acknowledgement_makes_no_adb_calls(self):
        with self.assertRaises(fa.AccessError): self.installer.apply(self.apk,apply=True,idle=True)
        self.assertFalse(self.adb.calls)

    def test_signature_conflict_does_not_install_or_create_session(self):
        self.adb.apk=b'other-apk'
        with self.assertRaises(fa.AccessError): self.apply()
        self.assertEqual(self.adb.apk,b'other-apk'); self.assertFalse(self.adb.files)

    def test_apply_verify_stop_proves_app_identity_without_uart(self):
        state=self.apply()
        token=self.adb.app_config['token']
        self.assertNotIn(token,json.dumps(state))
        self.assertTrue(self.installer.verify(state)['appAuthorizationVerified'])
        self.assertFalse(self.installer.verify(state)['opensUart'])
        result=self.installer.stop(state,apply=True,acknowledgement=True,idle=True)
        self.assertFalse(result['physicalIdleVerified'])
        self.assertFalse(self.adb.live); self.assertIsNone(self.adb.app_config)
        self.assertFalse(any(p.endswith('/config') for p in self.adb.files))
        self.assertFalse(any('--serve' in str(c) or '--probe-fast' in str(c) or 'force-stop' in str(c) for c in self.adb.calls))

    def test_active_receiver_rejects_shutdown_and_keeps_config(self):
        state=self.apply(); self.adb.clear_result=409
        with self.assertRaises(fa.AccessError):
            self.installer.stop(state,apply=True,acknowledgement=True,idle=True)
        self.assertTrue(self.adb.live); self.assertIsNotNone(self.adb.app_config)

    def test_failed_provisioning_stops_new_backend(self):
        self.adb.config_result=409
        with self.assertRaises(fa.AccessError) as caught: self.apply()
        self.assertFalse(self.adb.live)
        self.assertNotIn('token',str(caught.exception)); self.assertIsNone(self.adb.app_config)

    def test_readback_tampering_aborts_before_start(self):
        self.adb.tamper=True
        with self.assertRaises(fa.AccessError): self.apply()
        self.assertFalse(self.adb.live)

    def test_insecure_existing_base_is_not_chmodded_into_acceptance(self):
        self.adb.dirs[fa.BASE]='1000:777:directory'
        with self.assertRaises(fa.AccessError): self.apply()
        self.assertEqual(self.adb.dirs[fa.BASE],'1000:777:directory')
        self.assertFalse(self.adb.files)

    def test_upgrading_an_active_app_is_rejected_before_package_install(self):
        self.adb.apk=b'older-apk'; self.adb.clear_result=409
        with self.assertRaises(fa.AccessError): self.apply()
        self.assertEqual(self.adb.apk,b'older-apk')
        self.assertFalse(self.adb.files)

    def test_identical_installed_apk_is_not_reinstalled(self):
        self.adb.apk=b'owner-apk'; self.apply()
        self.assertFalse(any(c[0]=='run' and c[1][0]=='install' for c in self.adb.calls))

    def test_lease_uses_robot_clock_not_pc_clock(self):
        self.adb.clock=25000
        state=self.apply()
        self.assertEqual(state['expiresAtSeconds'],28600)

    def test_stop_survives_a_large_backward_wall_clock_jump(self):
        state=self.apply(); self.adb.clock=1000
        self.assertTrue(self.installer.stop(state,apply=True,acknowledgement=True,idle=True)['revocationRequested'])

    def test_staging_uses_binary_safe_push_not_windows_stdin(self):
        self.sdk.bridge=b'ELF\x00\x1a\xff\r\ncomplete'
        state=self.apply()
        pushes=[c for c in self.adb.calls if c[0]=='run' and c[1][0]=='push']
        self.assertEqual(len(pushes),2)
        self.assertFalse(any(c[0]=='script' and 'cat >' in c[1] for c in self.adb.calls))
        self.assertEqual(fa.BASE,'/data/local/xcr-access')
        self.assertEqual(self.adb.files[fa.session_path(state['session'])+'/bridge'],self.sdk.bridge)

    def test_broker_executes_from_immutable_verified_package_path(self):
        state=self.apply()
        starts=[c[1][0] for c in self.adb.calls if c[0]=='shell' and '--detach' in c[1]]
        self.assertEqual(starts,['/data/app/robot/lib/arm/libcooking_robot_access.so'])
        self.assertFalse(any(path.endswith('/access') for path in self.adb.files))

    def test_untrusted_parent_is_rejected_without_repairing_permissions(self):
        for value in ['2000:777:directory','0:777:directory','0:751:symbolic link','0:2000:751:directory']:
            with self.subTest(value=value):
                self.adb=FakeAdb(); self.adb.dirs['/data/local']=value
                self.installer=fa.Installer(self.adb,self.sdk)
                with self.assertRaises(fa.AccessError): self.apply()
                self.assertFalse(self.adb.files)

    def test_untrusted_package_broker_or_ancestor_is_rejected(self):
        broker='/data/app/robot/lib/arm/libcooking_robot_access.so'
        for path,value in [(broker,'10001:10001:755:regular file'),(broker,'1000:1000:777:regular file'),
                           (broker,'1000:1000:755:symbolic link'),('/data/app/robot','10001:10001:755:directory')]:
            with self.subTest(path=path,value=value):
                self.adb=FakeAdb(); self.adb.dirs[path]=value
                self.installer=fa.Installer(self.adb,self.sdk)
                with self.assertRaises(fa.AccessError): self.apply()
                self.assertFalse(self.adb.files)

    def test_installed_broker_hash_is_verified_before_execution(self):
        self.adb.broker=b'tampered'
        with self.assertRaises(fa.AccessError): self.apply()
        self.assertFalse(self.adb.live); self.assertFalse(self.adb.files)

    def test_session_record_is_available_before_secret_provisioning(self):
        records=[]; self.adb.config_result=409
        with self.assertRaises(fa.AccessError):
            self.installer.apply(self.apk,apply=True,acknowledgement=True,idle=True,record=lambda value:records.append(dict(value)))
        self.assertEqual(len(records),1)
        self.assertFalse(records[0]['appAuthorizationVerified'])
        self.assertNotIn('token',json.dumps(records))

    def test_expired_session_can_be_revoked_after_broker_exit(self):
        state=self.apply(); self.adb.clock=30000; self.adb.live=False
        result=self.installer.stop(state,apply=True,acknowledgement=True,idle=True)
        self.assertTrue(result['revocationRequested']); self.assertIsNone(self.adb.app_config)

    def test_verify_rejects_expired_session(self):
        state=self.apply(); self.adb.clock=30000
        with self.assertRaises(fa.AccessError): self.installer.verify(state)

    def test_verify_requires_both_root_status_and_app_id(self):
        state=self.apply(); self.adb.live=False
        with self.assertRaises(fa.AccessError): self.installer.verify(state)
        self.adb.live=True; self.adb.app_config=None
        with self.assertRaises(fa.AccessError): self.installer.verify(state)

    def test_changed_installed_package_blocks_session_reuse(self):
        state=self.apply(); self.adb.apk=b'other-apk'
        with self.assertRaises(fa.AccessError): self.installer.verify(state)

    def test_state_for_another_target_is_rejected(self):
        state=self.apply(); state['serial']='other:5555'
        with self.assertRaises(fa.AccessError): self.installer.verify(state)

    def test_old_record_cannot_verify_or_revoke_new_app_session(self):
        state=self.apply(); self.adb.app_config['socket']='xcr.access.10001.'+'b'*16
        with self.assertRaises(fa.AccessError): self.installer.verify(state)
        with self.assertRaises(fa.AccessError):
            self.installer.stop(state,apply=True,acknowledgement=True,idle=True)
        self.assertEqual(self.adb.app_config['socket'],'xcr.access.10001.'+'b'*16)

    def test_cleanup_failed_apply_never_clears_previous_app_configuration(self):
        records=[]; self.adb.config_result=409
        previous={'socket':'xcr.access.10001.'+'b'*16,'token':'b'*64}
        self.adb.app_config=dict(previous)
        with self.assertRaises(fa.AccessError):
            self.installer.apply(self.apk,apply=True,acknowledgement=True,idle=True,record=lambda value:records.append(dict(value)))
        self.assertEqual(self.adb.app_config,previous)

    def test_lost_provision_reply_still_clears_only_matching_idle_config(self):
        self.adb.config_reply_lost=True
        with self.assertRaises(fa.AccessError): self.apply()
        self.assertIsNone(self.adb.app_config); self.assertFalse(self.adb.live)

    def test_lost_provision_reply_cannot_shutdown_a_now_active_session(self):
        self.adb.config_reply_lost=True; self.adb.clear_result=409
        with self.assertRaises(fa.AccessError): self.apply()
        self.assertIsNotNone(self.adb.app_config); self.assertTrue(self.adb.live)


class ApkTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.path=Path(self.temp.name)/'owner.apk'
        self.sdk=fa.Sdk(Path(self.temp.name),'java')
        self.sdk.metadata=lambda path:(191,'a'*64)
        self.bridge=(fa.ROOT/'machine/src/main/jniLibs/armeabi-v7a/libkitchenos_bridge.so').read_bytes()

    def archive(self,entries):
        with zipfile.ZipFile(self.path,'w') as archive:
            for name,data in entries: archive.writestr(name,data)

    def test_native_entries_are_extracted_from_verified_apk(self):
        self.archive([('lib/armeabi-v7a/libcooking_robot_access.so',self.bridge),('lib/armeabi-v7a/libkitchenos_bridge.so',self.bridge)])
        apk=self.sdk.apk(self.path,'armeabi-v7a')
        self.assertEqual(apk.bridge,self.bridge); self.assertEqual(apk.sha256,fa.digest(self.path.read_bytes()))

    def test_missing_broker_and_wrong_abi_are_rejected(self):
        self.archive([('lib/armeabi-v7a/libkitchenos_bridge.so',self.bridge)])
        with self.assertRaises(fa.AccessError): self.sdk.apk(self.path,'armeabi-v7a')
        self.archive([('lib/armeabi-v7a/libcooking_robot_access.so',b'not an elf executable!'),('lib/armeabi-v7a/libkitchenos_bridge.so',self.bridge)])
        with self.assertRaises(fa.AccessError): self.sdk.apk(self.path,'armeabi-v7a')

    def test_changed_bridge_contract_is_rejected(self):
        self.archive([('lib/armeabi-v7a/libcooking_robot_access.so',self.bridge),('lib/armeabi-v7a/libkitchenos_bridge.so',self.bridge+b'changed')])
        with self.assertRaisesRegex(fa.AccessError,'controller contract'): self.sdk.apk(self.path,'armeabi-v7a')


class RecordTests(unittest.TestCase):
    def test_private_or_unknown_fields_in_records_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'record.json'; path.write_text(json.dumps({'token':'a'*64}))
            with self.assertRaises(fa.AccessError) as caught: fa.read_state(path)
            self.assertNotIn('a'*64,str(caught.exception))

    def test_repeated_or_wrong_typed_record_fields_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            apk=Path(folder)/'owner.apk'; apk.write_bytes(b'owner-apk')
            adb=FakeAdb(); installer=fa.Installer(adb,FakeSdk(apk))
            state=installer.apply(apk,apply=True,acknowledgement=True,idle=True)
            path=Path(folder)/'record.json'
            for key,value in [('uid',True),('testEmulator','false'),('apkSha256','invalid'),('socket','arbitrary')]:
                changed=dict(state); changed[key]=value; path.write_text(json.dumps(changed))
                with self.subTest(key=key), self.assertRaises(fa.AccessError): fa.read_state(path)
            path.write_text(json.dumps(state)[:-1]+', "uid":10001}')
            with self.assertRaises(fa.AccessError): fa.read_state(path)


if __name__ == '__main__': unittest.main()
