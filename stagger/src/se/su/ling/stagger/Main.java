package se.su.ling.stagger;

import java.io.*;
import java.util.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;

public class Main {
    /**
     * Creates and returns a tokenizer for the given language.
     */
    private static Tokenizer getTokenizer(Reader reader, String lang) {
        Tokenizer tokenizer;
        if(lang.equals("sv")) {
            tokenizer = new SwedishTokenizer(reader);
        } else if(lang.equals("en")) {
            tokenizer = new EnglishTokenizer(reader);
        } else if(lang.equals("any")) {
            tokenizer = new LatinTokenizer(reader);
        } else {
            throw new IllegalArgumentException();
        }
        return tokenizer;
    }

    /**
     * Creates and returns a tagger for the given language.
     */
    private static Tagger getTagger(
    String lang, TaggedData td, int posBeamSize, int neBeamSize)
    {
        Tagger tagger = null;
        if(lang.equals("sv")) {
            tagger = new SUCTagger(
                td, posBeamSize, neBeamSize);
        } else if(lang.equals("en")) {
            tagger = new PTBTagger(
                td, posBeamSize, neBeamSize);
        } else if(lang.equals("any")) {
            tagger = new GenericTagger(
                td, posBeamSize, neBeamSize);
        } else if(lang.equals("zh")) {
            tagger = new CTBTagger(
                td, posBeamSize, neBeamSize);
        } else {
            System.err.println("Invalid language: "+lang);
            System.exit(1);
        }
        return tagger;
    }

    private static TaggedToken[][][] getSUCFold(
        TaggedToken[][] sents, int fold)
    {
        TaggedToken[][][] parts = new TaggedToken[3][][];
        HashSet<String> fileSet = new HashSet<String>();
        for(TaggedToken[] sent : sents) {
            String fileID = sent[0].id.substring(0,4);
            fileSet.add(fileID);
        }
        ArrayList<String> files = new ArrayList<String>(fileSet);
        Collections.sort(files);
        assert files.size() == 500;
        HashMap<String,Integer> fileNr = new HashMap<String,Integer>();
        for(int i=0; i<files.size(); i++) {
            String fileID = files.get(i);
            fileNr.put(fileID, new Integer(i));
        }
        int nDev = 0, nTest = 0, nTrain = 0;
        for(TaggedToken[] sent : sents) {
            String fileID = sent[0].id.substring(0,4);
            int nr = (int)fileNr.get(fileID);
            if((nr % 10) == fold) nTest++;
            else if((((nr+1) % 10) == fold) && (((nr / 10)) % 5 == 0)) nDev++;
            else nTrain++;
        }
        parts[0] = new TaggedToken[nTrain][];
        parts[1] = new TaggedToken[nDev][];
        parts[2] = new TaggedToken[nTest][];
        int iDev = 0, iTest = 0, iTrain = 0;
        for(TaggedToken[] sent : sents) {
            String fileID = sent[0].id.substring(0,4);
            int nr = (int)fileNr.get(fileID);
            if((nr % 10) == fold) parts[2][iTest++] = sent;
            else if((((nr+1) % 10) == fold) && (((nr / 10)) % 5 == 0))
                parts[1][iDev++] = sent;
            else parts[0][iTrain++] = sent;
        }
        return parts;
    }

    /**
     * Splits the sentences into training/development/test data sets.
     *
     * @param sents         array of sentences
     * @param nFolds        number of folds in the experiment
     * @param devPercent    size of development set in 1/10 percent
     * @param testPercent   size of test set in 1/10 percent
     * @param i             fold number (between 0 and nFolds-1, inclusive)
     * @return              array with 3 TaggedToken[][] objects, containing
     *                      the training, development and test sets
     */
    private static TaggedToken[][][] getFold(
    TaggedToken[][] sents, int nFolds, int devPercent, int testPercent, int i)
    {
        int j,k;
        TaggedToken[][][] parts = new TaggedToken[3][][];
        ArrayList<Integer> order = new ArrayList<Integer>(sents.length);
        for(j=0; j<sents.length; j++) order.add(new Integer(j));
        Collections.shuffle(order, new Random(1));
        int nDev = (devPercent*sents.length) / 1000;
        int nTest = (testPercent*sents.length) / 1000;
        int nTrain = sents.length - (nDev+nTest);
        parts[0] = new TaggedToken[nTrain][];
        parts[1] = new TaggedToken[nDev][];
        parts[2] = new TaggedToken[nTest][];
        int a = (sents.length*i)/nFolds;
        for(j=0,k=0; j<a; j++) parts[0][k++] = sents[j];
        for(j=a+nDev+nTest; j<sents.length; j++) parts[0][k++] = sents[j];
        for(j=0; j<nDev; j++) parts[1][j] = sents[a+j];
        for(j=0; j<nTest; j++) parts[2][j] = sents[a+nDev+j];
        return parts;
    }

