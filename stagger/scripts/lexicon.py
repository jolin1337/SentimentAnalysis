#!/usr/bin/env python

from xml.etree import cElementTree as ElementTree
from conll import read_sent
import re

def is_open_tag(tag):
    return tag.split('|')[0] in ['NN','VB','JJ','AB','PC','RG','RO','PM']

def generate_tags_rec(m):
    if m == []: return [[]]
    tags = []
    for suffix in generate_tags_rec(m[1:]):
        for prefix in m[0]:
            tags.append([prefix] + suffix)
    return tags

def generate_tags(pos, m):
    return [(pos, tags) for tags in generate_tags_rec(m)]

def preprocess_forms(pos, lemgram_forms):
    if pos in ['av', 'ava']:
        param_wf = dict([(" ".join(param), wf) for param, wf in lemgram_forms])
        wf1 = param_wf.get('pos indef pl nom', None)
        wf2 = param_wf.get('pos def pl nom', None)
        if wf1 == wf2 and wf1 != None:
            lemgram_forms.remove((['pos', 'indef', 'pl', 'nom'], wf1))
            lemgram_forms.remove((['pos', 'def', 'pl', 'nom'], wf1))
            lemgram_forms.append((['pos', 'pl', 'nom'], wf1))
        param_wf = dict([(" ".join(param), wf) for param, wf in lemgram_forms])
        wf1 = param_wf.get('pos indef pl gen', None)
        wf2 = param_wf.get('pos def pl gen', None)
        if wf1 == wf2 and wf1 != None:
            lemgram_forms.remove((['pos', 'indef', 'pl', 'gen'], wf1))
            lemgram_forms.remove((['pos', 'def', 'pl', 'gen'], wf1))
            lemgram_forms.append((['pos', 'pl', 'gen'], wf1))
    if pos == 'vb':
        param_wf = dict([(" ".join(param), wf) for param, wf in lemgram_forms])
        wf1 = param_wf.get('pret_part indef pl nom', None)
        wf2 = param_wf.get('pret_part def pl nom', None)
        if wf1 == wf2 and wf1 != None:
            lemgram_forms.remove((['pret_part', 'indef', 'pl', 'nom'], wf1))
            lemgram_forms.remove((['pret_part', 'def', 'pl', 'nom'], wf1))
            lemgram_forms.append((['pret_part', 'pl', 'nom'], wf1))
        wf1 = param_wf.get('pret_part indef pl gen', None)
        wf2 = param_wf.get('pret_part def pl gen', None)
        if wf1 == wf2 and wf1 != None:
            lemgram_forms.remove((['pret_part', 'indef', 'pl', 'gen'], wf1))
            lemgram_forms.remove((['pret_part', 'def', 'pl', 'gen'], wf1))
            lemgram_forms.append((['pret_part', 'pl', 'gen'], wf1))

