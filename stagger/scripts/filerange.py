#!/usr/bin/env python

"""
A simple tool to select certain parts of the Chinese Treebank.

cat `tools/filerange.py 271-300:528-554:594-596:1040-1042 ctb/chtb_*.conll` >ctb/test.conll
"""

import sys
import re

RE_NUMBER = re.compile(r'(\d+)')

ranges = [xrange(int(s.split("-")[0]), int(s.split("-")[1])+1)
          for s in sys.argv[1].split(":")]

for filename in sys.argv[2:]:
    m = RE_NUMBER.search(filename)
    assert m != None
    x = int(m.group(1))
    if any([x in r for r in ranges]):
        print filename

