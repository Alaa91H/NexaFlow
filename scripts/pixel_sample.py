"""Minimal pure-stdlib PNG pixel sampler (no PIL needed)."""
import struct, sys, zlib

def load_png(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not a PNG'
    pos = 8
    w = h = bitdepth = colortype = None
    idat = b''
    while pos < len(data):
        ln, typ = struct.unpack('>I4s', data[pos:pos + 8])
        chunk = data[pos + 8:pos + 8 + ln]
        if typ == b'IHDR':
            w, h, bitdepth, colortype = struct.unpack('>IIBB', chunk[:10])
        elif typ == b'IDAT':
            idat += chunk
        elif typ == b'IEND':
            break
        pos += 12 + ln
    raw = zlib.decompress(idat)
    channels = {0: 1, 2: 3, 4: 2, 6: 4}[colortype]
    stride = w * channels
    # assume 8-bit depth (launcher screenshots are RGBA8)
    out = bytearray()
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p + stride]); p += stride
        if f == 1:
            for i in range(channels, stride): line[i] = (line[i] + line[i - channels]) & 255
        elif f == 2:
            for i in range(stride): line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out += line
        prev = line
    return w, h, channels, bytes(out)

def px(w, h, channels, buf, x, y):
    off = (y * w + x) * channels
    return tuple(buf[off:off + channels])

if __name__ == '__main__':
    path, coords = sys.argv[1], sys.argv[2:]
    w, h, ch, buf = load_png(path)
    for c in coords:
        x, y = map(int, c.split(','))
        r, g, b, *rest = px(w, h, ch, buf, x, y)
        print(f'({x},{y}) = #{r:02X}{g:02X}{b:02X}')
