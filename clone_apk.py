"""Make an isolated local test package while preserving Gboard's original code.

Only the package identity, local test label, backup/debug flags and network permission
change here. Chinese hooks are added separately. Input is the user's installed APKs.
"""
from pathlib import Path
import hashlib
import struct
import zlib
import zipfile

ROOT = Path(__file__).resolve().parent
OLD = "com.google.android.inputmethod.latin"
NEW = "com.localx.android.inputmethod.latin"
assert len(OLD) == len(NEW)
U32 = lambda data, offset: struct.unpack_from("<I", data, offset)[0]


def length8(data, offset):
    first = data[offset]
    return (((first & 127) << 8) | data[offset + 1], offset + 2) if first & 128 else (first, offset + 1)


def encode8(value):
    return bytes((128 | (value >> 8), value & 255)) if value > 127 else bytes((value,))


def decode_pool(data):
    count, styles, flags, start, style_start = struct.unpack_from("<IIIII", data, 8)
    header = struct.unpack_from("<H", data, 2)[0]
    strings = []
    for index in range(count):
        offset = start + U32(data, header + index * 4)
        if flags & 256:
            _, offset = length8(data, offset)
            size, offset = length8(data, offset)
            strings.append(data[offset:offset + size].decode("utf-8"))
        else:
            size = struct.unpack_from("<H", data, offset)[0]
            offset += 2
            if size & 32768:
                size = ((size & 32767) << 16) | struct.unpack_from("<H", data, offset)[0]
                offset += 2
            strings.append(data[offset:offset + size * 2].decode("utf-16-le"))
    assert styles == 0, "Styled manifest string pools need explicit handling"
    return strings, flags


def encode_pool(strings, flags):
    offsets, contents = [], bytearray()
    for value in strings:
        offsets.append(len(contents))
        utf16_size = len(value.encode("utf-16-le")) // 2
        if flags & 256:
            encoded = value.encode("utf-8")
            contents += encode8(utf16_size) + encode8(len(encoded)) + encoded + b"\0"
        else:
            assert utf16_size < 32768
            contents += struct.pack("<H", utf16_size) + value.encode("utf-16-le") + b"\0\0"
    contents += b"\0" * (-len(contents) % 4)
    start = 28 + len(strings) * 4
    return (struct.pack("<HHI", 1, 28, start + len(contents))
            + struct.pack("<IIIII", len(strings), 0, flags, start, 0)
            + b"".join(struct.pack("<I", item) for item in offsets) + contents)


