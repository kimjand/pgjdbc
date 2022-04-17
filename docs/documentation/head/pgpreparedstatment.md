---
layout: default_docs
title: Using the PGPreparedStatement Interface
header: Chapter 5. Issuing a Query and Processing the Result
resource: media
previoustitle: Using Java 8 Date and Time classes
previous: java8-date-time.html
nexttitle: Chapter 6. Calling Stored Functions
next: callproc.html
---

PgJDBC provides a non-standard extension to `PreparedStatment` that allows the use of named 
placeholders. Two styles are supported, `named placeholders` and `native placeholders`. This means that the same parameter can be used multiple times in a SQL statement.
The following must be considered when using the extension interface `PGPreparedStatement`:

* A named placeholder starts with a colon (`:`) and at least one character as defined by `Parser.isIdentifierStartChar()` 
    this may be followed by zero or more characters as defined by `Parser.isIdentifierContChar()`. 
* A native placeholder starts with a dollar (`$`) followed by at least one digit. Native parameters must form a contiguous set of integers, starting from 1. The names may appear in any order in the SQL statement.
* Parameter names in the `PGPreparedStatement` can be obtained from the method `getParameterNames()`.
* Setter methods defined `PGPreparedStatement` can be used to set the parameter values corresponding to the parameter name.
* Values can also be assigned to parameters using the setter methods in `PreparedStatement` using the 1-based index of the parameter name, as returned by `getParameterNames()`.
* PgJDBC does not allow mixing positional (`?`) and named (`:`) or native (`$`) placeholders.

<a name="named-parameters"></a>
**Example 5.5. Using named parameters**

This example binds a value to the parameter named myParam:

```java
PGPreparedStatement ps = 
  conn.prepareStatement("SELECT col_a FROM mytable WHERE col_a < :myParam AND col_b > :myParam")
      .unwrap(PGPreparedStatement.class);

ps.setInt("myParam", 42);
...
```

This example binds a value to a named parameter by the index of the parameter:

```java
PreparedStatement ps = 
  conn.prepareStatement("SELECT col_a FROM mytable WHERE col_a < :myParam AND col_b > :myParam");

ps.setInt(1, 42);
...
```
<a name="named-parameters"></a>
**Example 5.6. Using native parameters**

This example binds a value to the parameter $1:

```java
PGPreparedStatement ps = 
  conn.prepareStatement("SELECT col_a FROM mytable WHERE col_a < $1 AND col_b > $1")
      .unwrap(PGPreparedStatement.class);

ps.setInt("$1", 42);
...
```

This example binds a value to a named parameter by the index of the parameter:

```java
PreparedStatement ps = 
  conn.prepareStatement("SELECT col_a FROM mytable WHERE col_a < $1 AND col_b > $1");

ps.setInt(1, 42);
...
```
