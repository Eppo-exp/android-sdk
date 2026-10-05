#!/usr/bin/env python3
"""Validate and publish packages for an existing GitHub release."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

REPO = 'Eppo-exp/android-sdk'
CENTRAL = 'https://repo.maven.apache.org/maven2/cloud/eppo/'
FRAMEWORK = 'android-sdk-framework'
COMMON = 'android-sdk'
STAGING = Path('build/staging-deploy')
SEMVER = re.compile(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)')


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def gh_json(*args):
    return json.loads(run('gh', *args))


def stable(version):
    if not SEMVER.fullmatch(version):
        raise ValueError('Expected stable MAJOR.MINOR.PATCH, got ' + version)
    return version


def read_versions():
    result = run('./gradlew', '-q', ':releaseVersions', '--console=plain')
    versions = json.loads(next(line for line in result.splitlines() if line.startswith('{')))
    return {artifact: stable(versions[artifact]) for artifact in (FRAMEWORK, COMMON)}


def framework_input(path):
    # Conservative: new build inputs are included unless explicitly administrative.
    if path.startswith('android-sdk-framework/') or path == 'scripts/release/test-data-ref':
        return True
    return not (path.startswith(('eppo/', 'example/', '.github/', 'scripts/'))
                or path.endswith('.md') or path in ('.gitignore', '.gitattributes', 'LICENSE'))


def fingerprint(artifact):
    digest = hashlib.sha256()
    for path in sorted(run('git', 'ls-files').splitlines()):
        if not framework_input(path) and not (
                artifact == COMMON and path.startswith('eppo/')):
            continue
        data = Path(path).read_bytes()
        digest.update(path.encode() + b'\0' + data + b'\0')
    return digest.hexdigest()


def central_file(artifact, version, filename):
    url = CENTRAL + artifact + '/' + version + '/' + filename
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        error.close()
        if error.code == 404:
            return None
        raise  # Auth/rate-limit/server errors are not missing artifacts.


def available(artifact, version):
    return central_file(artifact, version, artifact + '-' + version + '.pom') is not None


def record_name(artifact, version):
    return artifact + '-' + version + '.json'


def releases():
    pages = gh_json('api', '--paginate', '--slurp', 'repos/' + REPO + '/releases?per_page=100')
    return [release for page in pages for release in page]


def record_from(release, artifact, version):
    name = record_name(artifact, version)
    for asset in release['assets']:
        if asset['name'] == name:
            return gh_json('api', '-H', 'Accept: application/octet-stream',
                           'repos/' + REPO + '/releases/assets/' + str(asset['id']))
    return None


def required_files(artifact, version):
    base = artifact + '-' + version
    suffixes = ['.pom', '.module', '.aar', '-sources.jar', '-javadoc.jar']
    return {base + suffix for suffix in suffixes}


def validate_record(record, artifact, version, inputs):
    if (record.get('schema') != 1 or record.get('artifact') != artifact
            or record.get('version') != version
            or record.get('state') not in ('attempting', 'published')
            or not required_files(artifact, version).issubset(record.get('files', {}))):
        raise ValueError('Publication provenance is incomplete for ' + artifact + ':' + version)
    if record.get('inputs') != inputs:
        version_file = 'android-sdk-framework/build.gradle' if artifact == FRAMEWORK else 'eppo/build.gradle'
        raise ValueError(artifact + ':' + version + ' has new source/build inputs but no version bump. '
                         + 'Bump the version in ' + version_file + ' before releasing.')
    for name, checksum in record['files'].items():
        if '/' in name or not re.fullmatch(r'[a-f0-9]{64}', checksum):
            raise ValueError('Invalid publication record')


def verify_record(record):
    complete = True
    for filename, expected in record['files'].items():
        data = central_file(record['artifact'], record['version'], filename)
        if data is None:
            complete = False
        elif hashlib.sha256(data).hexdigest() != expected:
            raise ValueError('Central content differs from publication record: ' + filename)
    return complete


def wait_for_record(record, seconds=1200):
    deadline = time.monotonic() + seconds
    while True:
        if verify_record(record):
            return
        if time.monotonic() >= deadline:
            raise ValueError('Publication is incomplete for ' + record['artifact']
                             + '. Inspect the existing Central deployment; do not re-upload blindly.'
                             + ' Once it is published, rerun this release workflow.')
        time.sleep(20)


def choose_action(record, exists):
    if record:
        # Completion comes from Central, not a mutable GitHub status record.
        return 'reuse' if exists else 'resume'
    if exists:
        raise ValueError('Coordinate exists without a publication record; refusing to overwrite/reuse it')
    return 'publish'


def make_plan(tag=None):
    if run('git', 'status', '--porcelain', '--untracked-files=normal'):
        raise ValueError('Use a clean checkout of the committed release inputs')
    fixture_ref = Path('scripts/release/test-data-ref').read_text().strip()
    if not re.fullmatch(r'[a-f0-9]{40}', fixture_ref):
        raise ValueError('Release test-data-ref must pin a full Git commit SHA')
    versions = read_versions()
    expected_tag = 'v' + versions[COMMON]
    if tag is not None and tag != expected_tag:
        raise ValueError('Release tag must be ' + expected_tag)
    sha = run('git', 'rev-parse', 'HEAD')
    existing_tag = subprocess.run(['git', 'rev-parse', '--verify',
                                   'refs/tags/' + expected_tag + '^{commit}'],
                                  text=True, capture_output=True)
    if existing_tag.returncode == 0 and existing_tag.stdout.strip() != sha:
        raise ValueError('Existing tag points at a different commit: ' + expected_tag)
    subprocess.run(['git', 'merge-base', '--is-ancestor', sha, 'origin/main'], check=True)
    all_releases = releases()
    plan = {'schema': 1, 'tag': expected_tag, 'commit': sha, 'artifacts': {}}
    for artifact, version in versions.items():
        inputs = fingerprint(artifact)
        matches = []
        for release in all_releases:
            if release['draft'] or release['prerelease']:
                continue
            record = record_from(release, artifact, version)
            if record:
                validate_record(record, artifact, version, inputs)
                matches.append(record)
        record = next((r for r in matches if r['state'] == 'published'),
                      matches[0] if matches else None)
        if matches and any(r['files'] != record['files'] for r in matches):
            raise ValueError('Conflicting publication records for ' + artifact)
        exists = verify_record(record) if record else available(artifact, version)
        try:
            action = choose_action(record, exists)
        except ValueError as error:
            raise ValueError(artifact + ':' + version + ': ' + str(error)) from error
        if artifact == COMMON and record and record['commit'] != sha:
            raise ValueError('Common version already belongs to another release commit')
        plan['artifacts'][artifact] = {'version': version, 'inputs': inputs,
                                      'action': action, 'record': record}
    return plan


def save_json(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n')


def upload_record(tag, record):
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / record_name(record['artifact'], record['version'])
        save_json(path, record)
        subprocess.run(['gh', 'release', 'upload', tag, str(path), '--repo', REPO], check=True)


def stage_record(artifact, item, plan):
    shutil.rmtree(STAGING, ignore_errors=True)
    module = 'android-sdk-framework' if artifact == FRAMEWORK else 'eppo'
    task = ':' + module + ':publishAllPublicationsToReleaseStagingRepository'
    subprocess.run(['./gradlew', task, '-Prelease'], check=True)
    directory = STAGING / 'cloud/eppo' / artifact / item['version']
    files = {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
             for path in directory.iterdir() if path.suffix in ('.aar', '.jar', '.pom', '.module')}
    record = {'schema': 1, 'artifact': artifact, 'version': item['version'],
              'inputs': item['inputs'], 'commit': plan['commit'], 'state': 'attempting',
              'files': files, 'run': os.environ.get('GITHUB_RUN_ID')}
    validate_record(record, artifact, item['version'], item['inputs'])
    pom = ET.parse(directory / (artifact + '-' + item['version'] + '.pom'))
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    deps = [(d.findtext('m:groupId', namespaces=ns), d.findtext('m:artifactId', namespaces=ns),
             d.findtext('m:version', namespaces=ns)) for d in pom.findall('m:dependencies/m:dependency', ns)]
    if any(v and ('SNAPSHOT' in v or '+' in v or '[' in v or '(' in v) for _, _, v in deps):
        raise ValueError('Release POM must reference stable framework and fixed dependencies')
    if artifact == COMMON:
        expected = ('cloud.eppo', FRAMEWORK, plan['artifacts'][FRAMEWORK]['version'])
        if expected not in deps:
            raise ValueError('SDK POM must reference the selected stable framework')
    return record


def publish(plan):
    # No package upload before all tests have passed in the calling workflow.
    for artifact in (FRAMEWORK, COMMON):
        item = plan['artifacts'][artifact]
        record = item['record']
        if item['action'] == 'publish':
            record = stage_record(artifact, item, plan)
            # Persist intent and exact checksums BEFORE contacting Central. If the runner
            # disappears, a rerun must reconcile this attempt instead of uploading twice.
            upload_record(plan['tag'], record)
            # Do not let JReleaser reuse framework outputs during the common deployment.
            shutil.rmtree(Path('build/jreleaser'), ignore_errors=True)
            subprocess.run(['./gradlew', 'jreleaserDeploy', '-PdeploymentVersion=' + item['version']], check=True)
        wait_for_record(record)
        # The intent/checksum record is immutable. Never replace it after deploy:
        # retries establish completion by checking Central against this record.
        print('Verified ' + artifact + ':' + item['version'], flush=True)
    verify_consumer(plan)
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a') as output:
            output.write('## Maven Central publication verified\n\n')
            for artifact, item in plan['artifacts'].items():
                output.write('- [' + artifact + ':' + item['version'] + ']('
                             + CENTRAL + artifact + '/' + item['version'] + '/)\n')


def verify_consumer(plan, repository='https://repo.maven.apache.org/maven2'):
    # Resolve published AARs and their transitive dependencies without project/mavenLocal fallback.
    version = plan['artifacts'][COMMON]['version']
    framework = plan['artifacts'][FRAMEWORK]['version']
    with tempfile.TemporaryDirectory() as directory:
        target = Path(directory)
        (target / 'settings.gradle').write_text("rootProject.name = 'release-consumer'\n")
        (target / 'build.gradle').write_text("""
