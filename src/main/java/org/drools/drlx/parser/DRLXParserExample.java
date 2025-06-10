package org.drools.drlx.parser;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

/**
 * Example demonstrating how to use the DRLX parser to parse DRLX language constructs.
 */
public class DRLXParserExample {

    public static void main(String[] args) throws Exception {
        // Example DRLX code
        String drlxCode = """
                unit MyRuleUnit;
                
                rule PersonRule {
                    var p : /persons[age > 20],
                    System.out.println(p.getName())
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
            String ruleName = ctx.ruleName().getText();
            System.out.println("Found rule declaration: " + ruleName);

            // Check naming convention
            if (!Character.isUpperCase(ruleName.charAt(0))) {
                System.out.println("  WARNING: Rule name should start with uppercase letter (Java class convention)");
            }
        }

        @Override
        public void enterRulePattern(DRLXParser.RulePatternContext ctx) {
            String oopathExpr = ctx.oopathExpression().getText();
            if (ctx.identifier() != null) {
                String varName = ctx.identifier().getText();
                System.out.println("Found rule pattern: var " + varName + " : " + oopathExpr);
            } else {
                System.out.println("Found rule pattern: " + oopathExpr);
            }

            // Check for Property Reactive
            DRLXParser.OopathExpressionContext oopathCtx = ctx.oopathExpression();
            if (oopathCtx.propertyReactive() != null) {
                String properties = oopathCtx.propertyReactive().getText();
                System.out.println("  Property Reactive: " + properties);
            }
        }

        @Override
        public void enterRuleAction(DRLXParser.RuleActionContext ctx) {
            if (ctx.block() != null) {
                if (ctx.getStart().getText().equals("do")) {
                    System.out.println("Found rule action: do " + ctx.block().getText());
                } else {
                    System.out.println("Found rule action: " + ctx.block().getText());
                }
            } else if (ctx.expressionStatement() != null) {
                System.out.println("Found rule action: " + ctx.expressionStatement().getText());
            } else if (ctx.expression() != null) {
                System.out.println("Found rule action: " + ctx.expression().getText());
            }
        }

        @Override
        public void enterOrElement(DRLXParser.OrElementContext ctx) {
            System.out.println("Found OR conditional element");
        }

        @Override
        public void enterNotElement(DRLXParser.NotElementContext ctx) {
            System.out.println("Found NOT conditional element");
        }
    }
}