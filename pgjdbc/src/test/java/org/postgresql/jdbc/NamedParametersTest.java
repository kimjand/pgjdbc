package org.postgresql.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.postgresql.PGPreparedStatement;
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
import java.util.logging.Level;
import java.util.logging.Logger;

public class NamedParametersTest extends BaseTest4 {

  @Test
  public void dontMix() throws Exception {
    try {
      con.prepareStatement("select ?+:dummy");
      fail("Should throw a SQLException");
    } catch (SQLException ex) {
      // ignore
      assertEquals("Positional and named parameters cannot be combined! Saw " +
          "positional parameter first.", ex.getMessage());
    }

    try {
      con.prepareStatement("select :dummy+?");
      fail("Should throw a SQLException");
    } catch (SQLException ex) {
      // ignore
      assertEquals("Positional and named parameters cannot be combined! Saw " +
          "named parameter first.", ex.getMessage());
    }
  }


  @Test
  public void setString() throws Exception {
    {
      PreparedStatement preparedStatement = con.prepareStatement("select :ASTR||:bStr||:c AS " +
          "teststr");
      PGPreparedStatement ps = preparedStatement.unwrap(PGPreparedStatement.class);
      final String failureParameterName = "BsTr";
      try {
        ps.setString(failureParameterName, "1");
        fail("Should throw a SQLException");
      } catch (SQLException ex) {
        assertEquals(String.format("The parameterName was not found : %s. The following names " +
                "are known : \n\t %s", failureParameterName, Arrays.toString(new String[]{"ASTR",
                "bStr", "c"})),
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
      PreparedStatement preparedStatement = con.prepareStatement(sql);
      PGPreparedStatement ps = preparedStatement.unwrap(PGPreparedStatement.class);

      assertEquals(sql, preparedStatement.toString());

      ps.setString("a", "1");
      ps.setString("aa", "2");

      assertEquals("select '2'||'2'||'1' AS teststr", preparedStatement.toString());

      preparedStatement.execute();
      final ResultSet resultSet = preparedStatement.getResultSet();
      resultSet.next();

      final String testStr = resultSet.getString("testStr");
      Assert.assertEquals("221", testStr);
      preparedStatement.close();
    }
  }

  @Test
  public void setDateReuse() throws Exception {
    {
      TestUtil.createTable(con, "test_dates", "pk INTEGER, d1 date, d2 date, d3 date");

      final Logger logger = Logger.getLogger("org.postgresql");
      logger.setLevel(Level.FINEST);
      logger.getParent().setLevel(Level.FINEST);
      logger.getParent().getHandlers()[0].setLevel(Level.FINEST);

      final java.sql.Date sqlDate = java.sql.Date.valueOf(LocalDate.now());
      {
        final String insertSQL = "INSERT INTO test_dates( d1, pk, d2, d3 ) values ( :date, :pk, " +
            ":date, :date )";
        PgPreparedStatement insertStmt =
            con.prepareStatement(insertSQL).unwrap(PgPreparedStatement.class);

        insertStmt.setInt("pk", 1);
        insertStmt.setDate("date", sqlDate);
        insertStmt.execute();
        insertStmt.close();
      }

      {
        final String sql = "SELECT td.*, :date::DATE AS d4 FROM test_dates td WHERE td.d1 = :date" +
            " AND :date " +
            "BETWEEN td.d2 AND td.d3";
        PgPreparedStatement pstmt = con.prepareStatement(sql).unwrap(PgPreparedStatement.class);

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
        final String updateSQL = "update test_dates SET d1 = :date2, d3 = :date2 WHERE pk = :pk " +
            "AND d1 =:date RETURNING d1, :date AS d2, d3, d2 AS d4";
        PgPreparedStatement updateStmt =
            con.prepareStatement(updateSQL).unwrap(PgPreparedStatement.class);

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
  public void testBatchWithReWrittenRepeatedInsertStatementOptimizationEnabled()
      throws SQLException {

    // Drop the test table if it already exists for some reason. It is
    // not an error if it doesn't exist.
    TestUtil.createTable(con, "testbatch", "pk INTEGER, col1 INTEGER, col2 INTEGER");

    PgPreparedStatement pstmt = null;
    try {

      pstmt =
          con.prepareStatement("INSERT INTO testbatch VALUES (:int1,:int2,:int1)").unwrap(PgPreparedStatement.class);
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
  }
}
