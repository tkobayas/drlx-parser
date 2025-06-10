# DRLX Parser

A Maven project that generates Java code from ANTLR4 grammar files for the DRLX language, which extends Java 20.

## Overview

DRLX (Drools Rule Language eXtension) is a domain-specific language that extends Java 20 syntax with rule-based constructs:

- `unit` declarations for defining rule units
- `rule` declarations for defining business rules
- OOPath expressions (`/collection[constraint]`) for pattern matching
- `do` blocks for actions

## Example DRLX Code

```drlx
unit MyRuleUnit;

rule PersonRule {
    var p : /persons[age > 20],
    System.out.println(p.getName())
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
```

### Rule Declarations
Rule names must follow Java class naming conventions (start with uppercase letter):

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
/collection[constraint]                    // Basic OOPath
/persons[age > 18]                        // Single constraint
/accounts[balance > 1000, active]        // Multiple constraints
/orders[status == "PENDING", priority > 5] // Multiple constraints
/employees[yearsOfService > 5][basePay, bonusPay] // With Property Reactive
```

### Variable Binding
```drlx
// With variable binding
var person : /persons[age > 18]

// Without variable binding (anonymous pattern)
/persons[age > 18]
```

### Property Reactive
Property Reactive allows you to specify which object properties should trigger rule re-evaluation when updated. Property Reactive brackets `[properties]` come after constraint brackets:

```drlx
// Rule re-evaluates when basePay or bonusPay changes
var e : /employees[yearsOfService > 5][basePay, bonusPay]

// Property Reactive with minimal constraint
/products[true][price, category]

// Combined with multiple constraints
/customers[active, premium][name, email, phone]
```

### Group Conditional Elements
DRLX supports logical grouping of patterns using conditional elements:

```drlx
// OR conditional element - matches if any pattern matches
or ( var a : /persons[hair=="purple"], 
     var b : /persons[hair=="indigo"], 
     var c : /persons[hair=="black"] )

// Single element OR (parentheses optional)
or var single : /persons[hair=="red"]

// NOT conditional element - matches if pattern does NOT match
not /persons[age < 18]

// NOT with multiple elements
not ( /minors[age < 13], 
      /restricted[access=="denied"] )
```

### Action Blocks
DRLX supports multiple action syntax variations for flexibility:

```drlx
// Traditional do block syntax
do {
    System.out.println("Rule fired!");
    account.activate();
}

// Block without do keyword
{
    System.out.println("Rule fired!");
    account.activate();
}

// Single statement with semicolon
System.out.println("Rule fired!");

// Single expression without semicolon (most concise)
System.out.println("Rule fired")
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