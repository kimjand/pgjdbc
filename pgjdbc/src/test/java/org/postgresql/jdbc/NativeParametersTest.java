/*
 * Copyright (c) 2020, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.postgresql.PGPreparedStatement;
import org.postgresql.PGProperty;
import org.postgresql.test.TestUtil;
import org.postgresql.test.jdbc2.BaseTest4;
import org.postgresql.test.jdbc2.BatchExecuteTest;

import org.junit.Assert;
import org.junit.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Properties;

public class NativeParametersTest extends BaseTest4 {

  @Override
  protected void updateProperties(Properties props) {
    super.updateProperties(props);
    PGProperty.PLACEHOLDER_STYLES.set(props, PlaceholderStyle.NATIVE.value());
  }

  @Test
  public void dontMix() throws Exception {
    try {
      con.prepareStatement("select ?+$1");
      fail("Should throw a SQLException");
    } catch (SQLException ex) {
      // ignore
      assertEquals(
          "Multiple bind styles cannot be combined. Saw POSITIONAL first but attempting to also "
              + "use: NATIVE",
          ex.getMessage());
    }

    try {
      con.prepareStatement("select $1+?");
      fail("Should throw a SQLException");
    } catch (SQLException ex) {
      // ignore
      assertEquals(
          "Multiple bind styles cannot be combined. Saw NATIVE first but attempting to also use: "
              + "POSITIONAL",
          ex.getMessage());
    }
  }

  @Test
  public void setDateReuse() throws Exception {
    {
      TestUtil.createTable(con, "test_dates", "pk INTEGER, d1 date, d2 date, d3 date");

      final java.sql.Date sqlDate = java.sql.Date.valueOf(LocalDate.now());
      {
        final String insertSQL = "INSERT INTO test_dates( d1, pk, d2, d3 ) values ( $2, $1, "
            + "$2, $2 )";
        PGPreparedStatement insertStmt =
            con.prepareStatement(insertSQL).unwrap(PGPreparedStatement.class);

        insertStmt.setInt(1, 1);
        insertStmt.setDate(2, sqlDate);
        insertStmt.execute();
        insertStmt.close();
      }

      {
        final String sql = "SELECT td.*, $1::DATE AS d4 FROM test_dates td WHERE td.d1 = $1"
            + " AND $1 "
            + "BETWEEN td.d2 AND td.d3";
        PGPreparedStatement pstmt = con.prepareStatement(sql).unwrap(PGPreparedStatement.class);

        pstmt.setDate(1, sqlDate);
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
        final String updateSQL = "update test_dates SET d1 = $3, d3 = $3 WHERE pk = $1 "
            + "AND d1 = $2 RETURNING d1, $2 AS d2, d3, d2 AS d4";
        PGPreparedStatement updateStmt =
            con.prepareStatement(updateSQL).unwrap(PGPreparedStatement.class);

        updateStmt.setInt("$1", 1);
        updateStmt.setDate("$2", sqlDate);
        updateStmt.setDate("$3", sqlDate2);
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
      PreparedStatement preparedStatement = con.prepareStatement("select $1||$2||$3 AS "
          + "teststr");
      PGPreparedStatement ps = preparedStatement.unwrap(PGPreparedStatement.class);
      final String failureParameterName = "$4";
      try {
        ps.setString(failureParameterName, "1");
        fail("Should throw a SQLException");
      } catch (SQLException ex) {
        assertEquals(String.format("The parameterName was not found : %s. The following names "
                + "are known : \n\t %s", failureParameterName, Arrays.toString(new String[]{
                  "$1",
                  "$2",
                  "$3"})),
            ex.getMessage());
      }
      ps.setString("$2", "1");
      ps.setString("$3", "2");
      ps.setString("$1", "3");
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
      final String sql = "select $2||$2||$1 AS teststr";
      PGPreparedStatement ps = con.prepareStatement(sql).unwrap(PGPreparedStatement.class);

      // Test toString before bind
      assertEquals(sql, ps.toString());

      ps.setString("$1", "1");
      ps.setString("$2", "2");

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
          (PgPreparedStatement) con.prepareStatement("select $1||$2||$3 AS teststr");

      int i = 1;
      for (String name : ps.getParameterNames()) {
        switch (name) {
          case "$1":
            ps.setString(i, "333");
            break;
          case "$2":
            ps.setString(i, "1");
            break;
          case "$3":
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
          con.prepareStatement("INSERT INTO testbatch VALUES ($1,$2,$1)")
              .unwrap(PGPreparedStatement.class);
      pstmt.setInt("$1", 1);
      pstmt.setInt("$2", 2);
      pstmt.addBatch();
      pstmt.setInt("$1", 3);
      pstmt.setInt("$2", 4);
      pstmt.addBatch();
      pstmt.setInt("$1", 5);
      pstmt.setInt("$2", 6);
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
      pstmt.setInt("$1", 1);
      pstmt.setInt("$2", 2);
      pstmt.addBatch();
      pstmt.setInt("$1", 3);
      pstmt.setInt("$2", 4);
      pstmt.addBatch();
      pstmt.setInt("$1", 5);
      pstmt.setInt("$2", 6);
      pstmt.addBatch();
      pstmt.setInt("$1", 7);
      pstmt.setInt("$2", 8);
      pstmt.addBatch();
      BatchExecuteTest.assertSimpleInsertBatch(4, pstmt.executeBatch());
      TestUtil.assertNumberOfRows(con, "testbatch", 7, "3+4 rows inserted");

      pstmt.setInt("$1", 1);
      pstmt.setInt("$2", 2);
      pstmt.addBatch();
      pstmt.setInt("$1", 3);
      pstmt.setInt("$2", 4);
      pstmt.addBatch();
      pstmt.setInt("$1", 5);
      pstmt.setInt("$2", 6);
      pstmt.addBatch();
      pstmt.setInt("$1", 7);
      pstmt.setInt("$2", 8);
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
}
