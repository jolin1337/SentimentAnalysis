#!/usr/bin/env python

import codecs
import sys
import os.path

def main():
    directory = sys.argv[1]
    for filename in sys.argv[2:]:
        with codecs.open(filename, "rb", "utf-8") as f:
            outname = os.path.join(directory, os.path.basename(filename))
            fileid = os.path.basename(filename).split(".")[0]
            with codecs.open(outname, "wb", "utf-8") as outf:
                sentid = 0
                for line in f:
                    fields = line.rstrip(u"\n").split(u"\t")
                    if len(fields) <= 1:
                        print >>outf
                        sentid += 1
                        continue
                    nr = fields[0]
                    form = fields[1]
                    pos = fields[3]
                    head = fields[6]
                    deprel = fields[7]
                    outdata = [
                        nr, form, u"", pos, pos, u"_",
                        head, deprel, u"_", u"_", u"_", u"_",
                        u"%s:%d:%s" % (fileid, sentid, nr)]
                    print >>outf, u"\t".join(outdata)

if __name__ == '__main__':
    main()