def saldo_to_suc(saldo_pos, saldo_param):
    suc_m = []
    if saldo_pos == 'nn':
        # Gender can be uter, neuter, or both
        if 'u' in saldo_param: suc_m.append(['UTR'])
        elif 'n' in saldo_param: suc_m.append(['NEU'])
        elif 'v' in saldo_param: suc_m.append(['UTR', 'NEU'])
        elif 'm' in saldo_param: suc_m.append(['UTR'])
        elif 'f' in saldo_param: suc_m.append(['UTR'])
        #elif 'm' in saldo_param: suc_m.append(['MAS'])
        #elif 'f' in saldo_param: suc_m.append(['FEM'])
        else: return None
        if 'sms' in saldo_param:
            suc_m += [['-'], ['-'], ['SMS']]
            return generate_tags('NN', suc_m)
        # Singular or plural
        if 'sg' in saldo_param: suc_m.append(['SIN'])
        elif 'pl' in saldo_param: suc_m.append(['PLU'])
        else: return None
        # Definite or indefinite
        if 'def' in saldo_param: suc_m.append(['DEF'])
        elif 'indef' in saldo_param: suc_m.append(['IND'])
        else: return None
        # Nominative or genitive
        if 'nom' in saldo_param: suc_m.append(['NOM'])
        elif 'gen' in saldo_param: suc_m.append(['GEN'])
        else: return None
        return generate_tags('NN', suc_m)
    elif saldo_pos == 'nna':
        return [('NN', ['AN'])]
    elif saldo_pos == 'pm' or saldo_pos == 'pma':
        if 'nom' in saldo_param: suc_m.append(['NOM'])
        elif 'gen' in saldo_param: suc_m.append(['GEN'])
        else: return None
        return generate_tags('PM', suc_m)
    elif saldo_pos == 'vb':
        if 'sms' in saldo_param:
            return [('VB', ['SMS'])]
        if 'pres_part' in saldo_param:
            suc_m += [['PRS'], ['UTR/NEU'], ['SIN/PLU'], ['IND/DEF'], ]
            if 'nom' in saldo_param: suc_m.append(['NOM'])
            elif 'gen' in saldo_param: suc_m.append(['GEN'])
            else: return None
            return generate_tags('PC', suc_m)
        elif 'pret_part' in saldo_param:
            suc_m.append(['PRF'])
            if 'masc' in saldo_param: suc_m.append(['MAS'])
            elif 'no_masc' in saldo_param: suc_m.append(['UTR/NEU'])
            elif 'u' in saldo_param: suc_m.append(['UTR'])
            elif 'n' in saldo_param: suc_m.append(['NEU'])
            else: suc_m.append(['UTR/NEU']) # indefinite plurals
            if 'sg' in saldo_param: suc_m.append(['SIN'])
            elif 'pl' in saldo_param: suc_m.append(['PLU'])
            else: return None
            if 'def' in saldo_param: suc_m.append(['DEF'])
            elif 'indef' in saldo_param: suc_m.append(['IND'])
            else: suc_m.append(['IND/DEF'])
            if 'nom' in saldo_param: suc_m.append(['NOM'])
            elif 'gen' in saldo_param: suc_m.append(['GEN'])
            return generate_tags('PC', suc_m)
        # NB: imper forms are never marked as s-form in SALDO, but can be in
        # SUC.
        if 'imper' in saldo_param: return([('VB', ['IMP', 'AKT'])])
        if 'inf' in saldo_param: suc_m.append(['INF'])
        elif 'pres' in saldo_param: suc_m.append(['PRS'])
        elif 'pret' in saldo_param: suc_m.append(['PRT'])
        elif 'sup' in saldo_param: suc_m.append(['SUP'])
        else: return None
        if 'aktiv' in saldo_param: suc_m.append(['AKT'])
        elif 's-form' in saldo_param: suc_m.append(['SFO'])
        else: return None
        return generate_tags('VB', suc_m)
    elif saldo_pos == 'vba':
        return [('VB', ['AN'])]
    elif saldo_pos == 'ab':
        if 'sms' in saldo_param:
            return [('AB', ['SMS'])]
        if 'super' in saldo_param: suc_m.append(['SUV'])
        elif 'komp' in saldo_param: suc_m.append(['KOM'])
        elif 'pos' in saldo_param: suc_m.append(['POS'])
        elif 'invar' in saldo_param: pass
        else: return None
        return generate_tags('AB', suc_m)
    elif saldo_pos == 'aba':
        return [('AB', ['AN'])]
    elif saldo_pos == 'av':
        if 'sms' in saldo_param:
            return None # TODO
        if 'invar' in saldo_param:
            return [('JJ', ['POS', 'UTR/NEU', 'SIN/PLU', 'IND/DEF', 'NOM'])]
        if 'komp' in saldo_param: suc_m.append(['KOM'])
        elif 'pos' in saldo_param: suc_m.append(['POS'])
        elif 'super' in saldo_param: suc_m.append(['SUV'])
        else: return None
        if 'masc' in saldo_param: suc_m.append(['MAS'])
        elif 'u' in saldo_param: suc_m.append(['UTR'])
        elif 'n' in saldo_param: suc_m.append(['NEU'])
        else: suc_m.append(['UTR/NEU'])
        # elif 'no_masc' in saldo_param: suc_m.append(['UTR/NEU'])
        # else: return None
        if 'sg' in saldo_param: suc_m.append(['SIN'])
        elif 'pl' in saldo_param: suc_m.append(['PLU'])
        else: suc_m.append(['SIN/PLU'])
        if 'def' in saldo_param: suc_m.append(['DEF'])
        elif 'indef' in saldo_param: suc_m.append(['IND'])
        else: suc_m.append(['IND/DEF'])
        if 'nom' in saldo_param: suc_m.append(['NOM'])
        elif 'gen' in saldo_param: suc_m.append(['GEN'])
        else: return None
        return generate_tags('JJ', suc_m)
    elif saldo_pos == 'ava':
        return [('JJ', ['AN'])]

