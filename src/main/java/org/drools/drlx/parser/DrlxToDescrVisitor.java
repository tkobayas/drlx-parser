/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.drools.drlx.parser;

import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.drools.drl.ast.descr.ExprConstraintDescr;
import org.drools.drl.ast.descr.FromDescr;
import org.drools.drl.ast.descr.ImportDescr;
import org.drools.drl.ast.descr.MVELExprDescr;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.drl.ast.descr.PatternDescr;
import org.drools.drl.ast.descr.RuleDescr;
import org.drools.drl.ast.descr.UnitDescr;

/**
 * Very small visitor that walks a DRLX parse tree and builds drools-descr objects.
 * This is intentionally minimal and currently handles only the simple rule shape used in tests.
 */
public class DrlxToDescrVisitor extends DRLXParserBaseVisitor<Object> {

    private final TokenStream tokens;

    public DrlxToDescrVisitor() {
        this(null);
    }

    public DrlxToDescrVisitor(TokenStream tokens) {
        this.tokens = tokens;
    }

    @Override
    public PackageDescr visitDrlxCompilationUnit(DRLXParser.DrlxCompilationUnitContext ctx) {
        PackageDescr pkg = new PackageDescr();
        if (ctx.packageDeclaration() != null) {
            pkg.setName(ctx.packageDeclaration().qualifiedName().getText());
        }

        if (ctx.importDeclaration() != null) {
            ctx.importDeclaration().stream()
                    .map(this::visit)
                    .filter(ImportDescr.class::isInstance)
                    .map(ImportDescr.class::cast)
                    .forEach(pkg::addImport);
        }

        if (ctx.unitDeclaration() != null) {
            UnitDescr unitDescr = (UnitDescr) visit(ctx.unitDeclaration());
            pkg.setUnit(unitDescr);
        }

        if (ctx.ruleDeclaration() != null) {
            ctx.ruleDeclaration().stream()
                    .map(this::visit)
                    .filter(RuleDescr.class::isInstance)
                    .map(RuleDescr.class::cast)
                    .forEach(rule -> {
                        rule.setUnit(pkg.getUnit());
                        pkg.addRule(rule);
                    });
        }

        return pkg;
    }

    @Override
    public ImportDescr visitImportDeclaration(DRLXParser.ImportDeclarationContext ctx) {
        String target = ctx.qualifiedName().getText();
        if (ctx.MUL() != null) {
            target = target + ".*";
        }
        return new ImportDescr(target);
    }

    @Override
    public UnitDescr visitUnitDeclaration(DRLXParser.UnitDeclarationContext ctx) {
        return new UnitDescr(ctx.identifier().getText());
    }

    @Override
    public RuleDescr visitRuleDeclaration(DRLXParser.RuleDeclarationContext ctx) {
        RuleDescr ruleDescr = new RuleDescr(ctx.identifier().getText());
        if (ctx.ruleBody() != null) {
            ctx.ruleBody().ruleItem().forEach(item -> {
                if (item.rulePattern() != null) {
                    PatternDescr patternDescr = (PatternDescr) visit(item.rulePattern());
                    ruleDescr.getLhs().addDescr(patternDescr);
                } else if (item.ruleConsequence() != null) {
                    String consequence = (String) visit(item.ruleConsequence());
                    ruleDescr.setConsequence(consequence);
                }
            });
        }
        return ruleDescr;
    }

    @Override
    public PatternDescr visitRulePattern(DRLXParser.RulePatternContext ctx) {
        // for now, take the variable type (= first identifier) as the pattern type
        // Later, we will likely resolve the type via RuleUnit's DataSource definitions.
        PatternDescr patternDescr = new PatternDescr(ctx.identifier(0).getText(), ctx.identifier(1).getText());

        // for now, take the oopath base as a FromDescr data source
        // Later, we will likely use only the first part of the oopath as the data source. The rest would be constraints on the pattern.
        String oopathBaseText = stripOopathBase(getText(ctx.oopathExpression()));
        FromDescr fromDescr = new FromDescr();
        fromDescr.setDataSource(new MVELExprDescr(oopathBaseText));
        patternDescr.setSource(fromDescr);

        List<String> conditions = extractConditions(ctx.oopathExpression());
        conditions.forEach(condition -> patternDescr.addConstraint(new ExprConstraintDescr(condition)));
        return patternDescr;
    }

    private String stripOopathBase(String oopath) {
        String result = oopath;
        // remove trailing "[...]" part. For now, we assume only one such part exists at the end.
        int bracketIndex = result.indexOf('[');
        if (bracketIndex >= 0) {
            result = result.substring(0, bracketIndex);
        }
        return result;
    }

    @Override
    public String visitRuleConsequence(DRLXParser.RuleConsequenceContext ctx) {
        String statementText = getText(ctx.statement());
        return trimBraces(statementText);
    }

    private List<String> extractConditions(DRLXParser.OopathExpressionContext ctx) {
        List<DRLXParser.OopathChunkContext> oopathChunkContexts = ctx.oopathChunk();
        if (oopathChunkContexts.isEmpty()) {
            return List.of();
        }
        // For now, we assume a constraint exists only in the last chunk.
        DRLXParser.OopathChunkContext lastOopathChunkContext = oopathChunkContexts.get(oopathChunkContexts.size() - 1);
        return lastOopathChunkContext.drlxExpression().stream()
                .map(this::getText)
                .collect(Collectors.toList());
    }

    private String trimBraces(String text) {
        if (text == null) {
            return null;
        }
        String stripped = text;
        if (text.startsWith("{") && text.endsWith("}")) {
            stripped = text.substring(1, text.length() - 1);
        }
        return stripped.trim();
    }

    private String getText(ParserRuleContext ctx) {
        return tokens != null ? tokens.getText(ctx) : ctx.getText();
    }
}
