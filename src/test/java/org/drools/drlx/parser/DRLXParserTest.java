package org.drools.drlx.parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for the DRLX parser.
 */
public class DRLXParserTest {
    
    @Test
    public void testBasicUnitDeclaration() throws Exception {
        String drlxCode = """
            unit MyRuleUnit;
            """;
        
        ParseTree tree = parseDRLX(drlxCode);
        assertNotNull(tree);
        
        // Verify the parse tree structure
        assertTrue(tree instanceof DRLXParser.Start_Context);
        DRLXParser.Start_Context startCtx = (DRLXParser.Start_Context) tree;
        assertNotNull(startCtx.drlxCompilationUnit());
        assertNotNull(startCtx.drlxCompilationUnit().unitDeclaration());
        assertEquals("MyRuleUnit", startCtx.drlxCompilationUnit().unitDeclaration().identifier().getText());
    }
    
    @Test
    public void testUnitWithRule() throws Exception {
        String drlxCode = """
            unit MyRuleUnit;
            
            rule R1 {
                var p : /persons[age > 20],
                do { System.out.println(p.getName()); }
            }
            """;
        
        ParseTree tree = parseDRLX(drlxCode);
        assertNotNull(tree);
        
        // Extract unit and rule information
        DRLXParser.Start_Context startCtx = (DRLXParser.Start_Context) tree;
        DRLXParser.UnitDeclarationContext unitCtx = startCtx.drlxCompilationUnit().unitDeclaration();
        
        assertEquals("MyRuleUnit", unitCtx.identifier().getText());
        assertEquals(1, startCtx.drlxCompilationUnit().topLevelDeclaration().size());
        
        DRLXParser.TopLevelDeclarationContext topLevelCtx = startCtx.drlxCompilationUnit().topLevelDeclaration(0);
        assertNotNull(topLevelCtx.ruleDeclaration());
        
        DRLXParser.RuleDeclarationContext ruleCtx = topLevelCtx.ruleDeclaration();
        assertEquals("R1", ruleCtx.identifier().getText());
        
        DRLXParser.RuleBodyContext ruleBodyCtx = ruleCtx.ruleBody();
        assertEquals(1, ruleBodyCtx.rulePattern().size());
        assertNotNull(ruleBodyCtx.ruleAction());
    }
    
    @Test
    public void testRulePattern() throws Exception {
        String drlxCode = """
            unit TestUnit;
            
            rule TestRule {
                var person : /persons[age > 21]
            }
            """;
        
        ParseTree tree = parseDRLX(drlxCode);
        DRLXParser.Start_Context startCtx = (DRLXParser.Start_Context) tree;
        DRLXParser.RulePatternContext patternCtx = startCtx.drlxCompilationUnit()
            .topLevelDeclaration(0)
            .ruleDeclaration()
            .ruleBody()
            .rulePattern(0);
        
        assertEquals("person", patternCtx.identifier().getText());
        assertEquals("/persons[age>21]", patternCtx.oopathExpression().getText());
        
        DRLXParser.OopathExpressionContext oopathCtx = patternCtx.oopathExpression();
        assertEquals("persons", oopathCtx.identifier().getText());
        assertNotNull(oopathCtx.oopathConstraint());
        assertEquals("[age>21]", oopathCtx.oopathConstraint().getText());
    }
    
    @Test
    public void testWithPackageAndImports() throws Exception {
        String drlxCode = """
            package com.example;
            
            import java.util.List;
            import static java.lang.System.out;
            
            unit MyRuleUnit;
            
            rule R1 {
                var p : /persons[age > 20],
                do { out.println(p.getName()); }
            }
            """;
        
        ParseTree tree = parseDRLX(drlxCode);
        assertNotNull(tree);
        
        DRLXParser.Start_Context startCtx = (DRLXParser.Start_Context) tree;
        DRLXParser.DrlxCompilationUnitContext compUnitCtx = startCtx.drlxCompilationUnit();
        
        // Check package declaration
        assertNotNull(compUnitCtx.packageDeclaration());
        assertEquals("com.example", compUnitCtx.packageDeclaration().getText().replaceAll("[\\s;]", "").substring(7));
        
        // Check import declarations
        assertEquals(2, compUnitCtx.importDeclaration().size());
        
        // Check unit declaration
        assertNotNull(compUnitCtx.unitDeclaration());
        assertEquals("MyRuleUnit", compUnitCtx.unitDeclaration().identifier().getText());
    }
    
    /**
     * Helper method to parse DRLX code.
     */
    private ParseTree parseDRLX(String code) throws Exception {
        ANTLRInputStream input = new ANTLRInputStream(code);
        DRLXLexer lexer = new DRLXLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DRLXParser parser = new DRLXParser(tokens);
        return parser.start_();
    }
}