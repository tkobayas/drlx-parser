# DRLX Parser

A Maven project that generates Java code from ANTLR4 grammar files for the DRLX language, which extends Java 20.

## Overview

DRLX (Drools Language eXtension) is a domain-specific language that extends Java 20 syntax with rule-based constructs:

- `unit` declarations for defining rule units
- `rule` declarations for defining business rules
- OOPath expressions (`/collection[constraint]`) for pattern matching
- `do` blocks for actions

## Example DRLX Code

```drlx
unit MyRuleUnit;

rule R1 {
    var p : /persons[age > 20],
    do { System.out.println(p.getName()); }
}
```

## Project Structure

```
drlx-parser/
├── pom.xml                           # Maven configuration
├── src/main/antlr4/org/drools/drlx/parser/  # ANTLR4 grammar files
│   ├── DRLXLexer.g4                  # DRLX lexer (extends Java 20)
│   ├── DRLXParser.g4                 # DRLX parser (extends Java 20)
│   ├── Java20Lexer.g4                # Java 20 base lexer
│   └── Java20Parser.g4               # Java 20 base parser
├── src/main/java/org/drools/drlx/parser/    # Generated Java classes
│   └── DRLXParserExample.java        # Usage example
└── src/test/java/org/drools/drlx/parser/    # Test classes
    └── DRLXParserTest.java           # Unit tests
```

## Building the Project

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

### Build Commands

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Generate JAR
mvn package

# Run the example
mvn exec:java -Dexec.mainClass="org.drlx.parser.DRLXParserExample"
```

## Generated Java Classes

The ANTLR4 Maven plugin generates the following Java classes:

### Lexer
- `DRLXLexer.java` - Tokenizes DRLX source code

### Parser
- `DRLXParser.java` - Parses DRLX syntax trees
- Context classes for each grammar rule (e.g., `UnitDeclarationContext`, `RuleDeclarationContext`)

### Listeners and Visitors
- `DRLXParserListener.java` - Interface for parse tree listeners
- `DRLXParserBaseListener.java` - Base implementation of listener
- `DRLXParserVisitor.java` - Interface for parse tree visitors
- `DRLXParserBaseVisitor.java` - Base implementation of visitor

## Usage

### Basic Parsing

```java
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

// Parse DRLX code
String drlxCode = "unit MyUnit; rule R1 { var p : /persons[age > 20] }";
ANTLRInputStream input = new ANTLRInputStream(drlxCode);
DRLXLexer lexer = new DRLXLexer(input);
CommonTokenStream tokens = new CommonTokenStream(lexer);
DRLXParser parser = new DRLXParser(tokens);
ParseTree tree = parser.start_();
```

### Using Listeners

```java
// Walk the parse tree with a custom listener
ParseTreeWalker walker = new ParseTreeWalker();
DRLXParserBaseListener listener = new DRLXParserBaseListener() {
    @Override
    public void enterUnitDeclaration(DRLXParser.UnitDeclarationContext ctx) {
        System.out.println("Found unit: " + ctx.identifier().getText());
    }
    
    @Override
    public void enterRuleDeclaration(DRLXParser.RuleDeclarationContext ctx) {
        System.out.println("Found rule: " + ctx.identifier().getText());
    }
};
walker.walk(listener, tree);
```

## DRLX Language Features

### Unit Declarations
```drlx
unit MyRuleUnit;

unit ExtendedUnit extends BaseUnit;
```

### Rule Declarations
```drlx
rule MyRule {
    var person : /persons[age > 18],
    var account : /accounts[owner == person],
    do {
        account.activate();
        System.out.println("Activated account for " + person.getName());
    }
}
```

### OOPath Expressions
```drlx
/collection[constraint]           // Basic OOPath
/persons[age > 18]               // Age constraint
/accounts[balance > 1000]        // Balance constraint
/orders[status == "PENDING"]     // Status constraint
```

### Variable Binding
```drlx
var variableName : /collection[constraint]
```

### Action Blocks
```drlx
do {
    // Java code here
    System.out.println("Rule fired!");
}
```

## Maven Configuration

The project uses the ANTLR4 Maven plugin to generate Java code from grammar files:

```xml
<plugin>
    <groupId>org.antlr</groupId>
    <artifactId>antlr4-maven-plugin</artifactId>
    <version>4.13.1</version>
    <configuration>
        <outputDirectory>${project.build.directory}/generated-sources/antlr4/org/drlx/parser</outputDirectory>
        <visitor>true</visitor>
        <listener>true</listener>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>antlr4</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## Dependencies

- **ANTLR4 Runtime** (4.13.1) - Required for parsing
- **JUnit Jupiter** (5.10.0) - For testing (test scope)

## Testing

Run the test suite with:

```bash
mvn test
```

The tests verify:
- Basic unit declarations
- Rule declarations with patterns and actions
- OOPath expression parsing
- Package and import declarations
- Variable binding syntax

## License

This project demonstrates ANTLR4 grammar development for educational purposes.