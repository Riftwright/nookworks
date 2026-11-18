"""A small reader and writer of Minecraft's NBT (gzipped, big-endian), enough for structure files.

Values: dict = compound, list of a wrapped type = list, Byte/Short/Int/Long/Float/Double/String wrappers below, or a
plain int (Int) / str (String) / float (Double).
"""
from __future__ import annotations

import gzip
import struct


class Tagged:
    def __init__(self, value):
        self.value = value

    def __repr__(self):
        return f"{type(self).__name__}({self.value!r})"


class Byte(Tagged): pass
class Short(Tagged): pass
class Int(Tagged): pass
class Long(Tagged): pass
class Float(Tagged): pass
class Double(Tagged): pass
class List(Tagged): pass  # value: a list; element type from the first, or End when empty


def _type(v):
    if isinstance(v, Byte): return 1
    if isinstance(v, Short): return 2
    if isinstance(v, (Int, bool)) or (isinstance(v, int)): return 3
    if isinstance(v, Long): return 4
    if isinstance(v, Float): return 5
    if isinstance(v, (Double, float)): return 6
    if isinstance(v, str): return 8
    if isinstance(v, (List, list)): return 9
    if isinstance(v, dict): return 10
    raise TypeError(v)


def _payload(v, out: bytearray):
    t = _type(v)
    raw = v.value if isinstance(v, Tagged) else v
    if t == 1: out += struct.pack(">b", raw)
    elif t == 2: out += struct.pack(">h", raw)
    elif t == 3: out += struct.pack(">i", int(raw))
    elif t == 4: out += struct.pack(">q", raw)
    elif t == 5: out += struct.pack(">f", raw)
    elif t == 6: out += struct.pack(">d", raw)
    elif t == 8:
        b = raw.encode("utf-8")
        out += struct.pack(">H", len(b)) + b
    elif t == 9:
        items = list(raw)
        et = _type(items[0]) if items else 0
        out += struct.pack(">bi", et, len(items))
        for item in items:
            _payload(item, out)
    elif t == 10:
        for k, item in raw.items():
            out += struct.pack(">b", _type(item))
            kb = k.encode("utf-8")
            out += struct.pack(">H", len(kb)) + kb
            _payload(item, out)
        out += b"\x00"


def write(path, root: dict) -> None:
    out = bytearray(b"\x0a\x00\x00")
    _payload(root, out)
    with open(path, "wb") as f:
        f.write(gzip.compress(bytes(out), mtime=0))


def read(path):
    data = gzip.decompress(open(path, "rb").read())
    pos = [3]

    def take(fmt):
        n = struct.calcsize(fmt)
        v = struct.unpack_from(fmt, data, pos[0])
        pos[0] += n
        return v[0]

    def payload(t):
        if t == 1: return take(">b")
        if t == 2: return take(">h")
        if t == 3: return take(">i")
        if t == 4: return take(">q")
        if t == 5: return take(">f")
        if t == 6: return take(">d")
        if t == 7: return [take(">b") for _ in range(take(">i"))]
        if t == 8:
            n = take(">H"); s = data[pos[0]:pos[0] + n].decode("utf-8"); pos[0] += n; return s
        if t == 9:
            et = take(">b"); n = take(">i"); return [payload(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                kt = take(">b")
                if kt == 0: return d
                n = take(">H"); k = data[pos[0]:pos[0] + n].decode("utf-8"); pos[0] += n
                d[k] = payload(kt)
        if t == 11: return [take(">i") for _ in range(take(">i"))]
        if t == 12: return [take(">q") for _ in range(take(">i"))]
        raise ValueError(t)

    return payload(10)
