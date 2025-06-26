// DRLX Parser - minimal extension of MVEL3 parser

parser grammar DRLXParser;

import Mvel3Parser;

options {
    tokenVocab = DRLXLexer;
}

// Start rule for DRLX - can be either an expression or a compilation unit
drlxStart
    : mvelExpression EOF
    | compilationUnit
    ;
