"""Reads and writes Xiaomi Smart Band 8 face files (.bin) without the Windows compiler.

The file is a head (header, tables, widgets) followed by pictures: plain images (type 2, RGB565)
and picture lists (type 3, BGRA with alpha), then the preview. Pictures are LVGL run-length coded.
    python3 facebin.py check <face.bin>         rebuilds the file and compares
    python3 facebin.py demo <face.bin> <out.bin>  green background, red hour digits, id 266291999
"""
import struct,sys
def u32(b,o): return struct.unpack_from('<I',b,o)[0]
def parse(b):
    pieces=[]
    for t in (2,3):
        cnt=u32(b,0xb0+8*t); off=u32(b,0xb4+8*t)
        for i in range(cnt):
            e=off+16*i
            pieces.append([t,i,e,u32(b,e+8),u32(b,e+12)])
    pv=u32(b,0x20)
    pieces.append(['p',0,-1,pv,12+u32(b,pv+8)])
    pieces.sort(key=lambda p:p[3])
    head=b[:pieces[0][3]]
    end=pieces[-1][3]+pieces[-1][4]
    data={ (p[0],p[1]): b[p[3]:p[3]+p[4]] for p in pieces}
    return head,pieces,data,b[end:]
def rle_dec(d):
    chunk=d[4]&0xf; out=bytearray(); p=8
    while p<len(d):
        c=d[p];p+=1;n=c&0x7f
        if c&0x80: out+=d[p:p+chunk*n];p+=chunk*n
        else: out+=d[p:p+chunk]*n;p+=chunk
    return bytes(out)
def rle_enc(raw,chunk):
    out=bytearray(b'\xe0\x21\xa5\x5a')+struct.pack('<I',(len(raw)<<4)|chunk)
    n=len(raw)//chunk; i=0
    px=lambda k: raw[k*chunk:(k+1)*chunk]
    while i<n:
        r=1
        while i+r<n and r<127 and px(i+r)==px(i): r+=1
        if r>=2:
            out.append(r); out+=px(i); i+=r; continue
        j=i
        while j<n and j-i<127 and not (j+1<n and px(j+1)==px(j)): j+=1
        if j==i: j=i+1
        out.append(0x80|(j-i)); out+=raw[i*chunk:j*chunk]; i=j
    return bytes(out)
def build(head,pieces,data,tail):
    out=bytearray(head)
    for p in sorted(pieces,key=lambda p:p[3]):
        while len(out)%4: out.append(0)
        off=len(out); d=data[(p[0],p[1])]
        out+=d
        if p[2]>=0: struct.pack_into('<II',out,p[2]+8,off,len(d))
        else: struct.pack_into('<I',out,0x20,off); struct.pack_into('<I',out,0xac,off)
    return bytes(out+tail)
def demo(src, dst):
    b = open(src, 'rb').read()
    h, p, d, t = parse(b)
    img = d[(2, 0)]
    w, hh = struct.unpack_from('<HH', img, 4)
    raw = bytearray()
    for y in range(hh):
        k = y / (hh - 1)
        r, g, bb = int(20 * (1 - k)), int(40 + 120 * k), int(160 * (1 - k) + 40 * k)
        raw += struct.pack('<H', ((r >> 3) << 11) | ((g >> 2) << 5) | (bb >> 3)) * w
    enc = rle_enc(bytes(raw), 2)
    d[(2, 0)] = img[:8] + struct.pack('<I', len(enc)) + enc
    lst = d[(3, 0)]
    cnt = lst[1]
    sizes = [u32(lst, 12 + 4 * i) for i in range(cnt)]
    q = 8 + 4 * (cnt + 1)
    frames = []
    for s in sizes:
        raw = bytearray(rle_dec(lst[q:q + s]))
        q += s
        for i in range(0, len(raw), 4):
            raw[i], raw[i + 1], raw[i + 2] = 0x40, 0x40, 0xff
        frames.append(rle_enc(bytes(raw), 4))
    d[(3, 0)] = lst[:8] + struct.pack('<I', sum(map(len, frames))) + b''.join(struct.pack('<I', len(f)) for f in frames) + b''.join(frames)
    out = bytearray(build(h, p, d, t))
    out[0x28:0x38] = b'266291999' + bytes(7)
    open(dst, 'wb').write(out)


if __name__ == '__main__':
    if sys.argv[1] == 'check':
        b = open(sys.argv[2], 'rb').read()
        print('same' if build(*parse(b)) == b else 'DIFFERENT')
    else:
        demo(sys.argv[2], sys.argv[3])