repositories {
  maven { url '%s' }
  mavenCentral()
  google()
}
configurations { sdk }
dependencies { sdk 'cloud.eppo:android-sdk:%s' }
tasks.register('verifyRelease') {
  doLast {
    def artifacts = configurations.sdk.resolvedConfiguration.resolvedArtifacts
    assert artifacts.any { it.name == 'android-sdk' && it.type == 'aar' &&
      it.moduleVersion.id.version == '%s' }
    assert artifacts.any { it.name == 'android-sdk-framework' && it.type == 'aar' &&
      it.moduleVersion.id.version == '%s' }
    assert !artifacts.any { it.moduleVersion.id.version.endsWith('-SNAPSHOT') }
  }
}
""" % (repository, version, version, framework))
        subprocess.run([str(Path('gradlew').resolve()), '-p', directory,
                        'verifyRelease', '--refresh-dependencies'], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['plan', 'publish'])
    parser.add_argument('--tag', required=True)
    args = parser.parse_args()
    plan = make_plan(args.tag)
    print(json.dumps(plan, indent=2), flush=True)
    if args.command == 'publish':
        if run('git', 'rev-parse', 'refs/tags/' + args.tag + '^{commit}') != plan['commit']:
            raise ValueError('Checkout does not match the release tag')
        publish(plan)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, subprocess.CalledProcessError, urllib.error.URLError) as error:
        sys.exit(str(error))