RE_NN_PARADIGM = re.compile(ur'nn_\d([nuvfm])')

class Lexicon:
    def __init__(self):
        self.word_tag_count = {}
        self.deprels = set()
        self.netags = set()
        self.chunktags = set()

    def import_saldo(self, filename):
        morph_etree = ElementTree.parse(open(filename, 'rb'))
        wf_tag = []
        for el_entry in morph_etree.getiterator('LexicalEntry'):
            lemgram_forms = []
            lemgram_gf = None
            lemgram_pos = None
            lemgram_paradigm = None
            el_lemma = el_entry.find('Lemma')
            el_fr = el_lemma.find('FormRepresentation')
            for el_feat in el_fr.getiterator('feat'):
                att = el_feat.get('att')
                val = el_feat.get('val')
                if att == 'writtenForm':    lemgram_gf = val
                elif att == 'partOfSpeech': lemgram_pos = val
                elif att == 'paradigm':     lemgram_paradigm = val
            gender = None
            m = RE_NN_PARADIGM.match(lemgram_paradigm)
            if m != None: gender = m.group(1)
            for el_wordform in el_entry.getiterator('WordForm'):
                form_wf = None
                form_param = None
                for el_feat in el_wordform.getiterator('feat'):
                    att = el_feat.get('att')
                    val = el_feat.get('val')
                    if att == 'writtenForm':    form_wf = val
                    elif att == 'msd':          form_param = val
                    else: assert False
                param = form_param.split()
                if gender != None: param.append(gender)
                lemgram_forms.append((param, form_wf))
            preprocess_forms(lemgram_pos, lemgram_forms)
            for param, wf in lemgram_forms:
                suc_tags = saldo_to_suc(lemgram_pos, param)
                if suc_tags == None:
                    print >>sys.stderr, wf.encode('utf-8'), lemgram_paradigm.encode('utf-8'), lemgram_pos, param
                    continue
                for suc_pos, suc_m in suc_tags:
                    tag = u"|".join([suc_pos]+suc_m)
                    wf = wf.lower()
                    self.word_tag_count[
                        (wf, lemgram_gf, tag)] = 0

    def import_list(self, f, tag=u'UO'):
        for line in f:
            word = line.rstrip(u"\n").split(u"\t")[0]
            if u' ' in word: continue
            lower = word.lower()
            if tag == u'PM':
                self.word_tag_count[(lower, word, u'PM|NOM')] = 0
                if lower.endswith(u's'):
                    self.word_tag_count[(lower, word, u'PM|GEN')] = 0
                else:
                    self.word_tag_count[(lower+u's', word, u'PM|GEN')] = 0
            else:
                self.word_tag_count[(lower, lower, tag)] = 0

    def import_conll(self, f):
        while True:
            sent = read_sent(f)
            if sent == None: break
            for tok in sent:
                wf = tok[1]
                gf = tok[2] if tok[2] != '_' else None
                if tok[5] == '': tag = tok[4]
                else: tag = '|'.join([tok[4]] + tok[5].split('|'))
                wf = wf.lower()
                self.word_tag_count[(wf, gf, tag)] = \
                    self.word_tag_count.get((wf, gf, tag), 0) + 1
                if tok[7] != '_':
                    self.deprels.add(tok[7])
                if tok[8] != '_':
                    chunktag = tok[8] + ('-'+tok[9] if tok[9] != '_' else '')
                    self.chunktags.add(chunktag)
                if tok[10] != '_':
                    netag = tok[10] + ('-'+tok[11] if tok[11] != '_' else '')
                    self.netags.add(netag)
                
    def dump(self, outfile):
        data = sorted(self.word_tag_count.items())
        for (wf, gf, tag), n in data:
            print >>outfile, u"\t".join(
                [wf, gf if gf != None else u"", tag, str(n)])

if __name__ == '__main__':
    import sys
    import codecs
    l = Lexicon()
    for filename in sys.argv[1:]:
        if filename.endswith('.conll'):
            l.import_conll(codecs.open(filename, "rb", "utf-8"))
        elif filename.endswith('.xml'):
            l.import_saldo(filename)
        elif filename[:3] == 'UO:':
            l.import_list(codecs.open(filename[3:], "rb", "utf-8"), u'UO')
        elif filename[:3] == 'PM:':
            l.import_list(codecs.open(filename[3:], "rb", "utf-8"), u'PM')
        else:
            assert False
    l.dump(codecs.getwriter('utf-8')(sys.stdout))

