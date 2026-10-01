from pathlib import Path
import hashlib, json, re, subprocess, tempfile
MAIN='96134b3ae294a8c086f467bbe8a09f842aac8949'
BASE='358e54cac3224e77d11d872ac1c97f6074131610'
DRAFT='0f21e835d09cb2e016696713ca2bca897b6e03a1'
def git(*args): return subprocess.check_output(['git',*args])
def source(ref,name):
    result=subprocess.run(['git','show',ref+':'+name],capture_output=True)
    return result.stdout if result.returncode==0 else None
subprocess.run(['git','fetch','origin',DRAFT],check=True)
changed=git('diff','--name-only',BASE,DRAFT).decode().splitlines()
skip=('pom.xml','IEStorageDoctorInspector.java','LegacyInventoryBlock.java','SlimefunBlockCompat.java')
for name in changed:
    if name.endswith(skip): continue
    m,b,d=source(MAIN,name),source(BASE,name),source(DRAFT,name)
    if m==d: continue
    path=Path(name); path.parent.mkdir(parents=True,exist_ok=True)
    if m==b:
        assert d is not None,name
        path.write_bytes(d)
    else:
        assert all(v is not None for v in (m,b,d)),name
        with tempfile.TemporaryDirectory() as td:
            paths=[Path(td)/n for n in ('main','base','draft')]
            for p,value in zip(paths,(m,b,d)): p.write_bytes(value)
            result=subprocess.run(['git','merge-file','-p',*[str(p) for p in paths]],capture_output=True)
        text=result.stdout.decode()
        if result.returncode:
            assert name.endswith(('SimpleDrawer.java','DrawerStorage.java')),name
            text=re.sub(r'^<<<<<<<[^\n]*\n(.*?)^=======\n.*?^>>>>>>>[^\n]*\n',r'\1',text,flags=re.M|re.S)
        path.write_text(text)
for name in ('items/chests/SimpleDrawer.java','storage/DrawerStorage.java'):
    path=Path('src/main/java/me/mmmjjkx/betterChests')/name
    text=path.read_text().replace('import me.mrCookieSlime.Slimefun.api.BlockStorage;\n','').replace('import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;\n','')
    seen=set();lines=[]
    for line in text.splitlines(keepends=True):
        if line.startswith('import '):
            if line in seen: continue
            seen.add(line)
        lines.append(line)
    path.write_text(''.join(lines))
path=Path('scripts/verify_doctor_drawers.py');text=path.read_text()
text=text.replace('if ERRORS:', '''require('throw new IllegalStateException("No registered Slimefun block data' in compat,
        "Modern writes without a loaded registered block must fail visibly")
require('LegacyItemStackCompat.clearToAir(item)' in simple_drawer,
        "Legacy Cargo must retain the exact in-place AIR mutation")
require('SlimefunBlockDataCompat.read(block, STORED_AMOUNT)' in ie_inspector,
        "Main's read-only IE Doctor snapshot boundary must remain")

if ERRORS:''')
path.write_text(text)
path=Path('pom.xml');text=path.read_text().replace('<version>1.0.2</version>','<version>1.0.3</version>',1)
text=text.replace('        <plugins>','        <plugins>\n            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.5.3</version></plugin>',1)
path.write_text(text)
path=Path('.github/workflows/build.yml');text=path.read_text().replace('1.0.2','1.0.3')
text=text.replace('      - name: Verify Paper 26.3 alpha compilation\n        run: mvn --batch-mode --update-snapshots -Dpaper.version=26.3-rc-3.build.1-alpha -DskipTests clean package','''      - name: Verify Paper 26.2 compilation and tests
        run: mvn --batch-mode --update-snapshots -Dpaper.version=26.2.build.129-stable clean verify
      - name: Verify Paper 26.3 compilation and tests
        run: mvn --batch-mode --update-snapshots -Dpaper.version=26.3-rc-3.build.1-alpha clean verify''')
path.write_text(text)
expected={
 'pom.xml':'0dcc3ed3c9454c0571cfffdcf656b063e066d076',
 'scripts/verify_doctor_drawers.py':'63b791e7ccff91dafd09e7f78f39519689b0d7dc',
 'src/test/java/me/mmmjjkx/betterChests/compat/SlimefunBlockCompatTest.java':'43a5276dfdb04aa0d2db69e486f533a87f403332',
 'src/main/java/me/mmmjjkx/betterChests/compat/LegacyItemStackSerialization.java':'cd18c8a58f7103fb3f51cc145f4ca37c6f45662b',
 'src/main/java/me/mmmjjkx/betterChests/compat/LegacyBlockTickerCompat.java':'54c758c0060f28a4a7fb642903dcf3e323e0497c',
 'src/main/java/me/mmmjjkx/betterChests/compat/LegacyMenuCompat.java':'18701ab0ff100f3c43b0d109589a2a9873b6b49e',
 'src/main/java/me/mmmjjkx/betterChests/compat/SlimefunBlockCompat.java':'6fa2096fa30f976be6bc0fcb8eff8006dc26601f',
 'src/main/java/me/mmmjjkx/betterChests/storage/DrawerStorage.java':'cd8f08581183c66513cca803646cc35a14865d63',
 'src/main/java/me/mmmjjkx/betterChests/diagnostics/BetterChestsDoctor.java':'047677be5a212dc85ebea717aca3fbd5b920383c',
 'src/main/java/me/mmmjjkx/betterChests/items/tools/ChestColorer.java':'5f87ace768e8925d8a6a76e67a90429160e1e679',
 'src/main/java/me/mmmjjkx/betterChests/items/tools/LocationRecorder.java':'7122a612f693dcc53b2bb39aa7b884dbd73c99d9',
 'src/main/java/me/mmmjjkx/betterChests/items/chests/SimpleChest.java':'b91b4d991549db2476e4f28f7c2eb2bc43004702',
 'src/main/java/me/mmmjjkx/betterChests/items/chests/SimpleDrawer.java':'ca25494326da91ee2a2da672e0f2d290d5d127bc',
 'src/main/java/me/mmmjjkx/betterChests/items/chests/ie/IEStorageUnit.java':'0920313bde8aa82c46c65ffcf40e0c63f7653cc6',
 'src/main/java/me/mmmjjkx/betterChests/items/chests/ie/IEStorageCache.java':'24d57337036514ea53d4450386475f8461b082c5',
 '.github/workflows/build.yml':'841862333076c0060e960fbfc32dac30bbc3e797'
}
for name,sha in expected.items():
    data=Path(name).read_bytes()
    actual=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
    assert actual==sha,(name,actual,sha)
Path('evidence').mkdir(exist_ok=True)
Path('evidence/candidate-files.json').write_text(json.dumps({'base':MAIN,'files':expected},indent=2)+'\n')
print('Verified all',len(expected),'exact candidate source blobs')
