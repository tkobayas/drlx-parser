# 

[**Overview	1**](#overview)

[**Todo	2**](#todo)

[**Ideas to explore	3**](#ideas-to-explore)

[**Unknown or unresolved or Unhappy with	3**](#unknown-or-unresolved-or-unhappy-with)

[**DRLXXXX for DRLOLD People	4**](#drlxxxx-for-drlold-people)

[Patterns and Entry Points	4](#patterns-and-entry-points)

[Property Reactive	5](#property-reactive)

[Group Conditional Elements	5](#group-conditional-elements)

[Rules and Consequences	5](#rules-and-consequences)

[Positional and Object Named syntax (POSL).	6](#positional-and-object-named-syntax-\(posl\).)

[‘from’	6](#‘from’)

[Query	7](#query)

[Rule Attributes (Annotations)	9](#rule-attributes-\(annotations\))

[**Java++ language enhancements	9**](#java++-language-enhancements)

[Property accessors	9](#property-accessors)

[‘with’ style blocks	9](#‘with’-style-blocks)

[List access and Map access	9](#list-access-and-map-access)

[Inline cast and coercion 	10](#inline-cast-and-coercion )

[**Rule Literal Models	10**](#rule-literal-models)

[**Units of Execution and Data Sources	10**](#units-of-execution-and-data-sources)

[**Simple Reactive rule and bindings	11**](#simple-reactive-rule-and-bindings)

[**Passive elements	12**](#passive-elements)

[**Pluggable Operators	12**](#pluggable-operators)

[**Add/Remove/Update	12**](#add/remove/update)

[**Literal expressions are cached	14**](#literal-expressions-are-cached)

[**Casting and coercion with rule elements	14**](#casting-and-coercion-with-rule-elements)

[**Positional and Object Named Language (PONL) aka RuleML POSL	15**](#positional-and-object-named-language-\(ponl\)-aka-ruleml-posl)

[**Property Specific	16**](#property-specific)

[**‘and’ / ‘or’ structures	16**](#‘and’-/-‘or’-structures)

[**‘not’ / ‘exists’	17**](#‘not’-/-‘exists’)

[**Implicit type with ‘var’ vs explicit type	18**](#implicit-type-with-‘var’-vs-explicit-type)

[**Iterate versus assignment bindings with expressions	18**](#iterate-versus-assignment-bindings-with-expressions)

[**‘from’ versus ‘/’	18**](#‘from’-versus-‘/’)

[**‘do’ :  Immediate vs Agenda Executions	19**](#‘do’-:-immediate-vs-agenda-executions)

[**Rising/Falling the three edges of firings	21**](#rising/falling-the-three-edges-of-firings)

[**‘drain’	23**](#‘drain’)

[**‘test’	24**](#‘test’)

[**‘if’	24**](#‘if’)

[**‘match’ (switch)	26**](#‘match’-\(switch\))

[**Actors and async messaging	27**](#actors-and-async-messaging)

[**DataDriven and Existence Driven	29**](#datadriven-and-existence-driven)

[**‘refresh’	29**](#‘refresh’)

[**Accumulate	30**](#accumulate)

[Simple and single function accumulates	30](#simple-and-single-function-accumulates)

[‘acc’ keyword 2 parameters	31](#‘acc’-keyword-2-parameters)

[‘acc’ keyword 3 parameters	31](#‘acc’-keyword-3-parameters)

[**Group By	33**](#group-by)

[**Queries	34**](#queries)

[Out Vars	37](#out-vars)

[Results Binding	37](#results-binding)

[Tabling	37](#tabling)

[Abduction TODO	38](#abduction-todo)

[Genericised Rules  / Hilog (IGNORE TODO)	38](#genericised-rules-/-hilog-\(ignore-todo\))

[**Needs and opportunistic backward chaining	39**](#needs-and-opportunistic-backward-chaining)

[**Anonymous rules inside of ‘do’	39**](#anonymous-rules-inside-of-‘do’)

[**Left, Right and Inner Joins	39**](#left,-right-and-inner-joins)

[**Java Streams API	39**](#java-streams-api)

[**Windows	40**](#windows)

[**Sequencing \[TODO, DO NOT READ YET\]	41**](#sequencing-[todo,-do-not-read-yet])

[The "followed by" (-\>) conditional element	41](#the-"followed-by"-\(-\>\)-conditional-element)

[The "strictly followed by" (=\>) conditional element	42](#the-"strictly-followed-by"-\(=\>\)-conditional-element)

[The "loosely followed by" (\~\>) conditional element	42](#the-"loosely-followed-by"-\(~\>\)-conditional-element)

[The "independently followed by" (\\\\) conditional element	43](#the-"independently-followed-by"-\(\\\\\)-conditional-element)

[Repetition	43](#repetition)

[Variable bindings and the concept of multi-dimensional tuples	44](#variable-bindings-and-the-concept-of-multi-dimensional-tuples)

[Addressing individual elements in a multi-dimensional tuple	45](#addressing-individual-elements-in-a-multi-dimensional-tuple)

[The event qualifiers	1](#the-event-qualifiers)

[‘first’	46](#‘first’)

[‘every’	47](#‘every’)

[**Rule Structure  (EBNF) TODO	47**](#rule-structure-\(ebnf\)-todo)

# 

# 

# Overview {#overview}

This takes a similar approach to previous DRLX iterations, however a more ‘lispy’ like approach to syntax seems to solve many of the other challenges of the prior approaches. Those would get too complex or too ambiguous \- lack of terminator was a constant struggle. This works well with the ‘and’, ‘or’ structures and allows accumulate and others to have ‘sequences’ of parameters, which makes the intent very clear at each position. Because it is ‘lispy’ and allows a tree structure a ‘,’ separator is used to separate conditional elements \- which works well allowing ; to be used to separate statements within a code block.

Each ‘,’ separated part can have an iteration or assignment or action, and standalone assignments from expressions are allowed per ‘,’ separator.

It also introduces implicit ‘var’ binding vs typed bindings. Within \[\] blocks, and \[\] blocks only, \== is mapped to ‘equals’ var1.equals(var2).

Rules and Queries now have a unified with consistent syntax, meaning a separate ‘query’ keyword is no longer needed.

I have also managed to get a version of queries that are consistent, regular, and nice looking syntax \- especially for transitive queries, which were ugly in v7 DRL.

This version does not have named consequences, as there can be nested ‘do’. I could see labelled blocks  as something useful to aid readability, but not speccing this now.

Note \-\> 

* insert/update/delete notation to be aligned with java collections with add/remove/update.  
* Use Handle instead of FactHandle  
  * Let’s stop using the term Fact, it’s Objects and Events.  
  * Longer version is now object handle, instead of fact handle.

# Todo {#todo}

* ~~Property specific.~~  
* ~~Document ? passive (no reactive) iteration ?/persons~~  
* ~~‘do’ actions can actually be supported at any position in the CE tree. Their executions can be immediate or scheduled by the agenda. Yes it would allow further non ‘do’ elements after the ‘do’~~.  
* ~~Support a special ‘set’ that supports optional functions for the rising and falling edges of propagation.~~  
* Logical object support at any place a ‘do’ is, sugar syntax may be added to avoid the ‘do’ if there is only a single logical action.  
* Support for taking advantage of life cycles, especially the set (@All) life cycle (see [https://developer.jboss.org/docs/DOC-47925](https://developer.jboss.org/docs/DOC-47925))  
* Efficiently expose the match elements for acc and groupBy, i.e. give access to Collection that wraps the TupleSet.  
* Returning a single value from a subnetwork.  
* ‘‘take’ing on Stream datasources, and potentially ‘yield’ing and . Because of the way we do ‘set’ propagations, controls can be placed to n objects from the datasource before propagating further. This means it will give a chance for further matches and ‘do’ executions in child nodes, before getting more from the stream.  
* Prologisms  
  * ‘neg’ operator, for strong negation. This includes how a user negates data.  
  * ‘cut’, especially when combined with ‘or’ use cases.  
  * Data structure unification, with Lists and Maps.  
  * Head | Tail syntax and use cases.  
* Special operators for imperfect reasoning.   
  * For example this is a fuzzy constraint /rooms\[temperature is HOT\].  
  * If the above syntax can be made flexible and non ambiguous it may need  
    something function oriented, like /rooms\[fuzzy.is(temperature, HOT), as a backup.  
* Multi-valued logic. For example handle unset correct \- so a field can be set, empty (null) and unset. Another is that an object (or field) can be known, unknown and unknowable.  
* Re-look at the syntax that was used for imperfect reasoning that used degrees of truth, and see if that can be incorporated into the language.  
* JDK switch can return, can our match ‘bind’.  
* Needs and opportunistic backward chaining.

# Ideas to explore {#ideas-to-explore}

* If ‘/’ is type of stream processing for comprehension, what about adding support for Streams or Stream like api. Can this be optionally incremental?  
* As rules and queries are now unified, potentially the ‘rule’ keyword could be removed, when in a separate DRL file.

# Unknown or unresolved or Unhappy with {#unknown-or-unresolved-or-unhappy-with}

* Should there be scoping of variables for ‘if’ and ‘match’ blocks. (probably not)  
* ‘,’ can be omitted before hard keywords, or after any {}.  
* The last ‘,’ of ‘if’ or ‘match’ looks weird, but if we don’t use that, then {} would be needed instead \- as those elements must be terminated.  
  * Potentially solved if ‘,’ is optional after any {}.

# DRLXXXX for DRLOLD People {#drlxxxx-for-drlold-people}

This is a quick summary that shows how the core DRL concepts map to DRLXXXX. It is hoped that by showing the mapping is simple, clean and minimal that people will not be put off by all the additional flexibility and power that will now be available. For each concept mapped, it will not go deeper on the DRLXXXX side than exactly what is necessary for the DRL concept to be mapped. There will be a lot of duplication with the main content, but from a different perspective. The main document will cover all concepts from the DRLXXXX side, and will go deeper. Where code blocks show comparisons between old and new, it uses the format of \<DRLOLD\> vs \<DRLXXXX\>. A lot of the changes are to provide a more consistent and intuitive syntax, that provides more power and flexibility without the grammar ambiguities that plagued DRL7. 

## Patterns and Entry Points {#patterns-and-entry-points}

To allow partitioning of data, Drools  used the concept of  partitioned working memories which it separates and accesses via that partition ‘entry-point’, with a default entry point for the classical use case where no ‘entry-point’ is specified.

| l : Location(city \== ”paris”) // from default entry point p : Person( locationId \== l.id)  from entry-point “persons”                                   //   from named entry point |
| :---- |

In DRLXXXX there is no longer a default entry-point and all patterns must be from what is equivalent to a named entry-point. The existing typed pattern syntax is gone, and a more compact, XPath (OOPath) like syntax is now used, to provide a more succinct and readable way to work with multi entry-point DRL. This unifies around the OOPath syntax, which was  introduced in 7.x The term ‘entry point’ is no longer used and they are simply named DataStore instances, referenced via properties in a Pojo. The first pattern without any joins has similar succinctness and would compare as below, note the first pattern goes against the type and all bindings must be declared by type or the ‘var’ keyword, where as the second against the property that references the DataStore instance:

| l : Location(city \== ”paris”) vs Location l  : /locations\[city \== “paris”\] // typed var l  : /locations\[city \== “paris”\] // var version |
| :---- |

The second pattern with joins, that uses the “persons” entry point, is however far more succinct and readable.

| p : Person( locationId \== l.id)  from entry-point “persons” vs var  p : /persons\[locationId \== l.id\] |
| :---- |

All statements in DRLXXXX must now be comma separated, to avoid some of the grammar ambiguities of DRL7. Putting this together, the example now looks like:

| var  l : /locations\[city \== “paris”\],  var  p : /persons\[locationId \== l.id\] |
| :---- |

## Property Reactive {#property-reactive}

Property reactive is also now succinct and makes for more readable rules. Where as before it was a separate annotation, after the pattern, it is now just a repeated ‘\[\]’ square brackets. Here the example constrains yearsOfService and also reacts to changes on that property, but it also needs to react to basePay and bonusPay, regardless of their values.

| p : Employee(yearsOfService \> 5\) @watch(basePay, bonusPay) vs var  e : /employees\[yearsOfService \> 5\]\[basePay, bonusPay\] |
| :---- |

## Group Conditional Elements {#group-conditional-elements}

DRLXXXX has adopted list based group conditional elements (CE), rather than the infix notation of 7.x. This has no specific advantage when considered in isolation, but provides greater consistency and intuitiveness when considered as whole within DRLXXXX.

| ( a : Person(hair==”purple”) or    B : Person(hair==”indigo”) or    c : Person(hair==”indigo”) ) vs or(var  a : /persons\[hair==”purple”\],     var  b : /persons\[hair==”indigo”\],     var  c : /persons\[hair==”indigo”\] ) |
| :---- |

Group CE’s with only one element look the same in 7.x as DRLXXXX, as the () is not needed for single element lists. So it looks very similar, except for the path syntax.

| not Person() vs not \\persons |
| :---- |

## Rules and Consequences {#rules-and-consequences}

The overall rule structure is now technical, using {} instead of ‘begin’ and ‘end’. All rule names must follow valid java class naming conventions \- no spaces and upper first character. Annotations can be applied to rules to provide more metadata, for things like @Description. The ‘then’, for the consequence executed by the agenda, is now a ‘do’. Putting it all together, you can see a full example:

| rule EmailPeopleInParis {     var  l : /locations\[city \== ”paris”\],      var  p : /persons\[locationId \== l.id\],     {Email email \= new Email();      email.to(p.emailAddress).body(“my message”).send();} } |
| :---- |

A more readable version could be:

| rule EmailPeopleInParis when {     var  l : /locations\[city \== ”paris”\],      var  p : /persons\[locationId \== l.id\] } then {     Email email \= new Email();     email.to(p.emailAddress).body(“my message”).send(); } |
| :---- |

The reason to maintains the when / then keyword is  twofold:

* Improve readability for newcomers but even for seasoned developers, it makes more immediate the separation between LHS and RHS  
* Clarify the distinction between the declaratives clauses and the imperative ones

If the ‘do’ is a single statement the ‘{}’ can be omitted, such as if we used a fluent builder of a static method.

| rule EmailPeopleInParis {     var  l : /locations\[city \== ”paris”\],      var  p : /persons\[locationId \== i.id=\],     Email.to(p.emailAddress).body(“my message”).send() } |
| :---- |

Named consequences are no longer supported, but you can have multiple action statements at various points in the tree.

| rule EmailPeople {     var  l : /locations,      if (l.location \== “paris”) {         var  p : /persons\[locationId \== i.id=\],         Email.to(p.emailAddress).body(“paris message”).send()     } else if (l.location \== “london”) {         var  p : /persons\[locationId \== i.id=\],         Email.to(p.emailAddress).body(“paris message”).send()     }  } |
| :---- |

## Positional and Object Named syntax (POSL). {#positional-and-object-named-syntax-(posl).}

In 7.x the ‘;’ semi colon was used within the main pattern to differentiate between positional and named syntax. These are now two separate, but sequential, constructs using the  ‘\[\]’ and ‘()’to differentiate between positional and slotted syntax.

| l : Location(district \== "Belleville"; "paris") vs var l : /locations("paris")\[district \== "Belleville"\], |
| :---- |

## ‘from’ {#‘from’}

As previously shown, the OOPath syntax with DataStores bound to properties replaced the syntax ‘from entry-point’, this can be further unified to also replace ‘from’ expressions and iterations. Here the first segment can take any variable or expression. 

| i : Integer() from new int\[\] {1, 2, 3} vs var i : /new int\[\] {1, 2, 3} |
| :---- |

## 

## Query {#query}

Queries is a much more complex topic, and DRLXXXX goes much further, but a simple example comparing the two approaches for a transitive closure will give a feel for the main changes, when porting DRL. 

The POJO Trust encodes the transitive relationship between two Thing objects.

| // a trusts b, trust chains are transitive public class Trust {    private Thing a;    private Thing b;    // getters and setters } |
| :---- |

The query and rule show how the two were used together in DRLOLD. Notice in the query how it mixes accessing the Trust class and the trusts query.

| query trusts(Thing a, Thing b)    Trust(;a, b) or   (Trust(;a, z) and trusts(;z, b)) end rule r1 when    a : Thing(name \== “a”)    trusts(;a, b) // transitively finds all the things a trusts then      ... end |
| :---- |

In DRLXXXX there is now a single ‘rule’ keyword, for rules and queries. A query is a rule with parameters. As before, rules follow the java class naming convention, but now there is an additional step to implicitly map the name rule to a named data source. Using the same conventions as before, the rule Trusts maps to the trusts data source. This unifies how queries are accessed, and ensures a consistent looking example for transitive closures.

| rule Trusts(Thing a, Thing b) {   or(/trusts(a, b),     (/trusts(a, z), /trusts(z, b)) } rule R1 {    a : /things\[name \== “a”\],    /trusts(a, b), // transitively finds all the things a trusts    do ... } |
| :---- |

While the query name is declared via caps, querying it must be via the unit’s DataSource field. This keeps things very regular.  As mentioned a query is just a rule with parameters now. 

| // top level and shown explicit rule Trusts(Object a, Object b) {   or (/trusts(a, b),       and (/trusts(var z, b), // indicates z is an out var            /trusts(x, z)       )   ) } rule R1 {    var a : /objectAs,    /trusts(a, var b) // transitively finds all the things A trusts    do {...} } |
| :---- |

## Rule Attributes (Annotations) {#rule-attributes-(annotations)}

Rule attributes like \`salience\` are defined as annotations on \`rule\`. (Need to be imported)

\`\`\`  
    @Salience(10)  
    rule CheckAge1 {  
        Person p : /persons\[ age \> 18 \],  
        do { System.out.println(p); }  
    }  
\`\`\`  
Supported attributes are to be determined.

# Java++ language enhancements {#java++-language-enhancements}

Series of language enhancements that layer on top of the java language, to be used in expressions and blocks, to make rule authoring more succinct and readable. We will take a much more cautious approach with java++ extensions, especially in the beginning, and  not all of this may be implemented in the end, for example the map/list syntax may be a step too far.

## Property accessors {#property-accessors}

| t.status \= RECEIVED; t.timestamp \= new Date(); |
| :---- |

## ‘with’ style blocks {#‘with’-style-blocks}

{} is used for a series of  setters in a block. The {} block returns the subject, allowing it to be easily used with ‘update’ or other similar methods.

| t{status \= RECEIVED,   timestamp \= new Date()}; |
| :---- |

\[\] is used for a series of boolean tests in a block. The \[\] block returns the overall boolean value.

| t\[status \== RECEIVED,   timestamp \= new Date()\]; |
| :---- |

## List access and Map access {#list-access-and-map-access}

There is no difference in the accessor syntax for maps or lists, the execution is based on the target type,  maps are just associated arrays after all :)  
Literal number.

| cheeseList\[0\]; |
| :---- |

Variable number.

| var x \= 10; cheeseList\[x\]; |
| :---- |

Map takes any object as an argument, here it is a String.

| cheeseMap\[“silton”\]; |
| :---- |

Here the argument is a specific stilton cheese.

| cheeseMap\[new Cheese(“Stilton”)\]; |
| :---- |

Here the argument is a specific Stilton cheese.

| var x \= new Cheese(“Stilton”); cheeseMap\[x\]; |
| :---- |

### Inline cast and coercion  {#inline-cast-and-coercion }

| object\#Car.manufacturer \= “Honda”; |
| :---- |

Coercion combined with units.

| var x \= 10\#litres \* 5pints; |
| :---- |

Date example

| p.dateOfBirth \= “01-01-2005”\#StdDate; |
| :---- |

# Rule Literal Models {#rule-literal-models}

For every rule there will be a generated class that provides the reflective metadata about it. This is particularly necessary for type safe query results, where each variable is a property. This code generation potentially makes itself useful for typed queries being invoked from Java too.

# Units of Execution and Data Sources {#units-of-execution-and-data-sources}

Units and Data sources will be covered in a lot more detail in a separate document. A short and simplified summary is detailed here, to provide some wider context to the rest of the DRL documentation.

For purposes of Drools the rule unit is a Pojo that is associated with 1..n rules. The class may be annotated with additional metadata that controls the behaviour of execution, such as @ExistanceDriven. Each field of the unit provides the data for the rules to match and react against. That data may:

* Instance of a class.  
* DataStore, DataStream (DataSource).  
* JRE Collection.

An example of a rule unit, with annotation to control rule behaviour and one DataStore.

| @ExistenceDriven public class MyRuleUnit {    private DataStore\<Person\> persons;    ..getters/setters.. }  |
| :---- |

## 

# Simple Reactive rule and bindings {#simple-reactive-rule-and-bindings}

Simple rule, with binding. Only the first segment can be bound this way. Note top level ‘and’ is implicit. The last element in a sequence does not need ‘,’.

| rule R1 {    var p : /persons,    {        ...    } }  |
| :---- |

Equivalent first segment binding on ‘this’. This can be used to bind ‘this’ or fields on any segment. Field bindings are generally discouraged, as most of the time they are not best practice. 

| rule R1{    /persons\[ var p : this\],    /address\[ var s : street\],   {...} }  |
| :---- |

‘this’ is implicit in the field access of locationId.

| rule R1 {    var l : /locations\[city \== "paris"\],    var p : /persons\[locationId \== l.id\],    {...}  } |
| :---- |

Explicit ‘p.’ is allowed and will end up exactly the same.

| rule R1 {    var l : /locations\[city \== "paris"\],    var p : /persons\[p.locationId \== l.id\],    do {...}  } |
| :---- |

# Passive elements {#passive-elements}

The ‘?’ symbol makes a rule passive, it will only propagate when prior data is pushed into it. The ‘?’ symbol can also be used on group conditional elements, like ‘and’.

| rule R1 {    var l : /locations\[city \== "paris"\],    var p : ?/persons\[p.locationId \== l.id\],    {...}  } |
| :---- |

# Pluggable Operators {#pluggable-operators}

Historically drools supported pluggable operators, which allow for CEP temporal constraints.

| rule R1 {    var a : /as,    var b : /bs\[this after a\],    do {...}  } |
| :---- |

It is also how other interesting things can be added, such as fuzzy operators. Here the \~ indicated it was an imperfect reasoning operator. Those operators also took arguments, to control their behavior \- older drools and some academic papers cover this.

| rule R1 {    var a : /rooms\[temperator \~is HOT\],    do {...}  } |
| :---- |

# Add/Remove/Update {#add/remove/update}

The data source the add/remove/updater operation is on must be explicit. Where the parser recognises the parameter is Handle, it will attempt to coerce the argument Object to its Handle.

Add example

| rule R1 {    var r : /request\[timestamp before now-1m\],     do alerts.add(new Alert(r)) } |
| :---- |

Remove example

| rule R1 {    var t : /tasks\[status \== COMPLETED\],     do alerts.remove(t) } |
| :---- |

Update example

| rule R1 {    var t : /tasks,     do { t.status \= RECEIVED; alerts.update(t);} } |
| :---- |

Compact syntax for update. Whereas old DRL has an explicit combined ‘modify’ block, I’m instead experimenting keeping it always ‘update’ but supporting a ‘with’ keyword for block updates, that returns itself \- so it can be used inside a method.  It’s not as compact as DRL7, but it is more regular and more orthogonal.

| rule R1 {    var t : /tasks,     do alerts.update(t{status \= RECEIVED,                      timestamp \= new Date()}); |
| :---- |

# Literal expressions are cached {#literal-expressions-are-cached}

This will only instance a single object, not for each propagation 

| rule R1 {    var p : /persons\[address \== new Address(“36 MyRoad”, “A City”)\] |
| :---- |

# Casting and coercion with rule elements {#casting-and-coercion-with-rule-elements}

Example of inline cast

| /objects\#Car\[speed \> 80\] |
| :---- |

This is also allowed, but not as efficient as above

| /objects\[this\#Car.manufacturer \== “Honda”\] |
| :---- |

And this is a normal cast, which is also inline inside of of \[\]

| /objects\[((Car)this).manufacturer \== “Honda”\] |
| :---- |

Inline cast with unit coercion. Units would require appropriate library support for the conversion.

| /objects\#Car\[petrol \> 5\#Litres\] |
| :---- |

Here the coercion is matched to a specific date formatter.

| /Person\[dateOfBirth \== “01-01-2005”\#StdDate\] |
| :---- |

# Positional and Object Named Language (PONL) aka RuleML POSL {#positional-and-object-named-language-(ponl)-aka-ruleml-posl}

\[\] and () is used to differentiate between positional and slotted syntax.

| rule R1 {    var l : /locations("paris"),    var p : /persons\[locationId \== l.id\],    do {...}  } |
| :---- |

‘var’ is used to indicate it’s an ‘out’ parameter.  Before we inferred if a var was an in or out binding \- i.e. used before or not. However for now, I think it’s best to keep it consistent elsewhere. But what if they want to have a “typed” out var? Maybe we should not allow that

| rule R1 {    var l : /locations("paris", var postCode), // postCode is the                                               // ‘out’ binding    var p : /persons\[locationId \== l.id\],    do {...}  } |
| :---- |

They may be mixed, () must come first.

| rule R1 {          var l : /locations("paris")\[district \== "Belleville"\],    var p : /persons\[locationId \== l.id\],    do {...}  } |
| :---- |

# Property Specific {#property-specific}

Property specific as an optional second set of \[\] parenthesis, if there are no constraints, the first set must be empty. The contents of the second \[\] use the same notation and conventions of 7.x. Comma separated list of properties to listen to, or not to listen to. Wildcards are allowed, filters are applied in order, each potentially overriding the one prior.

Only respond to changes to the city property

| var l : /location\[city \== “Paris”\] |
| :---- |

Only respond to changes to the city property

| var l : /location\[\]\[city\] |
| :---- |

Do not react to changes to the city property.

| var l : /location\[city \== “Paris”\]\[\!city\] |
| :---- |

Listen to all field changes, even though they are not constraints.

| var l : /location\[\]\[\*\] |
| :---- |

Listen to all field changes, except the city. 

| var l : /location\[\]\[\*, \!city\] |
| :---- |

When positional syntax is used, there will still need to be two \[\]\[\], even if the prior is empty.

| var l : /location(“Paris”)\[\]\[\*, \!city\] |
| :---- |

## 

## 

## 

## 

# ‘and’ / ‘or’ structures {#‘and’-/-‘or’-structures}

Top level ‘and’ is implicit, last element in the sequence does not need ‘,’

| rule R1{    var l : /locations,    var p : /persons\[locationId \= l.id\],    {...}  }  |
| :---- |

Top level ‘and’ shown explicit

| rule R1{    and(var l : /locations,        var p : /persons\[locationId \= l.id\]    ),   {...}  } |
| :---- |

Nested ‘or’, top level ‘and’ is implicit. 

| rule R1 {    or(and (var l : /locations\[city \== "paris"\],            var p : /persons\[locationId \= l.id\]       ),       and (var l : /locations\[city \== "london"\],            var p : /persons\[locationId \= l.id\]       )    ),   {...}  } |
| :---- |

**Binding scope in group CEs**

Bindings introduced inside `or(...)` are visible only within their own branch. DRLX does not unify same-named bindings across OR branches; use `and(...)` to make a binding visible across an OR. Bindings inside `not(...)` and `exists(...)` are visible only within the negated group.

Passive ‘?’ declared. This will not propagate if more locations or person matches are made, unless there is an update on the request data.

| rule R1{    r : /requests    ?and(var l : /locations\[region \== r.region\],         var p : /persons\[locationId \= l.id\]    ),    {...}  } |
| :---- |

# ‘not’ / ‘exists’ {#‘not’-/-‘exists’}

‘not’ and ‘exists’ follow the same rules as ‘and’ ‘or’, except they also allow  for parenthesis to be omitted if there is a single child element.

| rule R1 {    not /persons\[ age \< 18\]    {...}  } |
| :---- |

# Implicit type with ‘var’ vs explicit type {#implicit-type-with-‘var’-vs-explicit-type}

| rule R1 {    var l : /locations, // implicit    Person p :         /persons\[locationId \= l.id, var a : age\], //explicit    do {...} }  |
| :---- |

# Iterate versus assignment bindings with expressions {#iterate-versus-assignment-bindings-with-expressions}

‘:’ paired with ‘/’ means iterate and assign, i.e. ‘foreach’. ‘=’ means assign.

The main difference between this single line statement and a single line “do” is that firstly this executes immediately as part of the propagation and secondly the variable is introduced into the Tuple chain and available in expressions elsewhere.

| rule R1 {    var p : /persons,       int age \= p.age \* 25, // yes expressions are allowed here    {...} }  |
| :---- |

## 

# ‘from’ versus ‘/’ {#‘from’-versus-‘/’}

Expressions after the ‘/’, which are not followed by any child segments, are semantically equivalent to the old ‘from’. For this reason ‘from’ as a keyword is not used. However I believe we should introduce ‘from’ as terminology used for what the first ‘/’ segment is \- this could be reflected in the java DSL (instead of the pattern keyword).

| rule R1 {        var i : /new int\[\] {1, 2, 3},    {...} }  |
| :---- |

Assignment works too, and would not iterate.

| rule R1 {    var i \= new int\[\] {1, 2, 3},    do {...} }  |
| :---- |

# ‘do’ :  Immediate vs Agenda Executions  {#‘do’-:-immediate-vs-agenda-executions}

Any statement that starts with ‘do’ is scheduled and executed on the agenda, all other statements are executed immediately as part of the network propagation.

Normal ‘do’, with a single line statement.

| rule R1 {    var a : /as,    { System.out.println(a);}  } |
| :---- |

The {} can be omitted if there is a single statement.

| rule R1 {    var a : /as,    System.out.println(a)  } |
| :---- |

‘do’ can appear anywhere a conditional element is allowed.

| rule R1 {    var a : /as,    { System.out.println(a);},     var b : /bs,    { System.out.println(a \+ b);},     var c : /cs,    { System.out.println(a \+ b \+ c);}  } |
| :---- |

Same as above but {} omitted.

| rule R1 {    var a : /as,    System.out.println(a),     b : /bs,    do System.out.println(a \+ b),     c : /cs,    do System.out.println(a \+ b \+ c)  } |
| :---- |

If the ‘do’ is omitted, those statements are executed during propagation.

| rule R1 {    var a : /as,    System.out.println(a),     var b : /bs,    System.out.println(a \+ b),     var c : /cs,    System.out.println(a \+ b \+ c)  } |
| :---- |

{} can be used for immediate multi statement blocks, each of these is executed immediately with the propagation.

| rule R1 {    var a : /as,    { System.out.println(a); sendMessage(a);},     var b : /bs,    { System.out.println(a \+ b); sendMessage(a, b);},     var c : /cs,    { System.out.println(a \+ b \+ c); sendMessage(a, b, c);},  } |
| :---- |

First two are immediate, the last is executed by the agenda.

| rule R1 {    var a : /as,    System.out.println(a),    var b : /bs,    System.out.println(a \+ b),     var c : /cs,    do System.out.println(a \+ b \+ c)  } |
| :---- |

The first is executed by the agenda, the later two

| rule R1 {    var a : /as,    do System.out.println(a),     var b : /bs,    System.out.println(a \+ b),     var c : /cs,    System.out.println(a \+ b \+ c)  } |
| :---- |

## 

# Rising/Falling the three edges of firings {#rising/falling-the-three-edges-of-firings}

The expression that assigns age is executed on both the add and update propagation edges.

| rule R1 {    var p : /persons,       int age \= p.age \* 25 // yes expressions are allowed here  |
| :---- |

When this is first propagated it will create the age variable with the value derived from the expression. The edge keyword controls on which propagation the edge the statement executes on. It has three edges ‘onAdd’, ‘onUpdate’, ‘onRemove, any of which can be ommitted’. If only a single edge is used with a single statement then the () is optional.. In this example, with only ‘onAdd’ A further update will skip this node and age will remain at the value derived during add. 

|  rule R1 {    var p : /persons,     edge onAdd int age \= p.age \* 25 |
| :---- |

If more than one edge is used, it must use () with comma separation. The builder can detect this and optimise. This example has ‘onAdd’ and ‘onUpdate’.

| rule R1 {    var p : /persons,     edge(onAdd int counter \= 0,         onUpdate counter \= counter \+ 1\) |
| :---- |

Here is an example that shows all three edges, with the explicit edge named.

| rule R1 {    var p : /persons,     edge(onAdd int counter \= 0,         onUpdate counter \= counter \+ 1,         onRemove             if (counter \> 100\) {                 System.out.println(“over 100 updates for” \+ p);             }) |
| :---- |

Here is an example with onAdd omitted.

| rule R1 {    var p : /persons,     edge(onUpdate log(update \+ p),         onRemove log(‘remove’ \+ p)) |
| :---- |

## 

# ‘drain’ {#‘drain’}

The ‘drain’ keyword is used with a ‘from’ segment. It will react and respond to incoming elements from the right input. However it is drained from the node memory and further left inputs will not join with it.

In this example persons propagate as normal, however there is no right memory for alarms. So the p is held in the left memory until a right input attempts to join with it.  There is no right memory, so once join attempts are done that right input object will not attempt any further joinis, nor can it be joined with further left inputs.

| rule R1 {    var p : /persons,    drain var a : /alarms,    do System.out.println(p \+ “:” \+ a)  } |
| :---- |

## 

# ‘test’ {#‘test’}

‘test’ is equivalent to the old ‘eval’, and returns true or false. 

| rule R1 {    var p : /persons,    test p.age \> 30,     do System.out.println(a)  } |
| :---- |

# ‘if’ {#‘if’}

Each branch is enclosed in a ‘{ … ‘}’, there is an implicit ‘and’ in between. For this iteration the ‘,’ at the end of the ‘if’ block is needed \- not for ambiguity reasons, but for consistency (however this may be revisited).

| rule R1 {    var c : /customer,    if (c.creditRating \== Rating.LOW){       var p : /products\[rate \== Rates.HIGH\]    } else {       var p : /products\[rate \== Rates.LOW\] // this is needed    },    do System.out.println(c \+ “ ” \+ p); } |
| :---- |

‘else if’ is also supported.

| rule R1 {    var c : /customer,    if (c.creditRating \== Rating.LOW) {        var p : /products\[rate \== Rates.HIGH\]    } else if (c.creditRating \== Rating.MEDIUM) {       var p : /products\[rate \== Rates.MEDIUM\]    } else {       var p : /products\[rate \== Rates.LOW\], // this is needed    },    do System.out.println(c \+ “ ” \+ p) } |
| :---- |

If multiple elements are needed, they must be grouped by one of ‘and’/‘or’/’not’/’exists’.

| rule R1 {    var c : /customer,    if (c.creditRating \== Rating.LOW) {       var r : /requests,       var p : /products\[rate \== Rates.HIGH, type \= r.productType\]          } else {       var r : /requests,       var p : /products\[rate \== Rates.LOW, type \= r.productType\]          },    do System.out.println(c \+ “ ” \+ p) } |
| :---- |

Alternative ‘if’ syntax proposals. that hat avoid’s the {} but it still needs the final terminating ‘,’. 

| rule R1 {    var c : /customer,    if (c.creditRating \== Rating.LOW)       var p : /products\[rate \== Rates.HIGH\]    else       var p : /products\[rate \== Rates.LOW\] // this is needed    ,    do System.out.println(c \+ “ ” \+ p); } |
| :---- |

| rule R1 {    var c : /customer,    if (c.creditRating \== Rating.LOW) and (       var r : /requests,       var p : /products\[rate \== Rates.HIGH, type \= r.productType\]          ) else (       var r : /requests,       var p : /products\[rate \== Rates.LOW, type \= r.productType\]          ),    do System.out.println(c \+ “ ” \+ p) } |
| :---- |

## 

# ‘match’ (switch) {#‘match’-(switch)}

Minimal version, ‘case’ is a keyword so the ‘,’ can be omitted, except for the last line if the match is not the end of a sequence.

| rule R1 {    var c : /customer,    match (c.creditRating)        case Rating.LOW do System.out.println(c)       case Rating.MEDIUM do System.out.println(c)       case Rating.HIGH do System.out.println(c),    do ... } |
| :---- |

Grouping can be used for more than one element.

| rule R1 {    var c : /customer,    match (c.creditRating)        case Rating.LOW {          var p : /products\[rating \== c.rating\],          do System.out.println(“Low” \+ c \+ “ ” \+ p)       } case Rating.Medium {          var p : /products\[rating \== c.rating\],          do System.out.println(“Medium” \+ c \+ “ ” \+ p)       } case Rating.HIGH {          var p : /products\[rating \== c.rating\],          do System.out.println(“High” \+ c \+ “ ” \+ p)       } } |
| :---- |

Matching can be done on types too. Same notation as ‘/’ without the ‘/’. Recall that \# is inline cast or coerce.

| rule R1 {    var o : /objects,    match (o)        case \#Train do System.out.println(o)       case \#Plane do System.out.println(o)       case \#Automobile\[value \> 30\] do System.out.println(o) } |
| :---- |

## 

Support for “default” and also “else” can be added

| rule R1 {    var o : /objects,    match (o)        case \#Train do System.out.println(o)       case \#Plane do System.out.println(o)       case \#Automobile\[value \> 30\] do System.out.println(o)       default do System.out.println(“do this as well”) } |
| :---- |

# Actors and async messaging {#actors-and-async-messaging}

If the expression in the element position returns a Future, or equivalent recognised async handler, support is added to evaluate the function and then continue propagation once data is returned.

The following sends an async message, that once it returns triggers a response handler. The ‘r’ var is the Future, or equivalent reactive handler, that can be iterated as though it was a passive ‘from’.

| rule R1 {    var r \= sndMessage(“help”);    var v : /r } |
| :---- |

| rule R1 {    var r \= rcvMessage(“help”);    var v : /r } |
| :---- |

Or better still

| rule R1 {    var v : /rcvMessage(“help”); } |
| :---- |

The async statement can use any try/retry/fail fluent, or policy.

# DataDriven and Existence Driven {#datadriven-and-existence-driven}

By default all rules are data drive, and respond to all changes in data. Rules may also be declared as existence driven. Existence driven rules will match once per evaluation request, further changes will not match during the scope of that evaluation request. Once the engine is at rest, if there is another evaluation request any data that is still true will have an opportunity to match and fire again. 

Each time a evaluation request is made, any matches that exist in this rule will be refreshed and fired again.

| @ExistanceDriven rule StartRule {    var a /alarms,    do System.outprintln(a) } |
| :---- |

This may be annotated on the rule itself or on the rule unit.

| @ExistanceDriven Public class MyRuleUnit {    private DataStore\<Person\> persons; } |
| :---- |

# ‘refresh’ {#‘refresh’}

Once a tuple chain has fired, calling ‘refresh’ on it will submit it to the agenda for firing again, even if the underlying data has not changed

| TOOD |
| :---- |

# Accumulate {#accumulate}

### Simple and single function accumulates {#simple-and-single-function-accumulates}

Functions can be imported that will apply to the existing tuple chain

| rule R1 {    var p : /persons\[location \== “london”\],    avgAge \= avg(p.age),    do {...}  } |
| :---- |

It is perfectly reasonable to do the following, and the Rete network could do this already. Although analysis may decide to “push up” the child nodes for network optimizations reasons. Parent classes, that contain the functions, can be imported and then the functions used within that class.

| rule R1 {    var p : /persons\[location \== “london”\],    avgAge \= Func.avg(p.age),    minAge \= Func.min(p.age),    maxAge \= Func.max(p.age),    {...}  } |
| :---- |

As well as taking standard expressions, it can take a ‘from’ element, however for this to work the path must end in a final ‘.’ notation to make it clear it’s accessing and returning a single value.

| rule R1 {    // ‘.’ means access, not iterate    int avgAge \= avg(/persons.age),     do {...}  } |
| :---- |

‘and’ can still be used, notice ‘.’ is still last.

| rule R1 {    var avgAge \= avg( and(          l : /locations,             /persons\[locationId \= l.id\].age    ) ),    do {...}  } |
| :---- |

### ‘acc’ keyword 2 parameters {#‘acc’-keyword-2-parameters}

Simplest 2 parameter acc, is semantically similar to previous examples, with the direct use of the function without the ‘acc’

| rule R1 {    acc( p : /persons,         var avgAge \= avg(p.age)              ),    do {...}  } |
| :---- |

2 parameter acc when used with multiple functions must use ‘( ….).’, which is an implicit ‘and’ group. There are arguments to allow for optional (or not optional) ‘and’.

| rule R1 {    acc( p : /persons,        (var maxAge \= max(p.age),                   var minAge \= min(p.age))                        ),    do {...}  } |
| :---- |

Functions can take 1..n arguments. It’s back to a single statement so the ‘and(...)’ can be omitted.

| rule R1 {    acc( and ( p1 : /persons\[location \== “london”\],               p2 : /persons\[location \== “paris”\],         var minAge \= max(p1.age, p2.age)                 ),    do {...}  } |
| :---- |

### ‘acc’ keyword 3 parameters {#‘acc’-keyword-3-parameters}

3 parameter version supports an init and action. Here the init is redundant, and is again semantically equivalent to prior examples, but it’s added to illustrate the point. There is no reverse, so it will recalculate everything each change.

| rule R1 {    acc( p : /persons,         int s, // init vars are local         s \= s \+ p.age,         int sum \= s, // result vars are visible to later joins              ),    do { … }  } |
| :---- |

A 2 argument ‘(....)’ block is used, to specify a reverse and action

| rule R1 {    acc( p : /persons,         int s, // init vars are local        ( s \= s \+ p.age,           S \= s \- p.age )         int sum \= s, // result vars are visible to later joins                     ),    do { … }  } |
| :---- |

3 parameter version with multiple functions. Again the init is redundant, added for illustration.

| rule R1 {    acc( p : /persons,         {int minAge; int maxAge;},         {minAge \= min(minAge, p.age);           maxAge \= max(maxAge, p.age);}             ),    do {...}  } |
| :---- |

If the acc wants a more complex select, to keep things at 3 parameters, it must use an explicit ‘and’.

|  rule r1 {    acc( and( p : /persons,              t : /cashflows\[personId \= p.id\])         ),         int avgTx,         avgTx \= avg(t.amount)              ),    do {...}  } |
| :---- |

This is now more complex and shows how a Holder object can be used. The single statement init and action, do not require a {}. They can still support multiple statements, but this would require {} with ; separators.

| rule R1 {    acc( and( p : /persons,              t : /cashflows\[personId \= p.id\] ),         var h \= new ResultsHolder(),         h{minAge \= min(p.age),           maxAge \= max(p.age)}                )    do {...} } |
| :---- |

And inline logic, no external functions works too, this simulates avg. Notice this has 5 positions now, for the reverse and result. If 4 positions are used, the reverse is omitted and it’s recalcated when the input is updated or removed. If there is more than one statement in a position, it must have {} around it. The last line has a single statement, so the {} can be omitted.

| rule R1 {    acc( and( p : /persons,              t : /cashflows\[personId \= p.id\] ),         { var count \= 0; var total \= 0;} ,         { total \+= p.age; count++},         { total \-= p.age; count--},         total / count    )    do {...} } |
| :---- |

# Group By {#group-by}

Group By works exactly as per acc, but with an extra parameter for the group definition. This includes the behaviour for different numbers of parameters \- except groupby always has one more, for the equivalent acc. Note this example uses a “compact” syntax for setting multiple fields on an instance.

| // top level and shown explicit rule r1 {    groupBy( and (p : /persons,                  cf : /cashflows\[personId \= p.id\]),             p.status, // the group             var r \= new ResultsHolder(),             r{ageTx \= avg(cf.amount);               maxTx \= max(cf.amount);}              )    do {...}  } |
| :---- |

It is optional to bind the group declaration, and it can be used like any other var at this point, including adding to the Holder class \- ideal if things need to be returned outside of subnetwork.

|             var g \= p.status, // the \= returns itself             var r \= new ResultsHolder(g), |
| :---- |

# Queries {#queries}

Positional syntax is used in this example.

| // A trusts B, trust chains are transitive public class Trust {    private Object a;    private Object b;    // getters and setters } |
| :---- |

A query is just a rule that has parameters. The query is a form of DataSource. The annotation associates it with the rule declared in the DRL file. Any rule, with or without parameters, can be declared a datasource. Rules without arguments cannot use unification for result passing, instead relying on the bound row of data.

| // Location thing X is Y public class MyUnit {    @Rule("Trusts") // I wonder if this could use a typed literal?    Private DataSource\<Trust\> trusts; } |
| :---- |

While the query name is declared via caps, querying it must be via the unit’s DataSource field. This keeps things very regular.  As mentioned a query is just a rule with parameters now. 

| // top level and shown explicit rule Trusts(Object a, Object b) {   or (/trusts(a, b),       and (/trusts(var a, z), // indicates z is an out var            /trusts(z, b)       )   ) } rule R1 {    var a : /things\[name==”a”\],    /trusts(a, var b) // transitively finds all the things A trusts    do {...} } |
| :---- |

Named access is allowed too, keep it regular with all ‘from’ syntax, be careful with names though to avoid clashing. Only the rule parameters are visible within the constraint block.

| rule R1 {    var subject : /things\[name==”a”\],    /trusts\[a \== subject, var object : b\]    do {...} } |
| :---- |

While the arguments follow Prolog style unification, it is also possible to bind the query itself. In such a case binding has access to all the outer variables, not just the parameters, and tuples of each query row. Every rule has a generated POJO class that has properties mapped to each variable name. That POJO also has reference to the Tuple chain. The integer position has access to each tuple chain that makes up the row, even if those tuple elements are not bound to a variable name. This example shows that that ‘t.a’ returns the same value as t.objects\[0\]. Note in both cases it coerces to the Object, and not the handle. The Handle can be accessed by the special ‘handles’ property, t.handles\[0\]. Potentially a second pojo could be generated to provide type safe accesso the Handles t.handles().a. Here ‘t’ is also iterable, but supports handles() and objects() methods, both of which return a List of the associated elements.

| rule R1 {    var a : /things\[name==”a”\],    var t : /trusts(a, var b),    allEqual(t.a, t\[0\]) } |
| :---- |

The example so far binds the query to a DataSource field in the Unit. If no binding exists, then it is implicitly made, using POJO naming conventions and only visible to DRL. It is possible to override the name of the implicit DataSource binding. Without the annotation it would implicitly be bound to “trusts”, instead how it’s bound to “trustworthy”.

| @DataSource(“trustworthy”) rule Trusts(Object a, Object b) {  |
| :---- |

As query syntax is unified with ‘from’ paths, it can also be passive of an “open” reactive query. Without the ‘?’ it is reactive. This is a passive example

| rule R1 {    var a : /things\[name==”a”\],    ?/trusts(a, var b)   do {...} } |
| :---- |

‘do’ elements can be used in queries. However an exception will be thrown if a passive query is called and an agenda ‘do’ is found. By default the leaf ‘do’ is executed on the agenda, so this query cannot be called passively.

| // top level and shown explicit rule Trusts(Object a, Object b) {   or (/trusts(a, b),       and (/trusts(var z, b), // indicates z is an out var            /trusts(x, z)       )   ),   do System.out.println(a \+ “ trusts ” \+ b) } |
| :---- |

This change would allow the query to be called passively:

| System.out.println(a \+ “ trusts ” \+ b) |
| :---- |

## Out Vars {#out-vars}

Currently queries are untyped and a special Variable instance is used to declare an argument is unbound and thus an ourVar. Any given interface or class may declare, via annotation, a static field to be used as that comparator \- where this is done, type safe queries are allowed without vars.

## Results Binding {#results-binding}

By default a POJO is generated which each row is bound to and returned. It is possible for the user to specify the base class this generated class extends, and even interfaces it would implement.

| // top level and shown explicit rule R1(Object a, Object b) extends BaseClass                              implements MyInterface |
| :---- |

If there is a class on the classpath, that has the same name, this is used instead of generating one.

It is also possible to specify an alternative class and the user must instantiate and return this within the rule. If the rule has multiple branches and terminal leafs, it can use a different return statement per terminal node.

| // top level and shown explicit rule Holder R1(Object a, Object b) {     return new Holder(a, b) } |
| :---- |

The void keyword can be used when there is no result binding and also no generated class.

| // top level and shown explicit rule void R1(Object a, Object b) {     // } |
| :---- |

## Tabling {#tabling}

Any query rule annotated with @Tabled will have each request (goal) cached, this is cached recursive for every time it re-enters. Tabling avoids infinite recursion issues and also allows other queries to benefit from the pre-cached data. This is based on XBS tabling, [http://xsb.sourceforge.net/shadow\_site/manual1/node46.html](http://xsb.sourceforge.net/shadow_site/manual1/node46.html).

I believe this meets the ART opportunistic data-drive backward chaining implementation, and described here [http://haleyai.com/pdf/BackwardChaining.pdf](http://haleyai.com/pdf/BackwardChaining.pdf)

## Abduction TODO {#abduction-todo}

~~Each rule can optionally generate a POJO representation, where all it’s variables are properties.~~ 

 ~~Instantiations of that instance, per row, can be logical added to any datasource. Annotations can be used to declare if this pojo representation is to be generated and possibly provide additional configuration. Separately external classes could be used as configurators to code generators for these classes.~~

~~By default the rule name is the name used for the target class to hold each row. However it is possible to override this and target some other class. It’s not deceased yet if this should be a declarative aspect of the rule, or simply a ‘return’ statement for  each terminal leaf  node \- a “‘return” statement might be cleaner.~~  
Abduction can be simulated via queries  within the engine, and no longer needs first class constructs.

## Genericised Rules  / Hilog (IGNORE TODO) {#genericised-rules-/-hilog-(ignore-todo)}

Standard rules and queries are fixed to specific data sources. Genericized rules allow the data source to be passed as an argument. [https://www3.cs.stonybrook.edu/\~warren/xsbbook/node46.html](https://www3.cs.stonybrook.edu/~warren/xsbbook/node46.html)

Hilog/XSB seems to only allow one argument, I’ll allow multiple arguments for now. As this parameters only takes DataSources,  no type is needed, but it will allow generics to be specified \- so the DS matches.

By default any HiLog style query will only allow itself to be used via positional, and the type of the positions much match. Optionally @DuckTyped could be allowed, where key/value is allowed if at compile time the name and type of the properties match.  For this to work every query is a symbol ‘successor’, and every HiLog ‘twice(successor)’ query is also a symbol

From the XSB example  
successor(X,Y) :- Y is X+1.  
double(X,Y) :- Y is X+X.  
twice(F)(X,R) :- F(X,U), F(U,R).

| // top level and shown explicit rule Successor(int x, int y) { y \= x \+ 1 } rule Double(int x, int y) { y \= x \+ x } rule Twice(f)(int x, int r) {     /f(x, var u),     /f(u, r), } \---- successor(1,r) // r \== 2 \---- twice(successor)(1, r) // r \== 3 \---- twice(twice(successor))(1, r) // r \== 5 \---- twice(twice(double))(1, r) // r \== 16 \----- public class Pair {     @Position(0)private int x;     @Position(1)private int y;     ... getters/setters ... } // assumes there is  /pairs DS twice(pairs)  |
| :---- |

The previous Trusts rule is hard coded to the /trusts datasource. But in theory it should be able to work with classes that have matching positional and properties, java Generics may be used to tie this down.

| // top level and shown explicit rule closure(DataSource\<? extends Person\>ds)(Object a, Object b) {   or (/ds(a, b),       and (/ds(var z, b), // indicates z is an out var            /ds(x, z)       )   ) } rule R1 {    var a : /objectAs,    /trusts(a, var b) // transitively finds all the things A trusts    do {...} } |
| :---- |

# Needs and opportunistic backward chaining {#needs-and-opportunistic-backward-chaining}

Doesn’t exist in Drools yet.  
TODO

# Anonymous rules inside of ‘do’ {#anonymous-rules-inside-of-‘do’}

All outer vars of an anonymous rule are available as properties on the assigned rule instance. The rule instance is iterator and a Stream.

| // top level and shown explicit rule R1 {    var a : /objectA,    do {       var r \=  new Rule {          /trusts(a, var b) // find all the bs that a trusts       }       r.stream.forEach( i \-\> System.out.println(i.b));   ) } |
| :---- |

# Left, Right and Inner Joins {#left,-right-and-inner-joins}

Doesn’t exist in Drools yet.  
TODO

# Java Streams API {#java-streams-api}

Can any aspects of the Java streams api be integrated, in a way that makes sense? Similar to what OptaPlanner CS does.  
TODO.

# Windows {#windows}

The over keyword is now represented by the ‘|’ symbol.

|  var w : /withdrawals | length\[5\]  |
| :---- |

|  var w : /withdrawals | time\[5s\]  |
| :---- |

|  var w : /withdrawals | time\[4d6h5m6s\]  |
| :---- |

Windows over group elements

| and( var p : /persons      var w : /withdrawals\[accountId \== p.accountId\] ) | time\[5s\], |
| :---- |

**Applying windows before or after constraints.**    
Here the constraint is applied before the object reaches the window. So only gold customers will reach the window.

|  var w : /withdrawals\[customer \== GOLD\] | length\[5\]  |
| :---- |

Here all customers reach the window, that window is then reduced in size if no customers match.

| var w : /withdrawals | length\[5\], test w\[customer \== GOLD\] |
| :---- |

Window with simple average accumulation function.

| w : /withdrawals | time\[5s\], a \= avg(w) |
| :---- |

Window with accumulate keyword and  average function.

| acc( w : /withdrawals | time\[5s\],      a \= avg(w) ) |
| :---- |

Propagation delay

| /withdrawals | time\[5s\], delay.last(4s) |
| :---- |

Named windows (TODO) like queries have the same body syntax as rules, however they do not take parameters like queries. They are implicit

| window WithdrawalWindow {     /withdrawals | time(10s) } |
| :---- |

# Sequencing \[TODO, DO NOT READ YET\] {#sequencing-[todo,-do-not-read-yet]}

| Conditional Element | Description |
| :---- | :---- |
| A \-\> B | A followed by B |
| A \=\> B | A strictly followed by B |
| A \~\> B | A loosely followed by B |
| A \\\\ B | A independently followed by B |

### The "followed by" (-\>) conditional element {#the-"followed-by"-(->)-conditional-element}

The "followed by" conditional element (-\>) defines a temporal relationship between two event tuples where the left event tuple's end timestamp happens before the right event tuple's start timestamp. This semantic resembles the semantic of the "after" operator, but is stronger, works on tuples instead of individual events or attributes and defines additional constraints that the engine can use to infer new information and manage the relationship of the events. It is easy to understand if we consider the left event tuple to be composed of a single A event, and the right event tuple to be composed of a single B event. In this case:

| A \-\> B" implies that "A.endTime \<= B.startTime |
| :---- |

### The "strictly followed by" (=\>) conditional element {#the-"strictly-followed-by"-(=>)-conditional-element}

 The "strictly followed by" (=\>) operator determines that the event tuple on the left must be strictly followed by the event tuple on the right, i.e., without any other intervening tuples. The usability and feasibility of such conditional element needs to be investigated.

| A \=\> B" \*implies that\* "A.endTime \<= B.startTime" \*AND\* there is no C where "C.startTime \>= A.endTime \\&\\& C.startTime \<= B.startTime |
| :---- |

### The "loosely followed by" (\~\>) conditional element {#the-"loosely-followed-by"-(~>)-conditional-element}

   
The "loosely followed by" (\~\>) operator determines that the event tuple on the left must start before the event tuple on the right, but does not need to finish before it. The usability and feasibility of such conditional element needs to be investigated.  
 

| A \~\> B" \*implies\* that "A.startTime \<= B.startTime |
| :---- |

### The "independently followed by" (\\\\) conditional element {#the-"independently-followed-by"-(\\)-conditional-element}

   
The "independently followed by" (\\\\) operator determines that the event tuple on the left must be completed before the event tuple on the right, but no additional temporal constraints are enforced. This usually means that all the events in the event tuple on the left must happen before on the event stream than all but one of the events in the event tuple in the left. The usability and feasibility of such conditional element needs to be investigated.


| "A \\\\ B" \*does not imply\* any relationship between A and B other than event ordering in the stream. |
| :---- |

## Repetition {#repetition}

When matching events in streams, there are frequently patterns that will match multiple events. Sometimes, the users wants to ignore multiple event occurrences considering only the first or last event, sometimes the user wants to constrain the matching algorithm to a given number of repetitions, and sometimes the user wants to match and consider all such repetitions.   
   
To define pattern repetitions, the repetition operator is used in front of the first segment ‘from’ path. The general syntax for the repetition operator is:

| Syntax | Result |
| :---- | :---- |
| \* | Matches zero or more  |
| \+ | Matches one or more  |
| ? | Matches zero or one |
| *min*..*max* | Matches between *min* and *max* |
| ..*max* | Matches between zero and *max* |
| *min..* | Matches *min* or more |

For all the cases above where multiple repetitions are supported, the result will always be the "maximal match", i.e., it will match only the chain of events that consists of the maximal number of events, not a subchain consisting of some of those events. This is consistent with what is defined by Dr. David Luckham in his book "The Power of Events", but nevertheless requires users to be careful when defining repetition patterns in order to make sure matches will complete and not remain open forever, waiting for more events to match. This is particularly true when using repetition counters as the last pattern in a sequence with any operator other than "strictly followed by" (=\>).  
   
The default sequence conditional element that connects the repeated pattern is the followed by (-\>) conditional element, but it is possible to define a different conditional element. E.g., if the relationship between the repeated patterns is strictly followed by, it would be defined as:

| \[1..10|=\>\] \\a |
| :---- |

Please note that A can be an event or a tuple event. The supported sequence conditional elements are "-\>", "=\>", "\~\>" and "\\\\", as previously defined.   
   
E.g.: if the application should match an A event, followed by at least one B event followed by a C event, the rule would be written as:

| \\a \-\> \+\\b \-\> \\c |
| :---- |

### Variable bindings and the concept of multi-dimensional tuples {#variable-bindings-and-the-concept-of-multi-dimensional-tuples}

   
Tuples are essentially an ordered list of values. Rules and event definitions match events in a stream and produce tuples. E.g.:

| var v1 : \\a \-\>  var v2 : \\b \-\>  var v3 : \\c |
| :---- |

The rule X above will match tuples of 3 values, one each for A, B and C. A possible match for the previous rule could be the tuple:

| \[a, b, c\] |
| :---- |

In the previous tuple, one can address the "b" value in the tuple by its bound variable "$b".  
   
The approach above works well for situations without pattern repetitions, but how could one address individual elements on a repeated pattern? For instance, what is the value of "$b" in the example below?

| var v1 : \\a \-\>  var v2 : \+\\b \-\>  var v3 : \\c |
| :---- |

The answer is, v2 is an ordered list of values, i.e., a tuple by itself. The resulting tuple is, then, a tuple of tuples, or what can be called a "multi-dimensional tuple". In textual form, a tuple of tuples can be written with nested \[ \] to indicate nested tuples, or in graphical form, it can be represented with a tree structure. For instance, the following multi-dimensional tuple is a possible match for the previous rule:

| \[a, \[b0, b1, b2\], c\] |
| :---- |

In this case, v2 is a variable of the type Tuple and it is bound to \[b0, b1, b2\].

### Addressing individual elements in a multi-dimensional tuple {#addressing-individual-elements-in-a-multi-dimensional-tuple}

   
To address individual elements in a multi-dimensional tuple, a few concepts are defined. The first is the type Tuple. In the previous example, $b is a variable of the type Tuple.  
   
Individual elements of the type Tuple can be addressed by a numeric offset, like in an array, where the first element in a tuple has offset 0\. In the previous example, the following conditions are true:

| v2\[0\] \== b0 v2\[1\] \== b1 v2\[2\] \== b2 |
| :---- |

 The type Tuple also defines special properties:

| Property | Description |
| :---- | :---- |
| length | the number of elements in the tuple |
| first | the first element in the tuple |
| last | the last element in the tuple |
| asList | returns an ordered list of the elements in the tuple |
| iterator | an iterator for all elements in the tuple  |

For the previous example, the following conditions are true:

| v2.length \== 3 v2.first \== b0 v2.last \== b2 |
| :---- |

Multi-dimensional tuples can be several levels deep, depending on the definition of the patterns that created that tuple. For instance:  
   
{code}$t : ( first $ab : \[1:5\]( first $a : A() \-\> first $b : \[1:3\]B()) \-\> first $c : C() ){code}

| vt : (first vab : \[1:5\]          (first va : \\a \-\> first vb : \[1:3\]\\b) \-\> first vc : \\c ) |
| :---- |

The example above is definiting a pattern matching sequence of between 1 and 5 groups of one A followed by 1 to 3 B's. These groups are then followed by 1 C. All occurrences are qualified as "first". The whole tuple is bound to a variable $t of the type Tuple.  
   
A possible result tuple could be:

| \[ \[ \[ a0, \[b0\] \], \[ a1, \[ b1, b2 \] \], \[a2, \[ b3, b4 \] \] \], c0\] |
| :---- |

This example demonstrates that variables can be bound to nested patterns, effectivelly creating nested variables. Such variables can be addressed with the usual . navigation and can be understood as "aliases'' to the tuple indexes previously defined. The following expressions would hold true for the above example:

| vt\[0\] \== \[ \[ a0, \[b0\] \], \[ a1, \[ b1, b2 \] \], \[a2, \[ b3, b4 \] \] \] vt.ab \== \[ \[ a0, \[b0\] \], \[ a1, \[ b1, b2 \] \], \[a2, \[ b3, b4 \] \] \] vt\[1\] \== c0 vt.c \== c0   vt.vab\[0\] \== \[ a0, \[b0\] \] vt.vab.first \== \[ a0, \[b0\] \] vt.vab\[0\]\[0\] \== a0 vt.vab\[0\].$a \== a0 vt.vab\[0\].first \== a0 |
| :---- |

Complex cases like the previous one will rarely occur in real use cases, but nevertheless, it is quite common to have tuples with 1 or 2 nested levels. In any case, this proposal guarantees that all elements in a multi-dimensional tuple are accessible.

### 

### The event qualifiers {#the-event-qualifiers}

#### ‘first’ {#‘first’}

   
While the repetition operator allows the user to constrain event pattern matching, the event qualifiers allow the user to define what the engine should consider when encountering event repetitions. For instance, assume the following sequence of events:

| A1 B1 A2 A3 C1 B2 C2 A4 C3 |
| :---- |

 How can the user define a complex event X that is composed by A followed by C, but disregarding event repetitions?  
   
Using the following syntax would not achieve the desired result:

| var x \= (\\a \-\>\\b)  |
| :---- |

   
The previous definition would result in instantiating one X event for each pair of \[A,C\] where A happens before C. I.e.:  
 

| x \= \[A1, C1\] x \= \[A2, C1\] x \= \[A3, C1\] x \= \[A1, C2\] x \= \[A2, C2\] x \= \[A3, C2\] x \= \[A1, C3\] x \= \[A2, C3\] x \= \[A3, C3\] x \= \[A4, C3\] |
| :---- |

   
The desired result could be obtained by using an event qualifier instead:  
 

| var x \= (first A() \-\> first C()) |
| :---- |

   
The above code would result:

| X1 \= \[A1, C1\] |
| :---- |

   
 Please note that in the previous examples, the letters A..D can represent either individual events or event tuples.

#### ‘every’ {#‘every’}

The ‘every’ qualifier instructs the engine to start a new match for each event matching the pattern. This is the default behavior of all patterns in the engine and so every qualifier is optional for single patterns, but still needed for event tuple patterns. E.g., assuming the sequence of events in the beginning of this chapter:

| \\a \-\> \\b |
| :---- |

The above is the same as below

| every \\a \-\> every \\b |
| :---- |

# Rule Structure  (EBNF) TODO {#rule-structure-(ebnf)-todo}

rule:  
‘rule’ head ‘{‘ body ‘}’

body:  
group

group:  
‘and’ | ‘or’ ‘(‘ arg? (‘,’ arg) ‘)’

expr:

element

