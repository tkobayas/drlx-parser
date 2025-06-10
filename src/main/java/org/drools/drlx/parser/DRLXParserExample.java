package org.drools.drlx.parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

/**
 * Example demonstrating how to use the DRLX parser to parse DRLX language constructs.
 */
public class DRLXParserExample {
    
    public static void main(String[] args) throws Exception {
        // Example DRLX code
        String drlxCode = """
            unit MyRuleUnit;
            
            rule R1 {
                var p : /persons[age > 20],
                do { System.out.println(p.getName()); }
            }
            """;
        
        System.out.println("Parsing DRLX code:");
        System.out.println(drlxCode);
        System.out.println("========================");
        
        // Parse the DRLX code
        ParseTree tree = parseDRLX(drlxCode);
        
        // Print the parse tree
        System.out.println("Parse tree:");
        System.out.println(tree.toStringTree());
        
        // Walk the tree with a custom listener
        ParseTreeWalker walker = new ParseTreeWalker();
        DRLXCustomListener listener = new DRLXCustomListener();
        walker.walk(listener, tree);
    }
    
    /**
     * Parse DRLX code and return the parse tree.
     */
    public static ParseTree parseDRLX(String code) throws Exception {
        // Create a CharStream that reads from the string
        ANTLRInputStream input = new ANTLRInputStream(code);
        
        // Create a lexer that feeds off of input CharStream
        DRLXLexer lexer = new DRLXLexer(input);
        
        // Create a buffer of tokens pulled from the lexer
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        
        // Create a parser that feeds off the tokens buffer
        DRLXParser parser = new DRLXParser(tokens);
        
        // Parse starting from the start_ rule
        return parser.start_();
    }
    
    /**
     * Custom listener to demonstrate walking the parse tree.
     */
    static class DRLXCustomListener extends DRLXParserBaseListener {
        
        @Override
        public void enterUnitDeclaration(DRLXParser.UnitDeclarationContext ctx) {
            String unitName = ctx.identifier().getText();
            System.out.println("Found unit declaration: " + unitName);
        }
        
        @Override
        public void enterRuleDeclaration(DRLXParser.RuleDeclarationContext ctx) {
            String ruleName = ctx.identifier().getText();
            System.out.println("Found rule declaration: " + ruleName);
        }
        
        @Override
        public void enterRulePattern(DRLXParser.RulePatternContext ctx) {
            String varName = ctx.identifier().getText();
            String oopathExpr = ctx.oopathExpression().getText();
            System.out.println("Found rule pattern: var " + varName + " : " + oopathExpr);
        }
        
        @Override
        public void enterRuleAction(DRLXParser.RuleActionContext ctx) {
            System.out.println("Found rule action: do " + ctx.block().getText());
        }
    }
}