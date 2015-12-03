#!/bin/bash

# Usage example:
#
# scripts/cross.sh identifier stagger-xyz.jar corpus-directory
#                  "training-args" "testing-args"
#
# Make sure to include -lang xx in training-args.
#
# The last parameter is optional, in case one wants to test on another set of
# folds than the default (which is 00 - 09)

IDENT="$1"
JARFILE="$2"
CORPUSDIR="$3"
TRAINARGS="$4"
TESTARGS="$5"
FOLDS="$6"
if [ -z "$6" ]; then
    FOLDS="00 01 02 03 04 05 06 07 08 09"
fi

#if [ -e "$IDENT" ]; then
#    echo "Error: $IDENT already exists"
#    exit 1
#fi
mkdir "$IDENT"

for fold in $FOLDS; do
    if [ -e "$IDENT/fold$fold-test.conll" ]; then
        echo "Fold $fold already done."
    else
        echo "Training fold $fold"
        time java -ea -Xmx4G -jar "$JARFILE" \
            -modelfile "$IDENT"/fold"$fold".bin \
            -trainfile "$CORPUSDIR/fold$fold-train".conll \
            -devfile "$CORPUSDIR/fold$fold-dev".conll \
            $TRAINARGS -train 2>&1 | tee "$CORPUSDIR/fold$fold.log" && \
        echo "Tagging fold $fold" && \
        time java -ea -Xmx4G -jar "$JARFILE" \
            -modelfile "$IDENT"/fold"$fold".bin \
            $TESTARGS \
            -tag "$CORPUSDIR/fold$fold-test".conll \
            >"$IDENT"/"fold$fold-test".conll
    fi
done