    private static BufferedReader openUTF8File(String name)
    throws IOException {
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

    public static void main(String[] args) throws Exception {
        String lexiconFile = null;
        String trainFile = null;
        String devFile = null;
        String modelFile = null;
        ArrayList<Dictionary> posDictionaries = new ArrayList<Dictionary>();
        ArrayList<Embedding> posEmbeddings = new ArrayList<Embedding>();
        ArrayList<Dictionary> neDictionaries = new ArrayList<Dictionary>();
        ArrayList<Embedding> neEmbeddings = new ArrayList<Embedding>();
        int posBeamSize = 8;
        int neBeamSize = 4;
        String lang = null;
        boolean preserve = false;
        float embeddingSigma = 0.1f;
        boolean plainOutput = false;
        String fold = null;
        int maxPosIters = 16;
        int maxNEIters = 16;
        boolean extendLexicon = true;
        boolean hasNE = true;

        for(int i=0; i<args.length; i++) {
            if(args[i].equals("-lexicon")) {
                lexiconFile = args[++i];
            } else if(args[i].equals("-dict")) {
                String dest = args[++i];
                Dictionary dict = new Dictionary();
                try {
                    dict.fromFile(args[++i]);
                } catch(IOException e) {
                    System.err.println("Can not load dictionary file.");
                    e.printStackTrace();
                    System.exit(1);
                }
                if(dest.equals("pos")) {
                    posDictionaries.add(dict);
                } else if (dest.equals("ne")) {
                    neDictionaries.add(dict);
                } else if (dest.equals("all")) {
                    posDictionaries.add(dict);
                    neDictionaries.add(dict);
                } else {
                    System.err.println("Expected pos/ne/all.");
                    System.exit(1);
                }
            } else if(args[i].equals("-lang")) {
                lang = args[++i];
            } else if(args[i].equals("-extendlexicon")) {
                extendLexicon = true;
            } else if(args[i].equals("-noextendlexicon")) {
                extendLexicon = false;
            } else if(args[i].equals("-noner")) {
                hasNE = false;
            } else if(args[i].equals("-positers")) {
                maxPosIters = Integer.parseInt(args[++i]);
            } else if(args[i].equals("-neiters")) {
                maxNEIters = Integer.parseInt(args[++i]);
            } else if(args[i].equals("-posbeamsize")) {
                posBeamSize = Integer.parseInt(args[++i]);
            } else if(args[i].equals("-nebeamsize")) {
                neBeamSize = Integer.parseInt(args[++i]);
            } else if(args[i].equals("-preserve")) {
                preserve = true;
            } else if(args[i].equals("-plain")) {
                plainOutput = true;
            } else if(args[i].equals("-fold")) {
                fold = args[++i];;
            } else if(args[i].equals("-embed")) {
                String dest = args[++i];
                Embedding embedding = new Embedding();
                try {
                    embedding.fromFile(args[++i]);
                    // This gives a very slight decrease in accuracy
                    // embedding.rescale(embeddingSigma);
                } catch(IOException e) {
                    System.err.println("Can not load embedding file.");
                    e.printStackTrace();
                    System.exit(1);
                }
                if(dest.equals("pos")) {
                    posEmbeddings.add(embedding);
                } else if (dest.equals("ne")) {
                    neEmbeddings.add(embedding);
                } else if (dest.equals("all")) {
                    posEmbeddings.add(embedding);
                    neEmbeddings.add(embedding);
                } else {
                    System.err.println("Expected pos/ne/all.");
                    System.exit(1);
                }
            } else if(args[i].equals("-trainfile")) {
                trainFile = args[++i];
            } else if(args[i].equals("-devfile")) {
                devFile = args[++i];
            } else if(args[i].equals("-modelfile")) {
                modelFile = args[++i];
            } else if(args[i].equals("-train")) {
                TaggedToken[][] trainSents = null;
                TaggedToken[][] devSents = null;
                if(trainFile == null ||
                   modelFile == null || lang == null)
                {
                    System.err.println("Insufficient data.");
                    System.exit(1);
                }
                TaggedData td = new TaggedData(lang);
                trainSents = td.readConll(
                    trainFile, null, true, !trainFile.endsWith(".conll"));
                if(devFile != null)
                    devSents = td.readConll(
                        devFile, null, true, !devFile.endsWith(".conll"));
                System.err.println(
                    "Read " + trainSents.length +
                    " training sentences and " +
                    ((devSents == null)? 0 : devSents.length) +
                    " development sentences.");
                Tagger tagger = getTagger(
                    lang, td, posBeamSize, neBeamSize);
                tagger.buildLexicons(trainSents);
                Lexicon lexicon = tagger.getPosLexicon();
                System.err.println("POS lexicon size (corpus): " +
                                   lexicon.size());
                if(lexiconFile != null)
                {
                    if(extendLexicon) {
                        System.err.println(
                            "Reading lexicon: " + lexiconFile);
                    } else {
                        System.err.println(
                            "Reading lexicon (not extending profiles): " +
                            lexiconFile);
                    }
                    lexicon.fromFile(lexiconFile, td.getPosTagSet(),
                                     extendLexicon);
                    System.err.println("POS lexicon size (external): " +
                                       lexicon.size());
                }
                tagger.setPosDictionaries(posDictionaries);
                tagger.setPosEmbeddings(posEmbeddings);
                tagger.setNEDictionaries(neDictionaries);
                tagger.setNEEmbeddings(neEmbeddings);
                tagger.setMaxPosIters(maxPosIters);
                tagger.setMaxNEIters(maxNEIters);
                tagger.train(trainSents, devSents);
                ObjectOutputStream writer = new ObjectOutputStream(
                    new FileOutputStream(modelFile));
                writer.writeObject(tagger);
                writer.close();
        } else if(args[i].equals("-cross")) {
                TaggedData td = new TaggedData(lang);
                TaggedToken[][] allSents =
                    td.readConll(
                        trainFile, null, true,
                        !trainFile.endsWith(".conll"));
                Tagger tagger = getTagger(
                    lang, td, posBeamSize, neBeamSize);
                tagger.setPosDictionaries(posDictionaries);
                tagger.setPosEmbeddings(posEmbeddings);
                tagger.setNEDictionaries(neDictionaries);
                tagger.setNEEmbeddings(neEmbeddings);
                final int nFolds = 10;
                /*
                int devPercent = 25;
                int testPercent = 25;
                */
                Evaluation eval = new Evaluation();
                for(int j=0; j<nFolds; j++) {
                    Evaluation localEval = new Evaluation();
                    TaggedToken[][][] parts = getSUCFold(allSents, j);
                    /*
                    TaggedToken[][][] parts = getFold(
                        allSents, nFolds, devPercent, testPercent, j);
                    */
                    System.err.println(
                        "Fold " + j + ", train (" + parts[0].length +
                        "), dev (" + parts[1].length + "), test (" +
                        parts[2].length + ")");
                    Lexicon lexicon = tagger.getPosLexicon();
                    lexicon.clear();
                    tagger.buildLexicons(parts[0]);
                    if(lexiconFile != null)
                        lexicon.fromFile(
                            lexiconFile, td.getPosTagSet(), extendLexicon);
                    tagger.train(parts[0], parts[1]);
                    for(TaggedToken[] sent : parts[2]) {
                        TaggedToken[] taggedSent = tagger.tagSentence(
                            sent, true, false);
                        eval.evaluate(taggedSent, sent);
                        localEval.evaluate(taggedSent, sent);
                        //tagger.getTaggedData().writeConllSentence(
                        //    System.out, taggedSent);
                        tagger.getTaggedData().writeConllGold(
                            System.out, taggedSent, sent, plainOutput);
                    }
                    System.err.println("Local POS accuracy: "+
                        localEval.posAccuracy()+" ("+
                        localEval.posCorrect+" / "+
                        localEval.posTotal+")");
                }
                System.err.println("POS accuracy: "+eval.posAccuracy()+
                                   " ("+eval.posCorrect+" / "+
                                   eval.posTotal+")");
                System.err.println("NE precision: "+eval.nePrecision());
                System.err.println("NE recall:    "+eval.neRecall());
                System.err.println("NE F-score:   "+eval.neFscore());
                System.err.println("NE total:     "+eval.neTotal);
                System.err.println("NE correct:   "+eval.neCorrect);
                System.err.println("NE found:     "+eval.neFound);
            } else if(args[i].equals("-server")) {
                if(modelFile == null || i >= args.length-1) {
                    System.err.println("Insufficient data.");
                    System.exit(1);
                }
                InetAddress serverIP = InetAddress.getByName(args[++i]);
                int serverPort = Integer.parseInt(args[++i]);

                ObjectInputStream modelReader = new ObjectInputStream(
                    new FileInputStream(modelFile));
                System.err.println( "Loading Stagger model ...");
                Tagger tagger = (Tagger)modelReader.readObject();
                lang = tagger.getTaggedData().getLanguage();
                modelReader.close();

                ServerSocket ss = new ServerSocket(serverPort, 4, serverIP);
                while(true) {
                    Socket sock = null;
                    try {
                        sock = ss.accept();
                        System.err.println("Connected to "+
                            sock.getRemoteSocketAddress().toString());
                        InputStream ins = sock.getInputStream();
                        byte[] lenBuf = new byte[4];
                        if(ins.read(lenBuf) != 4) {
                            throw new IOException("Can not read length.");
                        }
                        int len = ByteBuffer.wrap(lenBuf).getInt();
                        if(len < 1 || len > 100000) {
                            throw new IOException("Invalid data size: "+len);
                        }
                        byte[] dataBuf = new byte[len];
                        if(ins.read(dataBuf) != len) {
                            throw new IOException("Can not read data.");
                        }
                        Reader reader = new StringReader(
                            new String(dataBuf, "UTF-8"));

                        BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(
                                sock.getOutputStream(), "UTF-8"));

                        Tokenizer tokenizer = getTokenizer(reader, lang);
                        ArrayList<Token> sentence;
                        int sentIdx = 0;
                        String fileID = "net";
                        while((sentence=tokenizer.readSentence())!=null) {
                            TaggedToken[] sent =
                                new TaggedToken[sentence.size()];
                            if(tokenizer.sentID != null) {
                                if(!fileID.equals(tokenizer.sentID)) {
                                    fileID = tokenizer.sentID;
                                    sentIdx = 0;
                                }
                            }
                            for(int j=0; j<sentence.size(); j++) {
                                Token tok = sentence.get(j);
                                String id;
                                id = fileID + ":" + sentIdx + ":" + tok.offset;
                                sent[j] = new TaggedToken(tok, id);
                            }
                            TaggedToken[] taggedSent =
                                tagger.tagSentence(sent, true, false);
                            tagger.getTaggedData().writeConllSentence(
                                (writer == null)? System.out : writer,
                                taggedSent, plainOutput);
                            sentIdx++;
                        }
                        tokenizer.yyclose();
                        if(!sock.isClosed()) {
                            System.err.println("Closing connection to "+
                                sock.getRemoteSocketAddress().toString());
                            writer.close();
                        }
                    } catch(IOException e) {
                        e.printStackTrace();
                        if(sock != null) {
                            System.err.println("Connection failed to "+
                                sock.getRemoteSocketAddress().toString());
                            if(!sock.isClosed()) sock.close();
                        }
                    }
                }
             } else if(args[i].equals("-tag")) {
                if(modelFile == null || i >= args.length-1) {
                    System.err.println("Insufficient data.");
                    System.exit(1);
                }
                ArrayList<String> inputFiles = new ArrayList<String>();
                for(i++; i<args.length && !args[i].startsWith("-"); i++)
                    inputFiles.add(args[i]);
                if(inputFiles.size() < 1) {
                    System.err.println("No files to tag.");
                    System.exit(1);
                }
                TaggedToken[][] inputSents = null;

                ObjectInputStream modelReader = new ObjectInputStream(
                    new FileInputStream(modelFile));
                System.err.println( "Loading Stagger model ...");
                Tagger tagger = (Tagger)modelReader.readObject();
                lang = tagger.getTaggedData().getLanguage();
                modelReader.close();

                // TODO: experimental feature, might remove later
                tagger.setExtendLexicon(extendLexicon);
                if(!hasNE) tagger.setHasNE(false);

                for(String inputFile : inputFiles) {
                    if(!(inputFile.endsWith(".txt") ||
                         inputFile.endsWith(".txt.gz")))
                    {
                        inputSents = tagger.getTaggedData().readConll(
                            inputFile, null, true,
                            !inputFile.endsWith(".conll"));
                        Evaluation eval = new Evaluation();
                        int count=0;
                        BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(System.out, "UTF-8"));
                        for(TaggedToken[] sent : inputSents) {
                            if (count % 100 == 0 )
                                System.err.print("Tagging sentence nr: "+
                                                 count + "\r" );
                            count++;
                            TaggedToken[] taggedSent =
                                tagger.tagSentence(sent, true, preserve);

                            eval.evaluate(taggedSent, sent);
                            tagger.getTaggedData().writeConllGold(
                                writer, taggedSent, sent, plainOutput);
                        }
                        writer.close();
                        System.err.println( "Tagging sentence nr: "+count);
                        System.err.println(
                            "POS accuracy: "+eval.posAccuracy()+
                            " ("+eval.posCorrect+" / "+
                            eval.posTotal+")");
                        System.err.println(
                            "NE precision: "+eval.nePrecision());
                        System.err.println(
                            "NE recall:    "+eval.neRecall());
                        System.err.println(
                            "NE F-score:   "+eval.neFscore());
                    } else {
                        String fileID =
                            (new File(inputFile)).getName().split(
                                "\\.")[0];
                        BufferedReader reader = openUTF8File(inputFile);
                        BufferedWriter writer = null;
                        if(inputFiles.size() > 1) {
                            String outputFile = inputFile + 
                                (plainOutput? ".plain" : ".conll");
                            writer = new BufferedWriter(
                                new OutputStreamWriter(
                                    new FileOutputStream(
                                        outputFile), "UTF-8"));
                        } else {
                            writer = new BufferedWriter(
                                new OutputStreamWriter(System.out, "UTF-8"));
                        }
                        Tokenizer tokenizer = getTokenizer(reader, lang);
                        ArrayList<Token> sentence;
                        int sentIdx = 0;
                        long base = 0;
                        while((sentence=tokenizer.readSentence())!=null) {
                            TaggedToken[] sent =
                                new TaggedToken[sentence.size()];
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
                            tagger.getTaggedData().writeConllSentence(
                                (writer == null)? System.out : writer,
                                taggedSent, plainOutput);
                            sentIdx++;
                        }
                        tokenizer.yyclose();
                        if(writer != null) writer.close();
                    }
                }
            } else if(args[i].equals("-tokenize")) {
                String inputFile = args[++i];
                BufferedReader reader = openUTF8File(inputFile);
                Tokenizer tokenizer = getTokenizer(reader, lang);
                ArrayList<Token> sentence;
                while((sentence = tokenizer.readSentence()) != null) {
                    if(sentence.size() == 0) continue;
                    if(!plainOutput) {
                        System.out.print(
                            sentence.get(0).value.replace(' ', '_'));
                        for(int j=1; j<sentence.size(); j++) {
                            System.out.print(
                                " " +
                                sentence.get(j).value.replace(' ', '_'));
                        }
                        System.out.println("");
                    } else {
                        for(Token token: sentence) {
                            System.out.println(token.value);
                        }
                        System.out.println();
                    }
                }
                tokenizer.yyclose();
            }
        }
    }
}

