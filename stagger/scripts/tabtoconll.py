#!/usr/bin/env python

# ./tabtoconll.py file.tab >file.conll
# File.tab is assumed to be of the format "token<space(s)>tag", with blank
# lines between sentences.

import sys
tokno = 1
sentno = 1
for line in open(sys.argv[1]):
    fields = line.rstrip("\n").split()
    if len(fields) < 2:
        tokno = 0
        sentno += 1
        print
    elif len(fields) == 2:
        fields = [
            str(tokno),
            fields[0],
            '',
            fields[1],
            fields[1],
            '_', # morph
            '_',
            '_',
            '_',
            '_',
            '_',
            '_',
            '%d:%d' % (sentno, tokno)
        ]
        print "\t".join(fields)
        tokno += 1
    else:
        assert False, "%d fields!" % len(fields)

