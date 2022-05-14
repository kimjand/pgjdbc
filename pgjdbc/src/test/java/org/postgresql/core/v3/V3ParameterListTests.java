/*
 * Copyright (c) 2003, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.core.v3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.postgresql.core.NativeQuery;
import org.postgresql.core.Oid;
import org.postgresql.core.ParameterContext;
import org.postgresql.core.Parser;
import org.postgresql.jdbc.PlaceholderStyles;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Test cases to make sure the parameterlist implementation works as expected.
 *
 * @author Jeremy Whiting jwhiting@redhat.com
 *
 */
public class V3ParameterListTests {

  private static class TestParameterContext extends ParameterContext {
    TestParameterContext(PlaceholderStyles allowedPlaceholderStyles) {
      super(allowedPlaceholderStyles);
    }

    static ParameterContext buildNamed(List<Integer> placeholderPositions,
        List<String> placeholderNames) throws SQLException {
      if (placeholderPositions.size() != placeholderNames.size()) {
        throw new IllegalArgumentException("Length of placerholderPositions and placerholderNames"
            + " differ");
      }
      final ParameterContext ctx = new ParameterContext(PlaceholderStyles.ANY);
      for (int i = 0; i < placeholderPositions.size(); i++) {
        ctx.addNamedParameter(placeholderPositions.get(i), BindStyle.NAMED, placeholderNames.get(i));
      }

      return ctx;
    }
  }

  private TypeTransferModeRegistry transferModeRegistry;

  @Before
  public void setUp() throws Exception {
    transferModeRegistry = new TypeTransferModeRegistry() {
        @Override
        public boolean useBinaryForSend(int oid) {
          return false;
        }

        @Override
        public boolean useBinaryForReceive(int oid) {
          return false;
        }
    };
  }

  @Test
  public void bogusPositions() throws SQLException {
    assertThrows(IllegalArgumentException.class,
        () -> {
          final ParameterContext parameterContext = new ParameterContext(PlaceholderStyles.ANY);
          parameterContext.addPositionalParameter(0);
          parameterContext.addPositionalParameter(0);
        });
    assertThrows(IllegalArgumentException.class,
        () -> {
          final ParameterContext parameterContext = new ParameterContext(PlaceholderStyles.ANY);
          parameterContext.addPositionalParameter(1);
          parameterContext.addPositionalParameter(0);
        });

    assertThrows(IllegalArgumentException.class,
        () -> {
          final ParameterContext parameterContext = new ParameterContext(PlaceholderStyles.ANY);
          parameterContext.addNamedParameter(0, ParameterContext.BindStyle.NAMED, "dummy");
          parameterContext.addNamedParameter(0, ParameterContext.BindStyle.NAMED, "dummy2");
        });
    assertThrows(IllegalArgumentException.class,
        () -> {
          final ParameterContext parameterContext = new ParameterContext(PlaceholderStyles.ANY);
          parameterContext.addNamedParameter(1, ParameterContext.BindStyle.NAMED, "dummy");
          parameterContext.addNamedParameter(0, ParameterContext.BindStyle.NAMED, "dummy2");
        });
  }

