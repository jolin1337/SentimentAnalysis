#!/usr/bin/env python
# encoding: utf-8

import sys, glob, os.path, codecs
import numpy as np
from operator import itemgetter

# ./stats.py gold-pattern train-pattern
#            evaluation1-pattern evaluation2-pattern|_ [lexicon]
#
# The first four parameters are (quoted) patterns, e.g. 'gold/train-*.conll'
# evaluation2-pattern may be _ if there is only one evaluation, otherwise the
# two evaluations will be compared.
#
# The last (optional) parameter is the filename of a Stagger-format external
# lexicon.
#
# time scripts/stats.py 'corpora/suc3/fold*-test.conll'
#                       'corpora/suc3/fold*-train.conll'
#                       '4ff6420-suc3-nosaldo/fold*-test.conll' _
#                        >4ff6420-suc3-nosaldo/stats2.txt
#

def read_file(filename):
    conll = False
    sents = []
    print >>sys.stderr, "Reading %s..." % filename
    with codecs.open(filename, "rb", "utf-8") as f:
        sent = []
        for line in f:
            fields = line.rstrip(u"\n").split(u"\t")
            if len(fields) > 3 and not conll:
                print >>sys.stderr, "%s: CoNLL format detected" % filename
                conll = True
            if line[0] == u"#": continue
            if line.strip() == u"":
                sents.append(sent)
                sent = []
                continue
            if conll:
                token = (fields[1],
                    fields[4]+u"|"+fields[5] \
                        if fields[5] != "_" and fields[5] != "" \
                        else fields[4])
            else:
                token = (fields[0], fields[1])
            sent.append(token)
        if len(sent) > 0:
            sents.append(sent)
    return sents

def read_lexicon(filename):
    print >>sys.stderr, "Reading lexicon file %s..." % filename
    return set([line.split(u"\t")[0].lower()
                for line in codecs.open(filename, "rb", "utf-8")])

def compare_files(gold_filename, test_filename, lexicon, outf=None,
                  tag_freqs=None, error_freqs=None):
    gold_sents = read_file(gold_filename)
    test_sents = read_file(test_filename)

    # correct unknown       correct known
    # total unknown         total known
    result = np.zeros((2,2), dtype='int32')

    if len(gold_sents) == len(test_sents) + 1:
        print >>sys.stderr, "WARNING: %s truncated" % gold_filename
        gold_sents = gold_sents[:-1]

    assert len(gold_sents) > 0
    assert len(gold_sents) == len(test_sents)
    for gold_sent, test_sent in zip(gold_sents, test_sents):
        assert len(gold_sent) == len(test_sent)
        for gold_token, test_token in zip(gold_sent, test_sent):
            assert gold_token[0] == test_token[0]
            if not tag_freqs is None:
                tag_freqs[(gold_token[1], test_token[1])] = \
                    tag_freqs.get((gold_token[1], test_token[1]), 0)+1
            correct = (gold_token[1] == test_token[1])
            known = (gold_token[0].lower() in lexicon)
            if correct:
                result[0,int(known)] += 1
            result[1,int(known)] += 1
            if not outf is None and not correct:
                print >>outf, u"ERROR:\t%s\t%s\t%s\t%s\t%s" % (
                    gold_token[0], gold_token[1], test_token[0],
                    test_token[1], u"<KNOWN>" if known else u"<UNKNOWN>")
            if not correct and not error_freqs is None:
                error = (gold_token[0], gold_token[1], test_token[1])
                error_freqs[error] = error_freqs.get(error, 0) + 1
    return result

