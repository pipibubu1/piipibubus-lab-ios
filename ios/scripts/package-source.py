from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import plistlib, yaml
root=Path(__file__).resolve().parents[2]
folder=root/'ios'
spec=yaml.safe_load((folder/'project.yml').read_text())
assert spec['options']['deploymentTarget']['iOS']=='18.4'
info=plistlib.loads((folder/'App/Info.plist').read_bytes())
assert info['CFBundleDisplayName']=='PIIPIBUBUS LAB'
assert info['CFBundleURLTypes'][0]['CFBundleURLSchemes']==['piipibubuslab']
assert 'NSAppTransportSecurity' not in info
sources=sorted((folder/'App').glob('*.swift'))
assert len(sources)==4
controller=(folder/'App/MainViewController.swift').read_text()
assert 'ASWebAuthenticationSession' in controller and 'PHPickerViewController' in controller
assert 'addScriptMessageHandler' not in controller and 'loadFileURL' not in controller
assert 'CODE_SIGNING_ALLOWED=NO' in (folder/'scripts/build-unsigned.sh').read_text()
assert 'macos-15' in (folder/'ci/build-ios.yml').read_text()
output=root/'public/downloads/piipibubus-ios-project.zip'
with ZipFile(output,'w',ZIP_DEFLATED) as bundle:
    for path in sorted(folder.rglob('*')):
        if path.is_file() and not any(part.endswith('.xcodeproj') or part=='ios-output' for part in path.parts):
            bundle.write(path,path.relative_to(root))
    bundle.write(folder/'ci/build-ios.yml','.github/workflows/build-ios.yml')
with ZipFile(output) as bundle:
    names=bundle.namelist()
    assert '.github/workflows/build-ios.yml' in names and 'ios/project.yml' in names
    assert not any(name.endswith(('.ipa','.keystore','.p12','.mobileprovision')) for name in names)
print('iOS project configuration and source package checks passed. No Apple SDK build has been run.')
