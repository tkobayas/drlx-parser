package org.drools.drlx.parser;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                
                rule PersonRule {
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
        assertEquals("PersonRule", ruleCtx.ruleName().getText());

        DRLXParser.RuleBodyContext ruleBodyCtx = ruleCtx.ruleBody();
        assertEquals(1, ruleBodyCtx.ruleElement().size());
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
                .ruleElement(0)
                .rulePattern();

        assertEquals("person", patternCtx.identifier().getText());
        assertEquals("/persons[age>21]", patternCtx.oopathExpression().getText());

        DRLXParser.OopathExpressionContext oopathCtx = patternCtx.oopathExpression();
        assertEquals("persons", oopathCtx.identifier().getText());
        assertNotNull(oopathCtx.oopathConstraint());
        assertEquals("[age>21]", oopathCtx.oopathConstraint().getText());
    }

    @Test
    public void testMultipleConstraints() throws Exception {
        String drlxCode = """
                unit TestUnit;
                
                rule TestRule {
                    var person : /persons[age > 21, name == "John", active]
                }
                """;

        ParseTree tree = parseDRLX(drlxCode);
        DRLXParser.Start_Context startCtx = (DRLXParser.Start_Context) tree;
        DRLXParser.RulePatternContext patternCtx = startCtx.drlxCompilationUnit()
                .topLevelDeclaration(0)
                .ruleDeclaration()
                .ruleBody()
                .ruleElement(0)
                .rulePattern();

        assertEquals("person", patternCtx.identifier().getText());
        assertEquals("/persons[age>21,name==\"John\",active]", patternCtx.oopathExpression().getText());

        DRLXParser.OopathExpressionContext oopathCtx = patternCtx.oopathExpression();
        assertEquals("persons", oopathCtx.identifier().getText());
        assertNotNull(oopathCtx.oopathConstraint());

        // Check that we have multiple expressions in the constraint
        DRLXParser.OopathConstraintContext constraintCtx = oopathCtx.oopathConstraint();
        assertEquals(3, constraintCtx.expression().size()); // age > 21, name == "John", active
    }

    @Test
    public void testPatternWithoutBinding() throws Exception {
        String drlxCode = """
                unit TestUnit;
                
                rule TestRule {
                    /persons[age > 25],
                    do { System.out.println("Found person"); }
                }
                """;

        ParseTree tree = parseDRLX(drlxCode);
        DRLXParser.Start_Context startCtx = (DRLXParser.Start_Context) tree;
        DRLXParser.RulePatternContext patternCtx = startCtx.drlxCompilationUnit()
                .topLevelDeclaration(0)
                .ruleDeclaration()
                .ruleBody()
                .ruleElement(0)
                .rulePattern();

        // Verify that there's no identifier (no variable binding)
        assertNull(patternCtx.identifier());

        // Verify the OOPath expression is present
        assertNotNull(patternCtx.oopathExpression());
        assertEquals("/persons[age>25]", patternCtx.oopathExpression().getText());

        DRLXParser.OopathExpressionContext oopathCtx = patternCtx.oopathExpression();
        assertEquals("persons", oopathCtx.identifier().getText());
        assertNotNull(oopathCtx.oopathConstraint());
        assertEquals("[age>25]", oopathCtx.oopathConstraint().getText());
    }

    @Test
    public void testPropertyReactive() throws Exception {
        String drlxCode = """
                unit TestUnit;
                
                rule TestRule {
                    var e : /employees[yearsOfService > 5][basePay, bonusPay],
                    do { System.out.println("Employee bonus updated"); }
                }
                """;

        ParseTree tree = parseDRLX(drlxCode);
        DRLXParser.Start_Context startCtx = (DRLXParser.Start_Context) tree;
        DRLXParser.RulePatternContext patternCtx = startCtx.drlxCompilationUnit()
                .topLevelDeclaration(0)
                .ruleDeclaration()
                .ruleBody()
                .ruleElement(0)
                .rulePattern();

        // Verify variable binding
        assertEquals("e", patternCtx.identifier().getText());

        // Verify OOPath expression with both constraint and property reactive
        DRLXParser.OopathExpressionContext oopathCtx = patternCtx.oopathExpression();
        assertEquals("employees", oopathCtx.identifier().getText());
        assertEquals("/employees[yearsOfService>5][basePay,bonusPay]", oopathCtx.getText());

        // Verify constraint
        assertNotNull(oopathCtx.oopathConstraint());
        assertEquals("[yearsOfService>5]", oopathCtx.oopathConstraint().getText());

        // Verify property reactive
        assertNotNull(oopathCtx.propertyReactive());
        assertEquals("[basePay,bonusPay]", oopathCtx.propertyReactive().getText());

        // Check individual properties
        DRLXParser.PropertyReactiveContext propCtx = oopathCtx.propertyReactive();
        assertEquals(2, propCtx.identifier().size());
        assertEquals("basePay", propCtx.identifier(0).getText());
        assertEquals("bonusPay", propCtx.identifier(1).getText());
    }

    @Test
    public void testSingleBracketInterpretedAsConstraint() throws Exception {
        String drlxCode = """
                unit TestUnit;
                
                rule TestRule {
                    /employees[salary, department]
                }
                """;

        ParseTree tree = parseDRLX(drlxCode);
        DRLXParser.Start_Context startCtx = (DRLXParser.Start_Context) tree;
        DRLXParser.RulePatternContext patternCtx = startCtx.drlxCompilationUnit()
                .topLevelDeclaration(0)
                .ruleDeclaration()
                .ruleBody()
                .ruleElement(0)
                .rulePattern();

        // No variable binding
        assertNull(patternCtx.identifier());

        // Verify OOPath expression
        DRLXParser.OopathExpressionContext oopathCtx = patternCtx.oopathExpression();
        assertEquals("employees", oopathCtx.identifier().getText());
        assertEquals("/employees[salary,department]", oopathCtx.getText());

        // Single bracket is interpreted as constraint, not property reactive
        assertNotNull(oopathCtx.oopathConstraint());
        assertEquals("[salary,department]", oopathCtx.oopathConstraint().getText());
        assertNull(oopathCtx.propertyReactive());
    }

    @Test
    public void testRuleNamingConventions() throws Exception {
        // Valid rule names (start with uppercase)
        String[] validRuleNames = {"Rule1", "MyRule", "ProcessOrderRule", "A", "ABC123"};

        for (String ruleName : validRuleNames) {
            String drlxCode = """
                    unit TestUnit;
                    
                    rule %s {
                        /persons[age > 20]
                    }
                    """.formatted(ruleName);

            ParseTree tree = parseDRLX(drlxCode);
            DRLXParser.Start_Context startCtx = (DRLXParser.Start_Context) tree;
            DRLXParser.RuleDeclarationContext ruleCtx = startCtx.drlxCompilationUnit()
                    .topLevelDeclaration(0)
                    .ruleDeclaration();

            assertEquals(ruleName, ruleCtx.ruleName().getText());

            // Verify naming convention
            assertTrue(Character.isUpperCase(ruleName.charAt(0)),
                       "Rule name should start with uppercase: " + ruleName);
        }

        // Test that invalid names are still parsed (but should trigger warnings)
        String invalidRuleName = "myInvalidRule";
        String drlxCode = """
                unit TestUnit;
                
                rule %s {
                    /persons[age > 20]
                }
                """.formatted(invalidRuleName);

        ParseTree tree = parseDRLX(drlxCode);
        DRLXParser.Start_Context startCtx = (DRLXParser.Start_Context) tree;
        DRLXParser.RuleDeclarationContext ruleCtx = startCtx.drlxCompilationUnit()
                .topLevelDeclaration(0)
                .ruleDeclaration();

        assertEquals(invalidRuleName, ruleCtx.ruleName().getText());
        assertFalse(Character.isUpperCase(invalidRuleName.charAt(0)),
                    "This rule name should NOT follow convention: " + invalidRuleName);
    }

    @Test
    public void testOrConditionalElement() throws Exception {
        String drlxCode = """
                unit TestUnit;
                
                rule TestRule {
                    or ( var a : /persons[hair=="purple"], 
                         var b : /persons[hair=="indigo"], 
                         var c : /persons[hair=="black"] ),
                    do { System.out.println("Found colorful hair"); }
                }
                """;

        ParseTree tree = parseDRLX(drlxCode);
        DRLXParser.Start_Context startCtx = (DRLXParser.Start_Context) tree;
        DRLXParser.RuleBodyContext ruleBodyCtx = startCtx.drlxCompilationUnit()
                .topLevelDeclaration(0)
                .ruleDeclaration()
                .ruleBody();

        // Should have 1 rule element: OR conditional (action is separate)
        assertEquals(1, ruleBodyCtx.ruleElement().size());

        // First element should be conditional element (OR)
        DRLXParser.RuleElementContext firstElement = ruleBodyCtx.ruleElement(0);
        assertNotNull(firstElement.conditionalElement());
        assertNotNull(firstElement.conditionalElement().orElement());

        // OR element should have parentheses with multiple elements
        DRLXParser.OrElementContext orCtx = firstElement.conditionalElement().orElement();
        assertEquals(3, orCtx.ruleElement().size()); // Three patterns inside OR

        // Verify action exists
        assertNotNull(ruleBodyCtx.ruleAction());
    }

    @Test
    public void testNotConditionalElement() throws Exception {
        String drlxCode = """
                unit TestUnit;
                
                rule TestRule {
                    not /persons[age < 18],
                    var adult : /persons[age >= 18],
                    do { System.out.println("Adult found, no minors"); }
                }
                """;

        ParseTree tree = parseDRLX(drlxCode);
        DRLXParser.Start_Context startCtx = (DRLXParser.Start_Context) tree;
        DRLXParser.RuleBodyContext ruleBodyCtx = startCtx.drlxCompilationUnit()
                .topLevelDeclaration(0)
                .ruleDeclaration()
                .ruleBody();

        // Should have 2 rule elements: NOT conditional + pattern (action is separate)
        assertEquals(2, ruleBodyCtx.ruleElement().size());

        // First element should be NOT conditional
        DRLXParser.RuleElementContext firstElement = ruleBodyCtx.ruleElement(0);
        assertNotNull(firstElement.conditionalElement());
        assertNotNull(firstElement.conditionalElement().notElement());

        // NOT element should be single pattern (no parentheses)
        DRLXParser.NotElementContext notCtx = firstElement.conditionalElement().notElement();
        assertNotNull(notCtx.rulePattern());

        // Second element should be regular pattern
        DRLXParser.RuleElementContext secondElement = ruleBodyCtx.ruleElement(1);
        assertNotNull(secondElement.rulePattern());
        assertEquals("adult", secondElement.rulePattern().identifier().getText());

        // Verify action exists
        assertNotNull(ruleBodyCtx.ruleAction());
    }

    @Test
    public void testSingleElementOrAndNot() throws Exception {
        String drlxCode = """
                unit TestUnit;
                
                rule TestRule {
                    or var single : /persons[hair=="red"],
                    not /minors[age < 13]
                }
                """;

        ParseTree tree = parseDRLX(drlxCode);
        DRLXParser.Start_Context startCtx = (DRLXParser.Start_Context) tree;
        DRLXParser.RuleBodyContext ruleBodyCtx = startCtx.drlxCompilationUnit()
                .topLevelDeclaration(0)
                .ruleDeclaration()
                .ruleBody();

        assertEquals(2, ruleBodyCtx.ruleElement().size());

        // First element: OR with single pattern (no parentheses)
        DRLXParser.OrElementContext orCtx = ruleBodyCtx.ruleElement(0).conditionalElement().orElement();
        assertNotNull(orCtx.rulePattern()); // Single pattern form
        assertEquals(0, orCtx.ruleElement().size()); // No parentheses form

        // Second element: NOT with single pattern (no parentheses)  
        DRLXParser.NotElementContext notCtx = ruleBodyCtx.ruleElement(1).conditionalElement().notElement();
        assertNotNull(notCtx.rulePattern()); // Single pattern form
        assertEquals(0, notCtx.ruleElement().size()); // No parentheses form
    }

    @Test
    public void testActionSyntaxVariations() throws Exception {
        // Test traditional do block syntax
        String traditionalSyntax = """
                unit TestUnit;
                
                rule TraditionalRule {
                    var p : /persons[age > 20],
                    do { System.out.println(p.getName()); }
                }
                """;

        ParseTree tree1 = parseDRLX(traditionalSyntax);
        DRLXParser.RuleActionContext actionCtx1 = extractRuleAction(tree1);
        assertNotNull(actionCtx1.block());
        assertTrue(actionCtx1.getText().startsWith("do"));

        // Test block without do keyword
        String blockWithoutDo = """
                unit TestUnit;
                
                rule BlockRule {
                    var p : /persons[age > 20],
                    { System.out.println(p.getName()); }
                }
                """;

        ParseTree tree2 = parseDRLX(blockWithoutDo);
        DRLXParser.RuleActionContext actionCtx2 = extractRuleAction(tree2);
        assertNotNull(actionCtx2.block());
        assertFalse(actionCtx2.getText().startsWith("do"));

        // Test single statement with semicolon
        String singleStatement = """
                unit TestUnit;
                
                rule StatementRule {
                    var p : /persons[age > 20],
                    System.out.println(p.getName());
                }
                """;

        ParseTree tree3 = parseDRLX(singleStatement);
        DRLXParser.RuleActionContext actionCtx3 = extractRuleAction(tree3);
        assertNotNull(actionCtx3.expressionStatement());

        // Test single expression without semicolon
        String singleExpression = """
                unit TestUnit;
                
                rule ExpressionRule {
                    var p : /persons[age > 20],
                    System.out.println(p.getName())
                }
                """;

        ParseTree tree4 = parseDRLX(singleExpression);
        DRLXParser.RuleActionContext actionCtx4 = extractRuleAction(tree4);
        assertNotNull(actionCtx4.expression());
    }

    private DRLXParser.RuleActionContext extractRuleAction(ParseTree tree) {
        DRLXParser.Start_Context startCtx = (DRLXParser.Start_Context) tree;
        return startCtx.drlxCompilationUnit()
                .topLevelDeclaration(0)
                .ruleDeclaration()
                .ruleBody()
                .ruleAction();
    }

    @Test
    public void testWithPackageAndImports() throws Exception {
        String drlxCode = """
                package com.example;
                
                import java.util.List;
                import static java.lang.System.out;
                
                unit MyRuleUnit;
                
                rule PersonRule {
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