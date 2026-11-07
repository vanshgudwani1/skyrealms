import gzip
import struct

def parse_and_clean():
    path_in = r"C:\Users\vansh\Downloads\dragonnest (1).schematic"
    path_out = r"C:\servermc\plugins\FastAsyncWorldEdit\schematics\dragonnest.schematic"
    
    with gzip.open(path_in, 'rb') as f:
        data = bytearray(f.read())
        
    offset = 0

    def read_byte():
        nonlocal offset
        b = data[offset]
        offset += 1
        return b

    def read_short():
        nonlocal offset
        s = struct.unpack('>h', data[offset:offset+2])[0]
        offset += 2
        return s

    def read_int():
        nonlocal offset
        i = struct.unpack('>i', data[offset:offset+4])[0]
        offset += 4
        return i

    def read_string():
        nonlocal offset
        l = read_short()
        s = data[offset:offset+l].decode('utf-8', errors='replace')
        offset += l
        return s

    def skip_tag(tag_type):
        nonlocal offset
        if tag_type == 0:
            return
        elif tag_type == 1:
            offset += 1
        elif tag_type == 2:
            offset += 2
        elif tag_type == 3:
            offset += 4
        elif tag_type == 4:
            offset += 8
        elif tag_type == 5:
            offset += 4
        elif tag_type == 6:
            offset += 8
        elif tag_type == 7: # ByteArray
            l = read_int()
            offset += l
        elif tag_type == 8: # String
            l = read_short()
            offset += l
        elif tag_type == 9: # List
            etype = read_byte()
            l = read_int()
            for _ in range(l):
                skip_tag(etype)
        elif tag_type == 10: # Compound
            while True:
                et = read_byte()
                if et == 0:
                    break
                read_string()
                skip_tag(et)
        elif tag_type == 11:
            l = read_int()
            offset += l * 4
        elif tag_type == 12:
            l = read_int()
            offset += l * 8

    # We want to find the "Entities" tag in the root compound and set its length to 0!
    # In NBT, TAG_List for Entities is:
    # 0x09 (TAG_List), string "Entities", 0x0A (TAG_Compound element type), 4 bytes length
    entities_pattern = b'\x09\x00\x08Entities\x0a'
    idx = data.find(entities_pattern)
    if idx != -1:
        length_offset = idx + len(entities_pattern)
        old_len = struct.unpack('>i', data[length_offset:length_offset+4])[0]
        print(f"Found Entities list at offset {idx} with {old_len} items.")
        
        # Read the tags to find where this list ends
        offset = length_offset + 4
        for _ in range(old_len):
            skip_tag(10) # each item is Compound
        end_offset = offset
        
        # Replace the list with an empty list (length = 0)
        # TAG_List header is idx ... length_offset + 4
        # We set length to 0 and remove the elements
        new_data = data[:length_offset] + struct.pack('>i', 0) + data[end_offset:]
        print(f"Entities stripped! Old size: {len(data)}, new size: {len(new_data)}")
        
        with gzip.open(path_out, 'wb') as f_out:
            f_out.write(new_data)
        print(f"Saved cleaned schematic to: {path_out}")
    else:
        print("Entities pattern not found!")

parse_and_clean()
