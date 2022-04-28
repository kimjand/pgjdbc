/*
 * Copyright (c) 2020, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.postgresql.PGConnection;
import org.postgresql.PGPreparedStatement;
import org.postgresql.PGProperty;
import org.postgresql.test.TestUtil;
import org.postgresql.test.jdbc2.BaseTest4;
import org.postgresql.test.jdbc2.BatchExecuteTest;
import org.postgresql.util.PSQLException;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

public class NamedParametersTest extends BaseTest4 {

  @Override
  protected void updateProperties(Properties props) {
    super.updateProperties(props);
    PGProperty.PLACEHOLDER_STYLES.set(props, PlaceholderStyles.NAMED.value());
  }

  @Test
  public void dontMix() throws Exception {
    final PGConnection pgConnection = con.unwrap(PGConnection.class);
    pgConnection.setPlaceholderStyle(PlaceholderStyles.ANY);

    try {
      con.prepareStatement("select ?+:dummy");
      fail("Should throw a SQLException");
    } catch (SQLException ex) {
      // ignore
      assertEquals(
          "Multiple bind styles cannot be combined. Saw POSITIONAL first but attempting to also "
              + "use: NAMED",
          ex.getMessage());
    }

    try {
      con.prepareStatement("select :dummy+?");
      fail("Should throw a SQLException");
    } catch (SQLException ex) {
      // ignore
      assertEquals(
          "Multiple bind styles cannot be combined. Saw NAMED first but attempting to also use: "
              + "POSITIONAL",
          ex.getMessage());
    }

    try {
      con.prepareStatement("select :test+$1");
      fail("Should throw a SQLException");
    } catch (SQLException ex) {
      // ignore
      assertEquals(
          "Multiple bind styles cannot be combined. Saw NAMED first but attempting to also use: "
              + "NATIVE",
          ex.getMessage());
    }
  }

  @Test
  public void testHasNamedParameters() throws SQLException {
    String sql = "SELECT 'constant'";
    try (PGPreparedStatement testStmt = con.prepareStatement(sql).unwrap(PGPreparedStatement.class)) {
      Assert.assertFalse(testStmt.hasParameterNames());
      final PSQLException psqlException = Assert.assertThrows(PSQLException.class, testStmt::getParameterNames);
      Assert.assertEquals("No parameter names are available, you need to call hasParameterNames "
          + "to verify the presence of any names.\n"
          + "Perhaps you need to enable support for named placeholders? Current setting is: "
          + "PLACEHOLDER_STYLES = ANY", psqlException.getMessage());
    }

    sql = "SELECT :myParam";
    try (PGPreparedStatement testStmt = con.prepareStatement(sql).unwrap(PGPreparedStatement.class)) {
      Assert.assertTrue(testStmt.hasParameterNames());
      Assert.assertEquals(Collections.singletonList("myParam"), testStmt.getParameterNames());
    }
  }

  @Test
  public void testMultiDigit() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("SELECT ");
    for ( int i = 0; i <= 10000; i++) {
      if (i % 10 == 0) {
        final String sql = sb.toString();
        try (PGPreparedStatement testStmt = con.prepareStatement(sql).unwrap(PGPreparedStatement.class)) {
          Assert.assertEquals(i != 0, testStmt.hasParameterNames());
          if ( i > 0 ) {
            Assert.assertEquals(i, testStmt.getParameterNames().size());
          }
        }
      }

      if ( i > 0 ) {
        sb.append(",");
      }
      sb.append(":p").append(i + 1);
    }
  }

  @Test
  public void testMultiDigitReuse() throws Exception {
    final int parameterCount = 100;
    StringBuilder sb = new StringBuilder();
    sb.append("SELECT ");
    for ( int i = 0; i <= 10000; i++) {
      if (i % parameterCount == 0) {
        final String sql = sb.toString();
        try (PGPreparedStatement testStmt = con.prepareStatement(sql).unwrap(PGPreparedStatement.class)) {
          Assert.assertEquals(i != 0, testStmt.hasParameterNames());
          if ( i > 0 ) {
            Assert.assertEquals(parameterCount, testStmt.getParameterNames().size());
          }
        }
      }

      if ( i > 0 ) {
        sb.append(",");
      }
      sb.append(":p").append((i % parameterCount) + 1);
    }
  }

  @Test
  public void setDateReuse() throws Exception {
    {
      TestUtil.createTable(con, "test_dates", "pk INTEGER, d1 date, d2 date, d3 date");

      final java.sql.Date sqlDate = java.sql.Date.valueOf(LocalDate.now());
      {
        final String insertSQL = "INSERT INTO test_dates( d1, pk, d2, d3 ) values ( :date, :pk, "
            + ":date, :date )";
        PGPreparedStatement insertStmt =
            con.prepareStatement(insertSQL).unwrap(PGPreparedStatement.class);

        insertStmt.setInt("pk", 1);
        insertStmt.setDate("date", sqlDate);
        insertStmt.execute();
        insertStmt.close();
      }

      {
        final String sql = "SELECT td.*, :date::DATE AS d4 FROM test_dates td WHERE td.d1 = :date"
            + " AND :date "
            + "BETWEEN td.d2 AND td.d3";
        PGPreparedStatement pstmt = con.prepareStatement(sql).unwrap(PGPreparedStatement.class);

        pstmt.setDate("date", sqlDate);
        pstmt.execute();

        final ResultSet resultSet = pstmt.getResultSet();
        assert resultSet != null;
        resultSet.next();

        Assert.assertEquals(sqlDate, resultSet.getDate("d1"));
        Assert.assertEquals(sqlDate, resultSet.getDate("d2"));
        Assert.assertEquals(sqlDate, resultSet.getDate("d3"));
        Assert.assertEquals(sqlDate, resultSet.getDate("d4"));

        pstmt.close();
      }

      final java.sql.Date sqlDate2 = java.sql.Date.valueOf(LocalDate.now().plus(1,
          ChronoUnit.DAYS));
      {
        final String updateSQL = "update test_dates SET d1 = :date2, d3 = :date2 WHERE pk = :pk "
            + "AND d1 =:date RETURNING d1, :date AS d2, d3, d2 AS d4";
        PGPreparedStatement updateStmt =
            con.prepareStatement(updateSQL).unwrap(PGPreparedStatement.class);

        updateStmt.setInt("pk", 1);
        updateStmt.setDate("date", sqlDate);
        updateStmt.setDate("date2", sqlDate2);
        updateStmt.execute();

        final ResultSet resultSet = updateStmt.getResultSet();
        assert resultSet != null;
        resultSet.next();

        Assert.assertEquals(sqlDate2, resultSet.getDate("d1"));
        Assert.assertEquals(sqlDate, resultSet.getDate("d2"));
        Assert.assertEquals(sqlDate2, resultSet.getDate("d3"));
        Assert.assertEquals(sqlDate, resultSet.getDate("d4"));

        updateStmt.close();
      }
    }
  }

  @Test
  public void setString() throws Exception {
    {
      PreparedStatement preparedStatement = con.prepareStatement("select :ASTR||:bStr||:c AS "
          + "teststr");
      PGPreparedStatement ps = preparedStatement.unwrap(PGPreparedStatement.class);
      final String failureParameterName = "BsTr";
      try {
        ps.setString(failureParameterName, "1");
        fail("Should throw a SQLException");
      } catch (SQLException ex) {
        assertEquals(String.format("The parameterName was not found : %s. The following names "
                + "are known : \n\t %s", failureParameterName, Arrays.toString(new String[]{
                  "ASTR",
                  "bStr",
                  "c"})),
            ex.getMessage());
      }
      ps.setString("bStr", "1");
      ps.setString("c", "2");
      ps.setString("ASTR", "3");
      preparedStatement.execute();
      final ResultSet resultSet = preparedStatement.getResultSet();
      resultSet.next();

      final String testStr = resultSet.getString("testStr");
      Assert.assertEquals("312", testStr);
    }
  }

  @Test
  public void setStringReuse() throws Exception {
    {
      final String sql = "select :aa||:aa||:a AS teststr";
      PGPreparedStatement ps = con.prepareStatement(sql).unwrap(PGPreparedStatement.class);

      // Test toString before bind
      assertEquals(sql, ps.toString());

      ps.setString("a", "1");
      ps.setString("aa", "2");

      // Test toString after bind
      assertEquals("select '2'||'2'||'1' AS teststr", ps.toString());

      ps.execute();
      final ResultSet resultSet = ps.getResultSet();
      resultSet.next();

      final String testStr = resultSet.getString("testStr");
      Assert.assertEquals("221", testStr);
      ps.close();
    }
  }

  @Test
  public void setValuesByIndex() throws Exception {
    {
      PgPreparedStatement ps =
          (PgPreparedStatement) con.prepareStatement("select :a||:b||:c AS teststr");

      int i = 1;
      for (String name : ps.getParameterNames()) {
        switch (name) {
          case "a":
            ps.setString(i, "333");
            break;
          case "b":
            ps.setString(i, "1");
            break;
          case "c":
            ps.setString(i, "222");
            break;
        }
        i++;
      }

      ps.execute();
      final ResultSet resultSet = ps.getResultSet();
      resultSet.next();

      final String testStr = resultSet.getString("testStr");
      Assert.assertEquals("3331222", testStr);
    }
  }

  @Test
  public void testBatchWithReWrittenRepeatedInsertStatementOptimizationEnabled()
      throws SQLException {

    // Drop the test table if it already exists for some reason. It is
    // not an error if it doesn't exist.
    TestUtil.createTable(con, "testbatch", "pk INTEGER, col1 INTEGER, col2 INTEGER");

    PGPreparedStatement pstmt = null;
    try {

      pstmt =
          con.prepareStatement("INSERT INTO testbatch VALUES (:int1,:int2,:int1)")
              .unwrap(PGPreparedStatement.class);
      pstmt.setInt("int1", 1);
      pstmt.setInt("int2", 2);
      pstmt.addBatch();
      pstmt.setInt("int1", 3);
      pstmt.setInt("int2", 4);
      pstmt.addBatch();
      pstmt.setInt("int1", 5);
      pstmt.setInt("int2", 6);
      pstmt.addBatch();
      BatchExecuteTest.assertSimpleInsertBatch(3, pstmt.executeBatch());
      TestUtil.assertNumberOfRows(con, "testbatch", 3, "3 rows inserted");

      /*
       * Now check the ps can be reused. The batched statement should be reset
       * and have no knowledge of prior re-written batch. This test uses a
       * different batch size. To test if the driver detects the different size
       * and prepares the statement on with the backend. If not then an
       * exception will be thrown for an unknown prepared statement.
       */
      pstmt.setInt("int1", 1);
      pstmt.setInt("int2", 2);
      pstmt.addBatch();
      pstmt.setInt("int1", 3);
      pstmt.setInt("int2", 4);
      pstmt.addBatch();
      pstmt.setInt("int1", 5);
      pstmt.setInt("int2", 6);
      pstmt.addBatch();
      pstmt.setInt("int1", 7);
      pstmt.setInt("int2", 8);
      pstmt.addBatch();
      BatchExecuteTest.assertSimpleInsertBatch(4, pstmt.executeBatch());
      TestUtil.assertNumberOfRows(con, "testbatch", 7, "3+4 rows inserted");

      pstmt.setInt("int1", 1);
      pstmt.setInt("int2", 2);
      pstmt.addBatch();
      pstmt.setInt("int1", 3);
      pstmt.setInt("int2", 4);
      pstmt.addBatch();
      pstmt.setInt("int1", 5);
      pstmt.setInt("int2", 6);
      pstmt.addBatch();
      pstmt.setInt("int1", 7);
      pstmt.setInt("int2", 8);
      pstmt.addBatch();
      BatchExecuteTest.assertSimpleInsertBatch(4, pstmt.executeBatch());
      TestUtil.assertNumberOfRows(con, "testbatch", 11, "3+4+4 rows inserted");

    } finally {
      TestUtil.closeQuietly(pstmt);
    }

    pstmt = null;
    ResultSet rs = null;
    try {
      pstmt = con.prepareStatement(
          "SELECT count(*) AS rows FROM testbatch WHERE pk = col2 AND pk <> col1")
          .unwrap(PGPreparedStatement.class);
      pstmt.execute();
      rs = pstmt.getResultSet();
      rs.next();
      // There should be 11 rows with pk <> col1 AND pk = col2
      Assert.assertEquals(11, rs.getInt("rows"));
    } finally {
      TestUtil.closeQuietly(rs);
      TestUtil.closeQuietly(pstmt);
    }
  }

  @Test
  public void testPGPreparedStatementSetters() throws NoSuchMethodException {
    // Make sure PGPreparedStatement declares the same setXXX-methods as PreparedStatement does:
    for (Method methodFromPreparedStatement : PreparedStatement.class.getDeclaredMethods()) {
      if (methodFromPreparedStatement.getName().startsWith("set")) {
        final Class<?>[] parameterTypesFromPreparedStatement = methodFromPreparedStatement.getParameterTypes();

        // Instead of int we need to see a setter method with String as the first parameter in the signature:
        assertEquals("", int.class, parameterTypesFromPreparedStatement[0]);
        Class<?>[] wantedParameterTypes = new Class[parameterTypesFromPreparedStatement.length];
        wantedParameterTypes[0] = String.class;
        System.arraycopy(parameterTypesFromPreparedStatement, 1, wantedParameterTypes, 1, wantedParameterTypes.length - 1);

        // We will get a NoSuchMethodException here if the method is missing
        //noinspection ResultOfMethodCallIgnored
        PGPreparedStatement.class.getDeclaredMethod(methodFromPreparedStatement.getName(), wantedParameterTypes);
      }
    }
  }
}
