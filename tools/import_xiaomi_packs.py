"""Bundle checksum-pinned original APKs losslessly, sharing identical ZIP payloads."""
from pathlib import Path
import argparse, hashlib, json, lzma, struct, zipfile

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/assets/xiaomi'
MAX_CHUNK=4*1024*1024
MIN_ENTRY=64*1024

def segments(raw):
    # Preserve every byte, including signatures, alignment, ZIP headers and footer.
    # Splitting at large entry boundaries lets identical native libs/images share
    # storage despite having different offsets or names in their original APKs.
    import io
    boundaries={0,len(raw)}
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        for entry in archive.infolist():
            if entry.compress_size<MIN_ENTRY:continue
            name,extra=struct.unpack_from('<HH',raw,entry.header_offset+26)
            start=entry.header_offset+30+name+extra
            boundaries.update((start,start+entry.compress_size))
    points=sorted(boundaries)
    for first,last in zip(points,points[1:]):
        for offset in range(first,last,MAX_CHUNK):yield raw[offset:min(last,offset+MAX_CHUNK)]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('scenes',type=Path);args=parser.parse_args()
    catalog=json.loads((ASSETS/'catalog.json').read_text(encoding='utf-8'))
    pool=ASSETS/'packs';pool.mkdir(exist_ok=True)
    manifest={'format':1,'max_chunk_bytes':MAX_CHUNK,'packs':{}}
    unique={};raw_total=0;references=0
    for pack in catalog:
        raw=(args.scenes/(pack['id']+'.apk')).read_bytes()
        assert len(raw)==pack['bytes'] and hashlib.sha256(raw).hexdigest()==pack['sha256'],pack['id']
        recipe=[];raw_total+=len(raw)
        for chunk in segments(raw):
            digest=hashlib.sha256(chunk).hexdigest();recipe.append([digest,len(chunk)]);references+=1
            if digest in unique:continue
            path=pool/(digest+'.xz')
            if path.is_file():
                encoded=path.read_bytes();assert lzma.decompress(encoded)==chunk,path
            else:
                encoded=lzma.compress(chunk,format=lzma.FORMAT_XZ,check=lzma.CHECK_CRC64,
                    filters=[{'id':lzma.FILTER_LZMA2,'preset':6,'dict_size':MAX_CHUNK}])
                assert lzma.decompress(encoded)==chunk
                path.write_bytes(encoded)
            unique[digest]=len(encoded)
        manifest['packs'][pack['id']]={'sha256':pack['sha256'],'bytes':len(raw),'chunks':recipe}
        print(pack['id'],len(recipe),'chunks; exact original APK retained',flush=True)
    assert {p.name for p in pool.iterdir()}=={key+'.xz' for key in unique},'Unexpected or obsolete generated chunk'
    manifest['storage']={'original_apk_bytes':raw_total,'compressed_unique_bytes':sum(unique.values()),
        'unique_chunks':len(unique),'chunk_references':references}
    (ASSETS/'packs.json').write_text(json.dumps(manifest,separators=(',',':'))+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(manifest['storage'],indent=2))

if __name__=='__main__':main()
