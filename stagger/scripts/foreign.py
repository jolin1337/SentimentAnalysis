#!/usr/bin/env python
# encoding: utf-8

"""
A tool to generate a list of foreign words from Google's web n-gram corpora.

tools/foreign.py /data0/corpora/web_1T_5-gram_v1/disk1/data/1gms/vocab_cs.gz /data1/corpora/web_1t_5gram_el/web_1t_5gram_el_v1_d[1-6]/data/*/1gms/vocab_cs.bz2 >data/web1t.embed
"""

import gzip
import bz2
import codecs
import re
import sys
import math

RE_LATIN_WORD = re.compile(
    ur'[A-Za-z\u00c0-\u00d6\u00d8-\u00f6\u00f8-\u017f]+$')

def main():
    words = {}
    for filename in sys.argv[1:]:
        if filename.endswith('.gz'):
            f = gzip.open(filename, 'rb')
        elif filename.endswith('.bz2'):
            f = bz2.BZ2File(filename, 'rb')
        else:
            assert False
        f = codecs.getreader('utf-8')(f)
        n = 0
        for line in f:
            try:
                word, freq = line.rstrip(u"\n").split(u"\t")
            except ValueError:
                continue
            word = word.lower()
            freq = int(freq)
            if RE_LATIN_WORD.match(word):
                words[word] = words.get(word, 0) + freq
                n += 1
                if n > 10000: break
        f.close()
    minfreq = float(min(words.itervalues()))
    for word, freq in words.iteritems():
        print (u"%s\t%.5f" % (word, math.log(freq/minfreq))).encode('utf-8')

if __name__ == '__main__':
    main()

