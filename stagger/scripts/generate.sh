#!/bin/sh

#echo "Generating name embeddings."
#scripts/create_namedictionary.py \
#    <../namn/sverige-efternamn.txt >data/sverige-efternamn-4.embed
#
#scripts/create_namedictionary.py \
#    <../namn/sverige-kvinnor.txt >data/sverige-kvinnor-4.embed
#
#scripts/create_namedictionary.py \
#    <../namn/sverige-maen.txt >data/sverige-maen-4.embed
#
#scripts/create_geonamesdictionary.py \
#    </data0/corpora/geonames/SE.txt >data/geonames-se.embed

DATA=../../data/stagger

echo "Creating saldo-all.txt"
scripts/lexicon.py \
    UO:$DATA/embed/web1t.embed \
    PM:$DATA/embed/sverige-efternamn-4.embed \
    PM:$DATA/embed/sverige-kvinnor-4.embed \
    PM:$DATA/embed/sverige-maen-4.embed \
    PM:$DATA/embed/geonames-se.embed \
    $DATA/lexicon/saldom.xml >$DATA/lexicon/saldo-all.txt 2>/dev/null

echo "Creating saldo.txt"
scripts/lexicon.py \
    $DATA/lexicon/saldom.xml >$DATA/lexicon/saldo.txt 2>/dev/null