def patch_manifest(raw, base):
    chunks = []
    offset = struct.unpack_from("<H", raw, 2)[0]
    while offset < len(raw):
        size = U32(raw, offset + 4)
        chunks.append(bytearray(raw[offset:offset + size]))
        offset += size
    pool_index = next(i for i, chunk in enumerate(chunks) if struct.unpack_from("<H", chunk)[0] == 1)
    strings, flags = decode_pool(chunks[pool_index])
    strings = [value.replace(OLD, NEW) for value in strings]
    android_ns = strings.index("http://schemas.android.com/apk/res/android")
    label_index = len(strings)
    strings.append("Gboard 中文增强（测试）")
    debug_name = strings.index("debuggable") if "debuggable" in strings else len(strings)
    if debug_name == len(strings):
        strings.append("debuggable")
    map_index = next(i for i, chunk in enumerate(chunks) if struct.unpack_from("<H", chunk)[0] == 0x180)
    resource_map = [U32(chunks[map_index], i) for i in range(8, len(chunks[map_index]), 4)]
    resource_map += [0] * max(0, debug_name + 1 - len(resource_map))
    resource_map[debug_name] = 0x0101000f
    chunks[map_index] = bytearray(struct.pack("<HHI", 0x180, 8, 8 + 4 * len(resource_map))
                                  + b"".join(struct.pack("<I", value) for value in resource_map))
    result = []
    skipped_depth = 0
    for chunk in chunks:
        kind = struct.unpack_from("<H", chunk)[0]
        if kind == 0x102:
            name = strings[U32(chunk, 20)]
            start, width, count = struct.unpack_from("<HHH", chunk, 24)
            assert width == 20
            attributes = [bytearray(chunk[16 + start + index * width:16 + start + (index + 1) * width])
                          for index in range(count)]
            internet = False
            for attr in attributes:
                attr_name = strings[U32(attr, 4)]
                value_type = attr[15]
                if name == "uses-permission" and attr_name == "name" and value_type == 3:
                    internet = strings[U32(attr, 16)] == "android.permission.INTERNET"
                if base and name == "application":
                    if attr_name == "label":
                        struct.pack_into("<I", attr, 8, label_index)
                        attr[15] = 3
                        struct.pack_into("<I", attr, 16, label_index)
                    elif attr_name == "allowBackup":
                        struct.pack_into("<I", attr, 8, 0xffffffff)
                        attr[15] = 0x12
                        struct.pack_into("<I", attr, 16, 0)
                    elif attr_name == "debuggable":
                        attr[15] = 0x12
                        struct.pack_into("<I", attr, 16, 0xffffffff)
            if base and name == "application" and not any(strings[U32(attr, 4)] == "debuggable" for attr in attributes):
                attributes.append(bytearray(struct.pack("<IIIHBBI", android_ns, debug_name, 0xffffffff, 8, 0, 0x12, 0xffffffff)))
                count += 1
                struct.pack_into("<H", chunk, 28, count)
            if base and name == "application":
                # Android's compiled-XML attribute lookup expects resource IDs
                # in ascending order, including the newly added debug flag.
                attributes.sort(key=lambda attr: resource_map[U32(attr, 4)] if U32(attr, 4) < len(resource_map) else 0)
                chunk = chunk[:16 + start] + b"".join(attributes)
                struct.pack_into("<I", chunk, 4, len(chunk))
            if skipped_depth or internet:
                skipped_depth += 1
                continue
        elif kind == 0x103 and skipped_depth:
            skipped_depth -= 1
            continue
        if not skipped_depth:
            result.append(chunk)
    result[result.index(chunks[pool_index])] = encode_pool(strings, flags)
    body = b"".join(result)
    return struct.pack("<HHI", 3, 8, 8 + len(body)) + body


def patch_identity(data):
    return data.replace(OLD.encode(), NEW.encode()).replace(OLD.encode("utf-16-le"), NEW.encode("utf-16-le"))


def patch_dex(data):
    data = bytearray(patch_identity(data))
    # Keep certificate validation, recognizing only this locally signed build.
    upstream = bytes(v & 255 for v in [16,57,56,-18,69,55,-27,-98,-114,-25,-110,-10,84,80,79,-72,52,111,-58,-77,70,-48,-69,-60,65,95,-61,57,-4,-4,-114,-63])
    from build_config import certificate_digest
    local = certificate_digest()
    data = data.replace(upstream, local)
    data[12:32] = hashlib.sha1(data[32:]).digest()
    struct.pack_into("<I", data, 8, zlib.adler32(data[12:]) & 0xffffffff)
    return data


def main():
    source = ROOT / "gboard-18.4.1"
    output = ROOT / "enhanced" / "unsigned"
    output.mkdir(parents=True, exist_ok=True)
    for apk in source.glob("*.apk"):
        with zipfile.ZipFile(apk) as original, zipfile.ZipFile(output / apk.name, "w") as target:
            for info in original.infolist():
                if info.filename.startswith("META-INF/") and info.filename.endswith((".RSA", ".SF", ".MF")):
                    continue
                data = original.read(info)
                if info.filename == "AndroidManifest.xml":
                    data = patch_manifest(data, apk.name == "base.apk")
                elif info.filename == "resources.arsc":
                    data = patch_identity(data)
                elif info.filename.endswith(".dex"):
                    data = patch_dex(data)
                target.writestr(info, data)
        print(apk.name)


if __name__ == "__main__":
    main()
