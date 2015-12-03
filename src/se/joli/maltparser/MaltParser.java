package se.joli.maltparser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import org.maltparser.concurrent.graph.ConcurrentDependencyGraph;
import se.su.ling.stagger.CTBTagger;
import se.su.ling.stagger.EnglishTokenizer;
import se.su.ling.stagger.GenericTagger;
import se.su.ling.stagger.LatinTokenizer;
import se.su.ling.stagger.PTBTagger;
import se.su.ling.stagger.SUCTagger;
import se.su.ling.stagger.SwedishTokenizer;
import se.su.ling.stagger.TagNameException;
import se.su.ling.stagger.TaggedData;
import se.su.ling.stagger.TaggedToken;
import se.su.ling.stagger.Tagger;
import se.su.ling.stagger.Token;
import se.su.ling.stagger.Tokenizer;


public class MaltParser {
    public static boolean plainOutput = false;
    public static boolean extendLexicon = true;
    public static boolean hasNE = true;
    
    protected Tagger tagger = null;
    protected Tokenizer tokenizer = null;
    List<String> positiveAdjAdv = null;
    List<String> negativeAdjAdv = null;
    
    List<String> positiveVerb = null;
    List<String> negativeVerb = null;
    
    /**
     * Creates and returns a tokenizer for the given language.
     */
    private static Tokenizer getTokenizer(Reader reader, String lang) {
        Tokenizer tokenizer;
        switch (lang) {
            case "sv":
                tokenizer = new SwedishTokenizer(reader);
                break;
            case "en":
                tokenizer = new EnglishTokenizer(reader);
                break;
            case "any":
                tokenizer = new LatinTokenizer(reader);
                break;
            default:
                throw new IllegalArgumentException();
        }
        return tokenizer;
    }
    /**
     * Creates and returns a tagger for the given language.
     */
    private static Tagger getTagger(String lang, TaggedData td, int posBeamSize, int neBeamSize) {
        Tagger tagger = null;
        switch (lang) {
            case "sv":
                tagger = new SUCTagger(
                        td, posBeamSize, neBeamSize);
                break;
            case "en":
                tagger = new PTBTagger(
                        td, posBeamSize, neBeamSize);
                break;
            case "any":
                tagger = new GenericTagger(
                        td, posBeamSize, neBeamSize);
                break;
            case "zh":
                tagger = new CTBTagger(
                        td, posBeamSize, neBeamSize);
                break;
            default:
                System.err.println("Invalid language: "+lang);
                break;
        }
        return tagger;
    }
    
    /**
     * Open a buffered file reader for a file with UTF-8 encoding
     * @param name - path/name of the file
     * @return A BufferedReader for the given file path
     * @throws IOException
     */
    private static BufferedReader openUTF8File(String name) throws IOException {
        if(name.equals("-"))
            return new BufferedReader(
                new InputStreamReader(System.in, "UTF-8"));
        else if(name.endsWith(".gz"))
            return new BufferedReader(new InputStreamReader(
                        new GZIPInputStream(
                            new FileInputStream(name)), "UTF-8"));
        return new BufferedReader(new InputStreamReader(
                    new FileInputStream(name), "UTF-8"));
    }
    
    /**
     * Returns a textual based tableview of the tagged words and their relation
     * in a sentence
     * @param sentence - the sentence to analys and tag
     * @param sentIdx - the index that we will start at in the sentence
     * @return  - table format textual representation of the tagged sentence
     * @throws IOException 
     */
    protected String sentenceToPOSTree(ArrayList<Token> sentence, int sentIdx) throws IOException {
        if(tagger == null || tokenizer == null) return null;
        BufferedWriter writer;
        StringWriter sb = new StringWriter();
        writer = new BufferedWriter(sb);//
                //new OutputStreamWriter(System.out, "UTF-8"));
        TaggedToken[] sent =
                new TaggedToken[sentence.size()];
        String fileID = "Hej";
        if(tokenizer.sentID != null) {
            if(!fileID.equals(tokenizer.sentID)) {
                fileID = tokenizer.sentID;
                sentIdx = 0;
            }
        }
        for(int j=0; j<sentence.size(); j++) {
            Token tok = sentence.get(j);
            String id;
            id = fileID + ":" + sentIdx + ":" +
                    tok.offset;
            sent[j] = new TaggedToken(tok, id);
        }
        TaggedToken[] taggedSent =
                tagger.tagSentence(sent, true, false);
        try {
            tagger.getTaggedData().writeConllSentence(
                    writer,
                    taggedSent,
                    plainOutput);
            
        } catch (TagNameException ex) {
            Logger.getLogger(MaltParser.class.getName()).log(Level.SEVERE, null, ex);
        }
        writer.close();
        return sb.toString();
    }
    /**
     * Tagg an entire sentence with Part of speech tags
     * @param sentence - the sentence to tag
     * @param sentIdx - The index to start from in word indicies
     * @return A new tagged sentence
     * @throws IOException 
     */
    public List<TaggedToken> tagSentenceWithPOS(List<Token> sentence, int sentIdx) throws IOException {
        if(tagger == null || tokenizer == null) return null;
        
        TaggedToken[] sent =
                new TaggedToken[sentence.size()];
        String fileID = "TaggedWord";
        if(tokenizer.sentID != null) {
            if(!fileID.equals(tokenizer.sentID)) {
                fileID = tokenizer.sentID;
                sentIdx = 0;
            }
        }
        for(int j=0; j<sentence.size(); j++) {
            Token tok = sentence.get(j);
            String id;
            id = fileID + ":" + sentIdx + ":" +
                    tok.offset;
            sent[j] = new TaggedToken(tok, id);
        }
        return Arrays.asList(tagger.tagSentence(sent, true, false));
    }
    /**
     * Parses multiple sentences in multiple files and converts the sentence to language specific rules
     * (swedish rules) @see SwedishRules.java
     * @param inputFiles - the files to read sentences from
     * @return an array of modified sentences based on language specific rules
     * @throws IOException 
     */
    public String[] parseSentences(String[] inputFiles) throws IOException {
        String[] result = new String[inputFiles.length];
        SwedishRules sr = new SwedishRules(negativeAdjAdv, positiveAdjAdv);
        sr.setTagger(tagger);
        //ArrayList<String> inputFiles = new ArrayList<String>();

        if(inputFiles.length < 1) {
            System.err.println("No files to tag.");
            System.exit(1);
        }

        String lang = "sv";//tagger.getTaggedData().getLanguage();

        // TODO: experimental feature, might remove later
        tagger.setExtendLexicon(extendLexicon);
        if(!hasNE) tagger.setHasNE(false);

        int indexResult = 0;
        for(String inputFile : inputFiles) {
            String fileID =
                    (new File(inputFile)).getName().split(
                            "\\.")[0];
            BufferedReader reader = openUTF8File(inputFile);

            tokenizer = getTokenizer(reader, lang);
            ArrayList<Token> sentence;
            int sentIdx = 0;
            String sentenceTreeText = "";
            while((sentence=tokenizer.readSentence())!=null) {
                List<TaggedToken> taggedSentence = tagSentenceWithPOS(sentence, sentIdx);
                taggedSentence = sr.applyNegationRule(taggedSentence);
                for(TaggedToken tt : taggedSentence)
                    sentenceTreeText += tt.lf;
                sentIdx++;
            }
            tokenizer.yyclose();
            result[indexResult++] = sentenceTreeText;
        }
        return result;
    }
    
