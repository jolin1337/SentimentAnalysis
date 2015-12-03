def read_sent(f):
    toks = []
    while True:
        line = f.readline()
        if line == "":
            return toks if len(toks) > 0 else None
        elif line.startswith("#"):
            continue
        elif line.endswith(u"\u2028") or line.endswith(u"\u0085") or \
             line.endswith(u"\u2029"):
            f.readline()
            continue
        elif line == "\n":
            if len(toks) > 0: return toks
        else:
            toks.append([
                s if s != '_' or i == 1 else None
                for i,s in enumerate(line.rstrip(u"\n").split(u"\t"))])

def write_sent(f, sent):
    for tok in sent:
        print >>f, u"\t".join([
            (u"" if i < 3 else u"_") if s is None else s
            for i,s in enumerate(tok)])
    print >>f

