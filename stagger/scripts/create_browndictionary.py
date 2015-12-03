#!/usr/bin/env python
# encoding: utf-8

# Clusters can be created by Percy Liang's program:
# time ~/local/brown-cluster-1.2/wcluster --c 100 --min-occur 10 --text
#    twingly-40M.tok --max-ind-level 3 | tee twingly-40M.log

import codecs
import sys
import math
import re

#RE_WORD = re.compile(ur'[0-9a-zA-ZåäöéÅÄÖ]+(-[a-zA-ZåäöéÅÄÖ]+)?$')
clustermap = {}

limit = int(sys.argv[1])
for line in codecs.getreader('utf-8')(sys.stdin):
    fields = line.rstrip(u"\n").split(u"\t")
    if len(fields) != 3: continue
    cluster = fields[0]
    word = fields[1]
    n = int(fields[2])
    if n < limit: continue
    # if RE_WORD.match(word) == None: continue
    # word = word.lower()
    if len(word) > 30: continue
    if word in clustermap:
        cluster2, n2 = clustermap[word]
        if n2 >= n: continue
    clustermap[word] = (cluster, n)

for word, (cluster, n) in clustermap.iteritems():
    print (u"%s\t%s" % (word, cluster)).encode('utf-8')