    /**
     * Loads a tagger from file this will mostly take long time to process so
     * do it once to make it performance friendly!
     * @param modelFile - the file that we want to load as a tagger model
     */
    public void loadStaggerModel(String modelFile) {
        try {
            try (ObjectInputStream modelReader = new ObjectInputStream(
                    new FileInputStream(modelFile))) {
                System.out.print( "Loading Stagger model ...");
                System.out.println();
                tagger = (Tagger)modelReader.readObject();
            }
        }
        catch(ClassNotFoundException | IOException ex) {
            tagger = null;
        }
    }
    /**
     * Loads a dictionary of words into memory from files
     * @param posWordsPath - Positive adverbs/adjective path
     * @param negWordsPath - Negative adverbs/adjective path
     * @param posVerbs - Positive verbs path
     * @param negVerbs - Negative verbs path
     * @throws IOException 
     */
    public void loadWords(String posWordsPath, String negWordsPath, String posVerbs, String negVerbs) throws IOException {
        positiveAdjAdv = Files.readAllLines(Paths.get(posWordsPath), Charset.forName("utf-8"));
        negativeAdjAdv = Files.readAllLines(Paths.get(negWordsPath), Charset.forName("utf-8"));
        
        positiveVerb = Files.readAllLines(Paths.get(posVerbs), Charset.forName("utf-8"));
        negativeVerb = Files.readAllLines(Paths.get(negVerbs), Charset.forName("utf-8"));
    }

    public static void main(String[] args) throws IOException {
        MaltParser mp = new MaltParser();
        String base = "/media/soldier/home/jolin1337/Documents2/Distributed_Systems/JavaLaboration/MaltParser/";
        mp.loadWords(base + "posAdjAdv.txt", base + "negAdjAdv.txt", base + "posVerbs.txt",  base + "negVerbs.txt");
        mp.loadStaggerModel(base + "stagger/swedish.bin");
        
        Scanner sc = new Scanner(System.in);
        String line = "";
        while(!line.equals("exit"))  {
            System.out.print("> ");
            line = sc.nextLine();
            Files.write(Paths.get(base + "stagger/tst2.txt"), line.getBytes());
            
            // Parse sentences from the file stagger/tst2.txt
            mp.parseSentences(new String[] {base + "stagger/tst2.txt"});
        }
        
        
        ConcurrentDependencyGraph outputGraph = null;
        // Loading the Swedish model swemalt-mini
//        ConcurrentMaltParserModel model = null;
//        try {
//            URL swemaltMiniModelURL = new File(base + "maltparser-1.8.1/example1.mco").toURI().toURL();
//            model = ConcurrentMaltParserService.initializeParserModel(swemaltMiniModelURL);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        for(String tokensStr : res) {
//            System.out.println("Token from stagger:");
//            System.out.println(tokensStr);
//            System.out.println("\nToken generated with maltparser");
//
//            // Creates an array of tokens, which contains the Swedish sentence 'Samtidigt får du högsta sparränta plus en skattefri sparpremie.'
//            // in the CoNLL data format.
//            String[] tokens = tokensStr.split("\n");
//            try {
//                outputGraph = model.parse(tokens);
//            } catch (Exception e) {
//               e.printStackTrace();
//            }
//            System.out.println(outputGraph);
//        }
    }
}
