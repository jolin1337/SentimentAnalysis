/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package se.joli.maltparser;

import java.util.ArrayList;
import java.util.List;
import se.su.ling.stagger.TaggedToken;
import se.su.ling.stagger.Tagger;
import se.su.ling.stagger.Token;

/**
 * This class codes swedish rules like negations
 * @author jolin1337
 */
public class SwedishRules {
    // The wrods to treat in the negation rule 
    private final String[] negationWords = new String[] {"inte"};
    // The negative words to replace the positive ones with
    private final List<String> negativeWords;
    // The positive words to replace the negative ones with
    private final List<String> positiveWords;
    
    // The amount of words to look for an typeOfWords behinde a negationWord
    private final int wordsAfterNegation = 3;
    
    // This defines adjective and adverb words in the POS tagger
    public static String typeOfWords = "^.*?(JJ|AB).*$";
    
    // The POS tagger to determine if the word is Adjective, Adverb etc.
    private Tagger tagger = null;
    
    
    private enum FoundWordTagEval {
        REPLACE_POSITIVE_WORD,
        REPLACE_NEGATIVE_WORD,
        DO_NOTHING,
        REMOVE_WORDS
    }
    
    /**
     * Constructor that defines the positive/negative word lists
     * @param negativeWords - the negative word list (should be every word that is of category in typeOfWords)
     * @param positiveWords  - the positive word list (should be every word that is of category in typeOfWords)
     */
    public SwedishRules(List<String> negativeWords, List<String> positiveWords) {
        this.negativeWords = negativeWords;
        this.positiveWords = positiveWords;
    }
    
    /**
     * Sets the pos tagger
     * @param t - the pos tagger
     */
    public void setTagger(Tagger t) {
        tagger = t;
    }
    
    public List<TaggedToken> applyNegationRule(List<TaggedToken> sentence) {
        return applyNegationRule(sentence, typeOfWords);//"JJ|AB");
    }
    /**
     * Apply the negation rule to the sentence with the words POS types defined in wordsToNegateRegex
     * @param sentence - The sentence to apply rule
     * @param wordsToNegateRegex - The words to negate in a negation expression
     * @return The new sentence that has applied the rules of negation 
     */
    public List<TaggedToken> applyNegationRule(List<TaggedToken> sentence, String wordsToNegateRegex) {
        // If tagger is not defined, you have to call setTagger(tagger) for this to work
        if(tagger == null) throw new NullPointerException("Tagger is not initialized yet! Use setTagger(Tagger tagger) to init it");
        // The new sentence to return as result
        List<TaggedToken> newSentence = new ArrayList<>();
        
        // Iterate through all words in the sentence
        for(int i = 0; i < sentence.size(); i++) {
            TaggedToken tt = sentence.get(i); // Get the i:th word in the sentence
            
            // If the word is a negation word like "not" ("inte")
            if(Helper.inArray(negationWords, tt.lf)) {
                
                // This list we will store the POS tags in
                String[] posTags = new String[3];
                
                FoundWordTagEval typeOfThreeWords = FoundWordTagEval.DO_NOTHING; 
                int k = 0;
                // Loop wordsAfterNegation words forward or as long as the sentence reaches
                for(; k < wordsAfterNegation && i+1 < sentence.size(); i++,k++) {
                    try {
                        // Get the word i+1 after the negation word
                        TaggedToken nextWord = sentence.get(i+1);
                        
                        // If the word is a negation word again e.g. "not" ("inte")
                        if(Helper.inArray(negationWords, nextWord.lf)) {
                            // If this was our first iteration
                            if(k==0) {
                                // We will remove the two negation words since it is the same as not having them there
                                typeOfThreeWords = FoundWordTagEval.REMOVE_WORDS;
                                i++;
                            }
                            // We have now reached another negation word and we 
                            // will start to search for the next words after that one 
                            // in the next iteration of words
                            break;
                        }
                        // Get tha POS Tag of the word
                        posTags[k] = tagger.getTaggedData().getPosTagSet().getTagName(nextWord.posTag);
                        // If the word is a word we look for e.g. adjective or adverb
                        if(posTags[k].matches(wordsToNegateRegex)) {
                            // If we have encontred any prevous positive words and is the current word a positive word
                            if(typeOfThreeWords != FoundWordTagEval.REPLACE_POSITIVE_WORD && Helper.inArray(positiveWords, nextWord.lf)) {
                                // if the current state of action is do nothing e.g we havent changed it
                                if(typeOfThreeWords == FoundWordTagEval.DO_NOTHING)
                                     // Make the action to replace the positive word with a negative one
                                    typeOfThreeWords = FoundWordTagEval.REPLACE_POSITIVE_WORD;
                                else 
                                     // set the state to remove all the loaded words
                                    typeOfThreeWords = FoundWordTagEval.REMOVE_WORDS;
                            }
                            else if(typeOfThreeWords != FoundWordTagEval.REPLACE_NEGATIVE_WORD && Helper.inArray(negativeWords, nextWord.lf)) {
                                if(typeOfThreeWords == FoundWordTagEval.DO_NOTHING)
                                    typeOfThreeWords = FoundWordTagEval.REPLACE_NEGATIVE_WORD;
                                else typeOfThreeWords = FoundWordTagEval.REMOVE_WORDS;
                            }
                        }
                        
                    } catch(Exception ex) {}
                }
                
                if(typeOfThreeWords == FoundWordTagEval.REPLACE_NEGATIVE_WORD) {
                    for(int j=i-k;j<i;j++) {
                        if(posTags[k-(i-j)].matches(wordsToNegateRegex))
                            newSentence.add(replaceTaggedToken(sentence.get(j+1), positiveWords.get(0)));
                        else newSentence.add(sentence.get(j+1));
                    }
                }
                else if(typeOfThreeWords == FoundWordTagEval.REPLACE_POSITIVE_WORD) {
                    for(int j=i-k;j<i;j++) {
                        if(posTags[k-(i-j)].matches(wordsToNegateRegex))
                            newSentence.add(replaceTaggedToken(sentence.get(j+1), negativeWords.get(0)));
                        else newSentence.add(sentence.get(j+1));
                    }
                }
                else if(typeOfThreeWords == FoundWordTagEval.DO_NOTHING) {
                    for(int j=i-k-1;j<i;j++)
                        newSentence.add(sentence.get(j+1));
                }
                else if(typeOfThreeWords == FoundWordTagEval.REMOVE_WORDS) {
                    for(int j=i-k;j<i;j++)
                        if(!posTags[k-(i-j)].matches(wordsToNegateRegex)) 
                            newSentence.add(sentence.get(j+1));
                }
            }
            else newSentence.add(tt);
        }
        try {
            for(TaggedToken tt : newSentence) 
                System.out.print(tt.textLower + " ");// + " (" + tagger.getTaggedData().getPosTagSet().getTagName(tt.posTag) + ") ");
        }catch(Exception ex) {

        }
        System.out.println();
        return newSentence;
    }
    /**
     * Replace the tagged tokens word with another word
     * @param tt
     * @param toReplaceWith
     * @return 
     */
    private TaggedToken replaceTaggedToken(TaggedToken tt, String toReplaceWith) {
        TaggedToken newTaggedToken = new TaggedToken(tt);
        newTaggedToken.textLower = toReplaceWith;
        newTaggedToken.lf = toReplaceWith;
        newTaggedToken.token = new Token(tt.token.type, tt.lf, tt.token.offset);
        return newTaggedToken;
    }
}
