import hashlib
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

spec = importlib.util.spec_from_file_location('release', Path(__file__).parents[1] / 'release.py')
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)


def record(artifact=r.FRAMEWORK, state='published'):
    return {'schema': 1, 'artifact': artifact, 'version': '0.1.0', 'inputs': 'inputs',
            'commit': 'commit', 'state': state,
            'files': {name: hashlib.sha256(b'published').hexdigest()
                      for name in r.required_files(artifact, '0.1.0')}}


class ReleaseTests(unittest.TestCase):
    def test_stable_versions_only(self):
        for version in ['0.1.0', '4.0.0', '12.31.0']:
            self.assertEqual(r.stable(version), version)
        for version in ['4.0.0-SNAPSHOT', 'v4.0.0', '04.0.0', '4.0.0-rc.1', '4.0.0\n', '$(id)']:
            with self.assertRaises(ValueError):
                r.stable(version)

    def test_first_release_publishes_both(self):
        self.assertEqual(r.choose_action(None, False), 'publish')

    def test_common_only_reuses_framework(self):
        self.assertEqual(r.choose_action(record(), True), 'reuse')
        self.assertEqual(r.choose_action(None, False), 'publish')

    def test_unknown_existing_coordinate_fails_closed(self):
        with self.assertRaisesRegex(ValueError, 'without a publication record'):
            r.choose_action(None, True)

    def test_changed_framework_without_bump_fails(self):
        with self.assertRaisesRegex(ValueError, 'no version bump'):
            r.validate_record(record(), r.FRAMEWORK, '0.1.0', 'changed inputs')

    def test_framework_aar_required(self):
        value = record()
        del value['files']['android-sdk-framework-0.1.0.aar']
        with self.assertRaises(ValueError):
            r.validate_record(value, r.FRAMEWORK, '0.1.0', 'inputs')

    def test_shared_build_and_fixture_pin_are_framework_inputs(self):
        for path in ['build.gradle', 'settings.gradle', 'gradle.properties', 'Makefile',
                     'gradle/wrapper/gradle-wrapper.properties', 'scripts/release/test-data-ref',
                     'android-sdk-framework/src/main/java/Example.java', 'android-sdk-framework/src/test/java/ExampleTest.java',
                     'android-sdk-framework/src/main/resources/notice.md', 'android-sdk-framework/src/test/resources/fixture.md', 'new-build-input']:
            self.assertTrue(r.framework_input(path), path)
        for path in ['eppo/build.gradle', 'README.md', '.github/workflows/test.yml',
                     'scripts/release/release.py']:
            self.assertFalse(r.framework_input(path), path)

    def test_only_404_is_absence(self):
        for code in [401, 403, 429, 500]:
            with patch.object(r.urllib.request, 'urlopen', side_effect=urllib.error.HTTPError(
                    'https://example.test', code, 'error', {}, None)):
                with self.assertRaises(urllib.error.HTTPError):
                    r.available(r.FRAMEWORK, '0.1.0')
        with patch.object(r.urllib.request, 'urlopen', side_effect=urllib.error.HTTPError(
                'https://example.test', 404, 'missing', {}, None)):
            self.assertFalse(r.available(r.FRAMEWORK, '0.1.0'))

    def test_resume_requires_all_matching_files(self):
        value = record(state='attempting')
        self.assertEqual(r.choose_action(value, False), 'resume')
        with patch.object(r, 'central_file', return_value=b'published'):
            self.assertTrue(r.verify_record(value))
        with patch.object(r, 'central_file', return_value=None):
            self.assertFalse(r.verify_record(value))
        with patch.object(r, 'central_file', return_value=b'different'):
            with self.assertRaisesRegex(ValueError, 'differs'):
                r.verify_record(value)

    def test_pending_deployment_is_not_uploaded_again(self):
        value = record(state='attempting')
        plan = {'tag': 'v4.0.0', 'artifacts': {
            r.FRAMEWORK: {'action': 'resume', 'record': value, 'version': '0.1.0'}}}
        with patch.object(r, 'wait_for_record', side_effect=ValueError('pending')), \
                patch.object(r, 'stage_record') as stage, patch.object(r, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'pending'):
                r.publish(plan)
            stage.assert_not_called()
            run.assert_not_called()

    def test_resume_framework_success_then_publish_common(self):
        framework = record()
        common = record(r.COMMON, 'attempting')
        plan = {'tag': 'v4.0.0', 'artifacts': {
            r.FRAMEWORK: {'action': 'reuse', 'record': framework, 'version': '0.1.0'},
            r.COMMON: {'action': 'publish', 'record': None, 'version': '4.0.0'}}}
        with patch.object(r, 'wait_for_record') as wait, \
                patch.object(r, 'stage_record', return_value=common) as stage, \
                patch.object(r, 'upload_record') as upload, \
                patch.object(r, 'verify_consumer') as consumer, \
                patch.object(r.subprocess, 'run') as deploy, patch.object(r.shutil, 'rmtree'):
            r.publish(plan)
            stage.assert_called_once_with(r.COMMON, plan['artifacts'][r.COMMON], plan)
            deploy.assert_called_once_with(['./gradlew', 'jreleaserDeploy', '-PdeploymentVersion=4.0.0'], check=True)
            self.assertEqual(wait.call_count, 2)
            upload.assert_called_once_with(plan['tag'], common)
            consumer.assert_called_once_with(plan)

    def test_persist_intent_before_deploy(self):
        plan = {'tag': 'v4.0.0', 'artifacts': {
            r.FRAMEWORK: {'action': 'publish', 'record': None, 'version': '0.1.0'}}}
        with patch.object(r, 'stage_record', return_value=record(state='attempting')), \
                patch.object(r, 'upload_record', side_effect=ValueError('cannot persist')), \
                patch.object(r.subprocess, 'run') as deploy:
            with self.assertRaisesRegex(ValueError, 'cannot persist'):
                r.publish(plan)
            deploy.assert_not_called()

    def test_plan_rejects_wrong_tag_and_detached_version(self):
        with patch.object(r, 'run', return_value=''), \
                patch.object(r, 'read_versions', return_value={r.FRAMEWORK: '0.1.0', r.COMMON: '4.0.0'}):
            with self.assertRaisesRegex(ValueError, 'must be v4.0.0'):
                r.make_plan('v0.1.0')

    def test_plan_rejects_dirty_checkout(self):
        with patch.object(r, 'run', return_value=' M build.gradle'):
            with self.assertRaisesRegex(ValueError, 'clean checkout'):
                r.make_plan()

    def test_pom_verification_rejects_snapshot_dependency(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / 'cloud/eppo/android-sdk/0.1.0'
            target.mkdir(parents=True)
            for name in r.required_files(r.COMMON, '0.1.0'):
                (target / name).write_bytes(b'jar')
            (target / 'android-sdk-0.1.0.pom').write_text('''
<project xmlns="http://maven.apache.org/POM/4.0.0"><dependencies><dependency>
<groupId>cloud.eppo</groupId><artifactId>android-sdk-framework</artifactId>
<version>0.1.0-SNAPSHOT</version></dependency></dependencies></project>''')
            with patch.object(r, 'STAGING', root), patch.object(r.shutil, 'rmtree'), \
                    patch.object(r.subprocess, 'run'):
                with self.assertRaisesRegex(ValueError, 'stable framework'):
                    r.stage_record(r.COMMON, {'version': '0.1.0', 'inputs': 'inputs'},
                                   {'commit': 'commit', 'artifacts': {r.FRAMEWORK: {'version': '0.1.0'}}})


    def test_framework_pom_rejects_java_snapshot_before_deploy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / 'cloud/eppo/android-sdk-framework/0.1.0'
            target.mkdir(parents=True)
            for name in r.required_files(r.FRAMEWORK, '0.1.0'):
                (target / name).write_bytes(b'aar')
            pom = target / 'android-sdk-framework-0.1.0.pom'
            pom.write_text("""<project xmlns="http://maven.apache.org/POM/4.0.0">
<dependencies><dependency><groupId>cloud.eppo</groupId>
<artifactId>eppo-sdk-framework</artifactId><version>0.1.0-SNAPSHOT</version>
</dependency></dependencies></project>""")
            with patch.object(r, 'STAGING', root), patch.object(r.shutil, 'rmtree'), \
                    patch.object(r.subprocess, 'run'):
                with self.assertRaisesRegex(ValueError, 'fixed dependencies'):
                    r.stage_record(r.FRAMEWORK, {'version': '0.1.0', 'inputs': 'inputs'},
                                   {'commit': 'commit'})
                pom.write_text(pom.read_text().replace('0.1.0-SNAPSHOT', '0.1.0'))
                value = r.stage_record(r.FRAMEWORK, {'version': '0.1.0', 'inputs': 'inputs'},
                                       {'commit': 'commit'})
                self.assertIn('android-sdk-framework-0.1.0.aar', value['files'])

    def test_common_changes_without_version_bump_fail(self):
        with self.assertRaisesRegex(ValueError, 'android-sdk.*no version bump'):
            r.validate_record(record(r.COMMON), r.COMMON, '0.1.0', 'changed common inputs')

    def test_full_plan_first_release_common_only_and_missing_bumps(self):
        versions = {r.FRAMEWORK: '0.1.0', r.COMMON: '4.0.0'}
        framework = record()
        old_common = record(r.COMMON)
        old_common['version'] = '4.0.0'
        old_common['files'] = {name: hashlib.sha256(b'published').hexdigest()
                               for name in r.required_files(r.COMMON, '4.0.0')}
        history = [{'draft': False, 'prerelease': False}]

        def command(*args):
            if args[:2] == ('git', 'status'):
                return ''
            return 'commit'

        def provenance(release, artifact, version):
            return records.get((artifact, version))

        cases = [
            ({}, {}, {r.FRAMEWORK: 'publish', r.COMMON: 'publish'}, None),
            ({(r.FRAMEWORK, '0.1.0'): framework}, {},
             {r.FRAMEWORK: 'reuse', r.COMMON: 'publish'}, None),
            ({(r.FRAMEWORK, '0.1.0'): framework}, {r.FRAMEWORK: 'changed'}, None,
             'android-sdk-framework.*no version bump'),
            ({(r.FRAMEWORK, '0.1.0'): framework, (r.COMMON, '4.0.0'): old_common},
             {r.COMMON: 'changed'}, None, 'android-sdk.*no version bump'),
        ]
        for records, changed, expected, error in cases:
            with self.subTest(expected=expected, error=error), \
                    patch.object(r, 'run', side_effect=command), \
                    patch.object(r, 'read_versions', return_value=versions), \
                    patch.object(r.subprocess, 'run', return_value=subprocess.CompletedProcess([], 1, '')), \
                    patch.object(r, 'releases', return_value=history), \
                    patch.object(r, 'record_from', side_effect=provenance), \
                    patch.object(r, 'available', side_effect=lambda a, v: (a, v) in records), \
                    patch.object(r, 'verify_record', return_value=True), \
                    patch.object(r, 'fingerprint', side_effect=lambda a: changed.get(a, 'inputs')):
                if error:
                    with self.assertRaisesRegex(ValueError, error):
                        r.make_plan('v4.0.0')
                else:
                    plan = r.make_plan('v4.0.0')
                    self.assertEqual({a: v['action'] for a, v in plan['artifacts'].items()}, expected)

    def test_fingerprint_detects_real_source_build_and_pin_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tracked = ['android-sdk-framework/src/main/Test.java', 'eppo/android-sdk-framework/src/main/Common.java',
                       'build.gradle', 'scripts/release/test-data-ref', 'README.md',
                       'android-sdk-framework/src/main/resources/notice.md', 'android-sdk-framework/src/test/resources/fixture.md']
            for name in tracked:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('original')
            import os
            previous = Path.cwd()
            try:
                os.chdir(root)
                with patch.object(r, 'run', return_value='\n'.join(tracked)):
                    framework = r.fingerprint(r.FRAMEWORK)
                    common = r.fingerprint(r.COMMON)
                    (root / tracked[1]).write_text('common change')
                    self.assertEqual(r.fingerprint(r.FRAMEWORK), framework)
                    self.assertNotEqual(r.fingerprint(r.COMMON), common)
                    (root / 'README.md').write_text('documentation change')
                    self.assertEqual(r.fingerprint(r.FRAMEWORK), framework)
                    for name in [tracked[0], 'build.gradle', 'scripts/release/test-data-ref',
                                 'android-sdk-framework/src/main/resources/notice.md', 'android-sdk-framework/src/test/resources/fixture.md']:
                        (root / name).write_text('changed')
                        self.assertNotEqual(r.fingerprint(r.FRAMEWORK), framework, name)
                        (root / name).write_text('original')
            finally:
                os.chdir(previous)


    def test_mutable_fixture_reference_is_rejected(self):
        with patch.object(r, 'run', return_value=''), patch.object(Path, 'read_text', return_value='main'):
            with self.assertRaisesRegex(ValueError, 'full Git commit SHA'):
                r.make_plan()

    def test_existing_tag_must_match_checkout(self):
        with patch.object(r, 'run', side_effect=['', 'new-sha']), \
                patch.object(r, 'read_versions', return_value={r.FRAMEWORK: '0.1.0', r.COMMON: '4.0.0'}), \
                patch.object(r.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, 'old-sha')):
            with self.assertRaisesRegex(ValueError, 'different commit'):
                r.make_plan('v4.0.0')

    def test_main_ancestry_failure_stops_before_publication_lookup(self):
        with patch.object(r, 'run', side_effect=['', 'off-main-sha']), \
                patch.object(r, 'read_versions', return_value={r.FRAMEWORK: '0.1.0', r.COMMON: '4.0.0'}), \
                patch.object(r.subprocess, 'run', side_effect=[subprocess.CompletedProcess([], 1, ''),
                    subprocess.CalledProcessError(1, ['git', 'merge-base'])]), \
                patch.object(r, 'releases') as releases:
            with self.assertRaises(subprocess.CalledProcessError):
                r.make_plan('v4.0.0')
            releases.assert_not_called()

    def test_pending_publication_wait_is_bounded(self):
        with patch.object(r, 'verify_record', return_value=False), patch.object(r.time, 'sleep') as sleep:
            with self.assertRaisesRegex(ValueError, 'Inspect the existing Central deployment'):
                r.wait_for_record(record(), seconds=0)
            sleep.assert_not_called()

    def test_intent_state_does_not_override_verified_central_completion(self):
        self.assertEqual(r.choose_action(record(state='attempting'), True), 'reuse')
        self.assertEqual(r.choose_action(record(state='published'), False), 'resume')

    def test_deploy_failure_after_central_success_retains_record_and_retries(self):
        import json
        artifacts = {r.FRAMEWORK: record(state='attempting'),
                     r.COMMON: record(r.COMMON, state='attempting')}
        plan = {'tag': 'v4.0.0', 'artifacts': {
            a: {'action': 'publish', 'record': None, 'version': value['version']}
            for a, value in artifacts.items()}}
        assets = {}
        central = set()
        uploads = []

        def external(command, **kwargs):
            if command[:3] == ['gh', 'release', 'upload']:
                self.assertNotIn('--clobber', command)
                path = Path(command[4])
                self.assertNotIn(path.name, assets, 'Provenance must never be replaced')
                assets[path.name] = json.loads(path.read_text())
                uploads.append(path.name)
            elif command[:2] == ['./gradlew', 'jreleaserDeploy']:
                central.add(r.FRAMEWORK)
                raise subprocess.CalledProcessError(1, command)
            else:
                self.fail(str(command))

        with patch.object(r, 'stage_record', side_effect=lambda a, *_: artifacts[a]), \
                patch.object(r.shutil, 'rmtree'), \
                patch.object(r.subprocess, 'run', side_effect=external):
            with self.assertRaises(subprocess.CalledProcessError):
                r.publish(plan)
        framework = assets[r.record_name(r.FRAMEWORK, '0.1.0')]
        self.assertEqual(framework['state'], 'attempting')
        self.assertEqual(central, {r.FRAMEWORK})
        with patch.object(r, 'central_file', return_value=b'published'):
            plan['artifacts'][r.FRAMEWORK].update(
                action=r.choose_action(framework, r.verify_record(framework)), record=framework)
        deployments = []

        def resume_external(command, **kwargs):
            if command[:2] == ['./gradlew', 'jreleaserDeploy']:
                deployments.append(r.COMMON)
                central.add(r.COMMON)
            else:
                external(command, **kwargs)

        with patch.object(r, 'stage_record', side_effect=lambda a, *_: artifacts[a]), \
                patch.object(r.shutil, 'rmtree'), patch.object(r, 'wait_for_record'), \
                patch.object(r, 'verify_consumer'), \
                patch.object(r.subprocess, 'run', side_effect=resume_external):
            r.publish(plan)
        self.assertEqual(deployments, [r.COMMON])
        self.assertEqual(len(uploads), 2)
        self.assertEqual(assets[r.record_name(r.FRAMEWORK, '0.1.0')], framework)

    def test_consumer_failure_after_both_deploys_needs_no_record_rewrite(self):
        plan = {'tag': 'v4.0.0', 'artifacts': {
            a: {'action': 'reuse', 'record': record(a, 'attempting'), 'version': '0.1.0'}
            for a in (r.FRAMEWORK, r.COMMON)}}
        with patch.object(r, 'wait_for_record'), patch.object(r, 'upload_record') as upload, \
                patch.object(r, 'stage_record') as stage, \
                patch.object(r, 'verify_consumer', side_effect=ValueError('consumer failed')):
            with self.assertRaisesRegex(ValueError, 'consumer failed'):
                r.publish(plan)
            upload.assert_not_called()
            stage.assert_not_called()


if __name__ == '__main__':
    unittest.main()
