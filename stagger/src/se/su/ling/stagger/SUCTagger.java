package se.su.ling.stagger;
import java.util.*;

public class SUCTagger extends Tagger {
    static final long serialVersionUID = -3288459020368778546L;

    public SUCTagger(TaggedData taggedData, int posBeamSize, int neBeamSize)
    {
        super(taggedData, posBeamSize, neBeamSize);
    }

    private String capitalizeLemma(String textLower, int posTag) {
        try {
            if(posTag == taggedData.getPosTagSet().getTagID("PM|NOM")) {
                return textLower.substring(0,1).toUpperCase() +
                       textLower.substring(1);
            } else
            if(posTag == taggedData.getPosTagSet().getTagID("PM|GEN")) {
                int suffixLen = 0;
                if(textLower.endsWith(":s")) suffixLen = 2;
                else if(textLower.endsWith("s")) suffixLen = 1;
                return textLower.substring(0,1).toUpperCase() +
                       textLower.substring(1,textLower.length()-suffixLen);
            }
        } catch(TagNameException e) { }
        return textLower;
    }

    /**
     * Returns the lemma form of the given string / POS tag.
     */
    protected String getLemma(TaggedToken token) {
        int posTag = token.posTag;
        String textLower = token.textLower;
        try {
            if(posTag == taggedData.getPosTagSet().getTagID("LE")) {
                return token.token.value;
            }
        } catch(TagNameException e) { }
        Lexicon.Entry[] entries = posLexicon.getEntries(textLower);
        if(entries != null) {
            for(Lexicon.Entry entry : entries) {
                if(entry.tag == posTag && entry.lf != null) return entry.lf;
            }
        }
        int len = textLower.length();
        for(int i=(len<=16)?1:len-16; i<len; i++) {
            entries = posLexicon.getEntries(textLower.substring(i));
            if(entries == null) continue;
            for(Lexicon.Entry entry : entries) {
                if(entry.tag == posTag && entry.lf != null) {
                    return capitalizeLemma(
                        textLower.substring(0,i) + entry.lf.toLowerCase(),
                        posTag);
                }
            }
        }
        return capitalizeLemma(textLower, posTag);
    }

    /**
     * Constructs POS tag lexicon, and generalized token lexicon.
     */
    public void buildLexicons(TaggedToken[][] sents) {
        super.buildLexicons(sents);
        try {
            tokTypeTags[Token.TOK_SMILEY] = new int[1];
            tokTypeTags[Token.TOK_SMILEY][0] =  
                taggedData.getPosTagSet().getTagID("LE", true);
            posLexicon.interpolate(
                taggedData.getPosTagSet().getTagID("NN|NEU|PLU|IND|NOM"),
                taggedData.getPosTagSet().getTagID("NN|NEU|SIN|IND|NOM"));
        } catch(TagNameException e) {
            assert false;
        }
    }

    protected void computeOpenTags() {
        String[] names = taggedData.getPosTagSet().getTagNames();
        int[] tags = new int[names.length];
        String[] openTagArray = {
            "NN", "VB", "JJ", "AB", "PC", "RG", "RO", "PM", "UO", "LE" };
        HashSet<String> openTagSet = new HashSet<String>(Arrays.asList(
            openTagArray));
        int nTags = 0;
        for(int i=0; i<names.length; i++) {
            if(openTagSet.contains(names[i].split("\\|")[0])) {
                tags[nTags++] = i;
            }
        }
        assert nTags > 0;
        openTags = Arrays.copyOf(tags, nTags);
    }

    /* This turned out not to work very well.
    protected void guessTags(String wordForm, boolean firstWord) {
        String textLower = wordForm.toLowerCase();
        // If the word is in the lexicon, do nothing.
        if(posLexicon.getEntries(textLower) == null) return;
        // Otherwise, search for suffixes that are in the lexicon.
        // TODO: special-case dashes?
        for(int i=2; i<textLower.length()-5; i++) {
            String suffix = textLower.substring(i);
            Lexicon.Entry[] entries = posLexicon.getEntries(suffix);
            if(entries != null) {
                // Found a suffix, add the tags of the suffix to the whole
                // word's lexicon entry (but tags that belong to open word
                // classes)
                for(Lexicon.Entry entry : entries) {
                    int idx = Arrays.binarySearch(openTags, entry.tag);
                    if(idx >= 0 && idx < openTags.length &&
                       openTags[idx] == entry.tag)
                        posLexicon.addEntry(textLower, entry.lf, entry.tag, 0);
                }
                return;
            }
        }
    }
    */
}