  @Test
  public void bindParameterReuse() throws SQLException {

    String query;
    List<NativeQuery> qry;
    NativeQuery nativeQuery;

    query = "SELECT :a+:a+:a+:b+:c+:b+:c AS a";
    qry = Parser.parseJdbcSql(query, true, true, true, false, true, PlaceholderStyles.ANY);
    assertEquals(1, qry.size());
    nativeQuery = qry.get(0);

    SimpleParameterList parameters = new SimpleParameterList(3,
        transferModeRegistry,
        TestParameterContext.buildNamed(
            Arrays.asList(0, 1, 2),
            Arrays.asList("a", "b", "c")
        )
    );
    assertEquals(query, nativeQuery.toString(parameters));

    query = "select :ASTR||:bStr||:c AS \nteststr";
    qry = Parser.parseJdbcSql(query, true, true, true, false, true, PlaceholderStyles.ANY);
    assertEquals(1, qry.size());
    nativeQuery = qry.get(0);

    parameters = new SimpleParameterList(3,
        transferModeRegistry,
        TestParameterContext.buildNamed(
            Arrays.asList(0, 1, 2),
            Arrays.asList("ASTR", "bStr", "c")
        )
    );
    assertEquals(query, nativeQuery.toString(parameters));

    parameters.setStringParameter(parameters.getIndex("c"), "p3", Oid.VARCHAR);
    parameters.setStringParameter(parameters.getIndex("bStr"), "p2", Oid.VARCHAR);
    parameters.setStringParameter(parameters.getIndex("ASTR"), "p1", Oid.VARCHAR);
    assertEquals(
        query
            .replace(":ASTR", "'p1'")
            .replace(":bStr", "'p2'")
            .replace(":c", "'p3'"),
        nativeQuery.toString(parameters)
    );
  }

  @Test
  public void dontModifyEMPTY_CONTEXT() throws SQLException {
    assertThrows(UnsupportedOperationException.class,
        () -> ParameterContext.EMPTY_CONTEXT.addPositionalParameter(0));
    assertThrows(UnsupportedOperationException.class,
        () -> ParameterContext.EMPTY_CONTEXT.addNamedParameter(0, ParameterContext.BindStyle.NAMED,"dummy"));
  }

  /**
   * Test to check the merging of two collections of parameters. All elements
   * are kept.
   *
   * @throws SQLException
   *           raised exception if setting parameter fails.
   */
  @Test
  public void testMergeOfParameterLists() throws SQLException {
    SimpleParameterList s1SPL = new SimpleParameterList(8, transferModeRegistry);
    s1SPL.setIntParameter(1, 1);
    s1SPL.setIntParameter(2, 2);
    s1SPL.setIntParameter(3, 3);
    s1SPL.setIntParameter(4, 4);

    SimpleParameterList s2SPL = new SimpleParameterList(4, transferModeRegistry);
    s2SPL.setIntParameter(1, 5);
    s2SPL.setIntParameter(2, 6);
    s2SPL.setIntParameter(3, 7);
    s2SPL.setIntParameter(4, 8);

    s1SPL.appendAll(s2SPL);
    assertEquals(
        "Expected string representation of values does not match outcome.",
        "<[1 ,2 ,3 ,4 ,5 ,6 ,7 ,8]>", s1SPL.toString());
  }

  @Test
  public void testHasNamedParameters() throws SQLException {
    String query;
    List<NativeQuery> qry;
    NativeQuery nativeQuery;

    query = "SELECT ?";
    qry = Parser.parseJdbcSql(query, true, true, true, false, true, PlaceholderStyles.ANY);
    nativeQuery = qry.get(0);
    Assert.assertFalse(nativeQuery.parameterCtx.hasNamedParameters());
    IllegalStateException illegalStateException = assertThrows(IllegalStateException.class, nativeQuery.parameterCtx::getPlaceholderNames);
    Assert.assertEquals("Call hasNamedParameters() first.",illegalStateException.getMessage());

    query = "SELECT :a";
    qry = Parser.parseJdbcSql(query, true, true, true, false, true, PlaceholderStyles.ANY);
    nativeQuery = qry.get(0);
    Assert.assertTrue(nativeQuery.parameterCtx.hasNamedParameters());
    Assert.assertEquals(Collections.singletonList("a"),nativeQuery.parameterCtx.getPlaceholderNames());

    query = "SELECT $1";
    qry = Parser.parseJdbcSql(query, true, true, true, false, true, PlaceholderStyles.ANY);
    nativeQuery = qry.get(0);
    Assert.assertTrue(nativeQuery.parameterCtx.hasNamedParameters());
    Assert.assertEquals(Collections.singletonList("$1"),nativeQuery.parameterCtx.getPlaceholderNames());
  }
}