def report_errors(f, tag_freqs, freqs, treshold=5):
    total = sum([n for e,n in freqs.iteritems()])
    table = sorted([(e,n) for e,n in freqs.iteritems() if n >= treshold],
                   key=itemgetter(1), reverse=True)
    for (token, good_tag, bad_tag), n in table:
        print >>f, u"%4d %6s%% %-16s %-24s %-24s" % (
            n, "%.3f" % (100.0*n/float(total)), token, good_tag, bad_tag)
    print >>f

    word_freqs = {}
    for (token, _, _), n in freqs.iteritems():
        error = token.lower()
        word_freqs[error] = word_freqs.get(error, 0) + n
    table = sorted([(e,n) for e,n in word_freqs.iteritems() if n >= treshold],
                   key=itemgetter(1), reverse=True)
    for token, n in table:
        print >>f, u"%4d %6s%% %s" % (
            n, "%.3f" % (100.0*n/float(total)), token)
    print >>f

    pos_freqs = {}
    for (_, good_tag, bad_tag), n in freqs.iteritems():
        error = (good_tag, bad_tag)
        pos_freqs[error] = pos_freqs.get(error, 0) + n
    table = sorted([(e,n) for e,n in pos_freqs.iteritems() if n >= treshold],
                   key=itemgetter(1), reverse=True)
    for (good_tag, bad_tag), n in table:
        print >>f, u"%4d %6s%% %-30s %-30s" % (
            n, "%.3f" % (100.0*n/float(total)), good_tag, bad_tag)
    print >>f

    spos_exist = {}
    spos_found = {}
    spos_correct = {}
    for (good_tag, set_tag), n in tag_freqs.iteritems():
        good_pos = good_tag.split("|")[0]
        set_pos = set_tag.split("|")[0]
        if good_pos == set_pos:
            spos_correct[good_pos] = spos_correct.get(good_pos, 0) + n
        spos_exist[good_pos] = spos_exist.get(good_pos, 0) + n
        spos_found[set_pos] = spos_found.get(set_pos, 0) + n
    p_r = [(spos,
            float(spos_correct[spos])/spos_found[spos],
            float(spos_correct[spos])/spos_exist[spos])
           for spos in sorted(spos_exist.keys())]
    p_r.sort(key=lambda (spos, p, r): 2.0*(p*r)/(p+r), reverse=True)
    for spos, p, r in p_r:
        print >>f, u"%-4s & %.2f\\%% & %.2f\\%% & %.2f\\%% \\\\" % (
            spos, 100*p, 100*r, 100*2.0*p*r/(p+r))
    print >>f


def report(name, f, result):
    print >>f, "Evaluation of %s" % name
    unknown_correct = int(result[0,0])
    total_unknown = int(result[1,0])
    known_correct = int(result[0,1])
    total_known = int(result[1,1])
    total_correct = unknown_correct + known_correct
    total_words = total_known + total_unknown
    print >>f, "    Total:   %.02f%% (%d/%d)" % (
        100.0*float(total_correct)/total_words, total_correct, total_words)
    if total_known > 0:
        print >>f, "    Known:   %.02f%% (%d/%d)" % (
            100.0*float(known_correct)/total_known, known_correct, total_known)
    if total_unknown > 0:
        print >>f, "    Unknown: %.02f%% (%d/%d)" % (
            100.0*float(unknown_correct)/total_unknown,
            unknown_correct, total_unknown)
    print >>f, "    UWR:     %.02f%% (%d/%d)" % (
        100.0*float(total_unknown)/total_words, total_unknown, total_words)
    print >>f

def compare_files_detailed(
gold_filename, test_filename, lexicon, fold,  outf=None):
    gold_sents = read_file(gold_filename)
    test_sents = read_file(test_filename)

    # correct unknown       correct known
    # total unknown         total known
    result = [[set(), set()], [set(), set()]]

    if len(gold_sents) == len(test_sents) + 1:
        print >>sys.stderr, "WARNING: %s truncated" % gold_filename
        gold_sents = gold_sents[:-1]

    assert len(gold_sents) > 0
    assert len(gold_sents) == len(test_sents)
    counter = 0
    for gold_sent, test_sent in zip(gold_sents, test_sents):
        assert len(gold_sent) == len(test_sent)
        for gold_token, test_token in zip(gold_sent, test_sent):
            assert gold_token[0] == test_token[0]
            correct = (gold_token[1] == test_token[1])
            known = (gold_token[0].lower() in lexicon)
            token = (fold, counter)
            counter += 1
            if correct:
                result[0][int(known)].add(token)
            result[1][int(known)].add(token)
            if not outf is None and not correct:
                print >>outf, u"ERROR:\t%s\t%s\t%s\t%s\t%s" % (
                    gold_token[0], gold_token[1], test_token[0],
                    test_token[1], u"<KNOWN>" if known else u"<UNKNOWN>")
    return result

