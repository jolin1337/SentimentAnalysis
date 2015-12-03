#!/usr/bin/env python
# encoding: utf-8

import codecs
import sys
import os.path
import re

# RE_SENT = re.compile(ur'<S ID=(\d+)>\n(.*?)\n</S>')
RE_SENT = re.compile(ur'<S ID=(\d+)>')
RE_WORD = re.compile(ur'(.+?)_([A-Z]+(?:-SHORT)?)$')

def main():
    directory = sys.argv[1]
    for filename in sys.argv[2:]:
        with codecs.open(filename, "rb", "utf-8") as f:
            fileid = os.path.basename(filename).split(".")[0]
            outname = os.path.join(directory, fileid+".conll")
            sentid = 0
            with codecs.open(outname, "wb", "utf-8") as outf:
                for line in f:
                    m = RE_SENT.match(line)
                    if m != None:
                        sentid = int(m.group(1))
                        continue
                    ms = [RE_WORD.match(s) for s in line.split()]
                    if None in ms or len(ms) == 0:
                        assert all([m == None for m in ms])
                        continue
                    tokid = 0
                    for m in ms:
                        word = m.group(1)
                        pos = m.group(2)
                        outdata = [
                            unicode(tokid+1), word, u"", pos, pos, u"_",
                            u"_", u"_", u"_", u"_", u"_", u"_",
                            u"%s:%s:%d" % (fileid, sentid, tokid)]
                        print >>outf, u"\t".join(outdata)
                        tokid += 1
                    sentid += 1
                    print >>outf
                """
                for m in RE_SENT.finditer(f.read()):
                    sentid = m.group(1)
                    tokid = 0
                    for wordtag in m.group(2).split():
                        m2 = RE_WORD.match(wordtag)
                        assert m2 != None
                        word = m2.group(1)
                        pos = m2.group(2)
                        outdata = [
                            unicode(tokid+1), word, u"", pos, pos, u"_",
                            u"_", u"_", u"_", u"_", u"_", u"_",
                            u"%s:%s:%d" % (fileid, sentid, tokid)]
                        print >>outf, u"\t".join(outdata)
                        tokid += 1
                """

if __name__ == '__main__':
    main()

