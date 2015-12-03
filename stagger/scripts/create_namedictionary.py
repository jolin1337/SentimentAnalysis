#!/usr/bin/env python
# encoding: utf-8

"""
time scripts/create_namedictionary.py 1:/home/robert/corpora/namn/sverige-efternamn.txt 2:/home/robert/corpora/namn/sverige-kvinnor.txt,/home/robert/corpora/namn/sverige-maen.txt 3:/data0/corpora/geonames/SE.txt >~/data/stagger/dict/names.dict
"""

import codecs
import sys
import os.path

def import_person_name(f, sym, names):
    for line in f:
        fields = line.rstrip(u"\n").split(u"\t")
        n = float(fields[4])
        if n < 10: continue
        for name in fields[5].split():
            if name in names:
                if n > names[name][0]: names[name] = (n, sym)
            else:
                names[name] = (n, sym)

def import_geonames(f, sym, names):
    places = {}
    for line in f:
        fields = line.rstrip(u"\n").split(u"\t")
        name = fields[1].strip()
        kind = fields[7].strip()
        n = int(fields[14])
        if not (n>50 and (kind.startswith('PPL') or kind.startswith('ADM'))):
            continue
        if n > places.get(name,0): places[name] = n

    forbidden = [u"län", u"kommun", u"socken"]
    for place, n in places.items():
        for name in place.split():
            if name.lower() in forbidden: continue
            if u"." in name: continue
            if name in names:
                if n > names[name][0]: names[name] = (n, sym)
            else:
                names[name] = (n, sym)


if __name__ == '__main__':
    outf = codecs.getwriter('utf-8')(sys.stdout)
    names = {}
    for arg in sys.argv[1:]:
        assert arg[1] == ':'
        sym = arg[0]
        for filename in arg[2:].split(','):
            with codecs.open(filename, 'rb', 'utf-8') as f:
                if os.path.basename(filename).startswith('sverige-'):
                    print >>sys.stderr, "Name file: %s" % filename
                    import_person_name(f, sym, names)
                else:
                    print >>sys.stderr, "Geonames file: %s" % filename
                    import_geonames(f, sym, names)
    for name, (n, sym) in sorted(names.items()):
        print >>outf, u"%s\t%s" % (name, sym)