def report_detailed(name, f, result, result2):
    def detailed_to_summary(detailed):
        return np.array([[len(s) for s in row] for row in detailed],
                        dtype='int32')

    report("Test A", f, detailed_to_summary(result))
    report("Test B", f, detailed_to_summary(result2))

    all_correct1 = result[0][0] | result[0][1]
    all_incorrect1 = (result[1][0] | result[1][1]) - all_correct1
    all_correct2 = result2[0][0] | result2[0][1]
    all_incorrect2 = (result2[1][0] | result2[1][1]) - all_correct2

    a = float(len(all_correct1 & all_correct2))
    b = float(len(all_correct1 & all_incorrect2))
    c = float(len(all_correct2 & all_incorrect1))
    d = float(len(all_incorrect1 & all_incorrect2))

    print """
             Correct B  Incorrect B
Correct A    %9d   %11d
Incorrect A  %9d   %11d
""" % (a,b,c,d)

    chi2 = ((b-c)*(b-c)) / (b+c)
    if chi2 > 10.83: print "chi2 = %g (p < 0.001)" % chi2
    elif chi2 > 6.64: print "chi2 = %g (p < 0.01)" % chi2
    elif chi2 > 3.84: print "chi2 = %g (p < 0.05)" % chi2
    else: print "chi2 = %g (p > 0.05)" % chi2

def main():
    gold_pattern = sys.argv[1]
    train_pattern = sys.argv[2]
    test_pattern = sys.argv[3]
    test2_pattern = None if sys.argv[4] == '_' else sys.argv[4]
    lexicon_path = sys.argv[5] if len(sys.argv) > 5 else None

    gold_files = sorted(glob.glob(gold_pattern))
    test_files = sorted(glob.glob(test_pattern))
    test2_files = None if test2_pattern is None else \
                  sorted(glob.glob(test2_pattern))
    train_files = sorted(glob.glob(train_pattern))

    assert len(gold_files) == len(test_files)
    assert test2_pattern is None or len(gold_files) == len(test2_files)
    assert len(gold_files) == len(train_files)

    lexicon = set() if lexicon_path is None else read_lexicon(lexicon_path)
    print >>sys.stderr, "Read %d lexicon entries." % len(lexicon)

    error_freqs = {}
    tag_freqs = {}

    if test2_pattern is None:
        # correct unknown       correct known
        # total unknown         total known
        sum_result = np.zeros((2,2), dtype='int32')
        print >>sys.stderr, "%d files to test" % len(test_files)

        for gold_file, test_file, train_file \
        in zip(gold_files, test_files, train_files):
            local_lexicon = frozenset([
                tok[0].lower()
                for sent in read_file(train_file)
                for tok in sent])
            print >>sys.stderr, "%d local lexicon entries." % len(local_lexicon)

            #with codecs.open(test_file+".errors", "wb", "utf-8") as outf:
            #    local_result = compare_files(
            #        gold_file, test_file, lexicon | local_lexicon, outf)
            local_result = compare_files(
                gold_file, test_file, lexicon | local_lexicon, None,
                tag_freqs, error_freqs)
            report(test_file, sys.stdout, local_result)
            sum_result += local_result

        report("GLOBAL", sys.stdout, sum_result)
        report_errors(codecs.getwriter('utf-8')(sys.stdout),
                      tag_freqs, error_freqs)
    else:
        # correct unknown       correct known
        # total unknown         total known
        test_result = [[set(), set()], [set(), set()]]
        test2_result = [[set(), set()], [set(), set()]]

        for fold, (gold_file, test_file, test2_file, train_file) \
        in enumerate(zip(gold_files, test_files, test2_files, train_files)):
            local_lexicon = frozenset([
                tok[0].lower()
                for sent in read_file(train_file)
                for tok in sent])
            print >>sys.stderr, "%d local lexicon entries." % len(local_lexicon)

            local_result = compare_files_detailed(
                gold_file, test_file, lexicon | local_lexicon, fold, None)
            local2_result = compare_files_detailed(
                gold_file, test2_file, lexicon | local_lexicon, fold, None)
            for i in range(2):
                for j in range(2):
                    test_result[i][j] |= local_result[i][j]
                    test2_result[i][j] |= local2_result[i][j]
            
            report_detailed("%s vs %s" % (test_file, test2_file), sys.stdout,
                            local_result, local2_result)

        report_detailed("GLOBAL", sys.stdout, test_result, test2_result)

if __name__ == '__main__': main()


