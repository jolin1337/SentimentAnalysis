#!/usr/bin/env python

import struct, sys, socket

if __name__ == '__main__':
    ip = sys.argv[1]
    port = int(sys.argv[2])
    with open(sys.argv[3], "rb") as f: data = f.read()
    header = struct.pack('>i', len(data))
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.connect((ip, port))
    s.sendall(header + data)
    while True:
        part = s.recv(0x1000)
        if len(part) <= 0: break
        sys.stdout.write(part)
    s.close()

