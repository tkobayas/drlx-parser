// DRLX Parser - minimal extension of MVEL3 parser

parser grammar DRLXParser;

import Mvel3Parser;

options {
    tokenVocab = DRLXLexer;
}

// Start rule for DRLX expressions
drlxStart
    : mvelExpression EOF
    ;