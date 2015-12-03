#!/usr/bin/env python
# encoding: utf-8

import sys
from xml.etree import cElementTree as ElementTree
import codecs
import os.path

if __name__ == '__main__':
    directory = sys.argv[1]
    for filename in sys.argv[2:]:
        name = os.path.splitext(os.path.basename(filename))[0]
        outfile = codecs.open(
            os.path.join(directory, name+'.conll'), "wb", "utf-8")
        etree = ElementTree.parse(open(filename, 'rb'))
        for el_s in etree.getiterator('s'):
            s_id = el_s.get('n')
            n = 1
            for el in el_s:
                if el.tag == 'w':
                    wf = el.text
                    gf = el.get('lemma')
                    pos = el.get('type')
                elif el.tag == 'c':
                    wf = el.text
                    pos = el.get('type')
                    gf = wf
                else:
                    assert False

                fields = [
                    str(n), wf.replace(u"_", u" "), gf, pos, pos, '_',
                    '_', '_',   # dependency information
                    '_', '_',   # chunk information
                    '_', '_',   # NER
                    '%s:%s:%d' % (name, s_id, n),
                ]
                print >>outfile, u"\t".join(fields)
                n += 1

            print >>outfile, u""
        outfile.close()

