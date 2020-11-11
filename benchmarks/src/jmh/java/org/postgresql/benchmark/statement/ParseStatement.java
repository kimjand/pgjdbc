/*
 * Copyright (c) 2003, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.benchmark.statement;

import org.postgresql.PGPreparedStatement;
import org.postgresql.util.ConnectionUtil;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Tests the performance of preparing, executing and performing a fetch out of a simple "SELECT ?,
 * ?, ... ?" statement.
 */
@Fork(value = 5, jvmArgsPrepend = "-Xmx128m")
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ParseStatement {
  @Param({"false"})
  public boolean unique;
  @Param({"false", "true"})
  public boolean named;
  @Param({"0", "1", "10", "20", "100", "1000"})
  private int bindCount;
  private Connection connection;

  @Param({"conservative"})
  private String autoSave;

  private String sql;

  private int cntr;

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(ParseStatement.class.getSimpleName())
        .addProfiler(GCProfiler.class)
        .detectJvmArgs()
        .build();

    new Runner(opt).run();
  }

  @Setup(Level.Trial)
  public void setUp() throws SQLException {
    Properties props = ConnectionUtil.getProperties();
    props.put("autosave", autoSave);

    connection = DriverManager.getConnection(ConnectionUtil.getURL(), props);

    // Start transaction
    Statement st = connection.createStatement();
    connection.setAutoCommit(false);
    st.execute("BEGIN");
    st.close();

    StringBuilder sb = new StringBuilder();
    sb.append("SELECT ");
    for (int i = 0; i < bindCount; i++) {
      if (i > 0) {
        sb.append(',');
      }
      if (named) {
        sb.append(":p_").append(i);
      } else {
        sb.append('?');
      }
    }
    sql = sb.toString();
  }

  @TearDown(Level.Trial)
  public void tearDown() throws SQLException {
    connection.close();
  }

  @Benchmark
  public Statement bindExecuteFetch(Blackhole b) throws SQLException {
    String sql = this.sql;
    if (unique) {
      sql += " -- " + cntr++;
    }
    PGPreparedStatement ps = connection.prepareStatement(sql).unwrap(PGPreparedStatement.class);
    for (int i = 1; i <= bindCount; i++) {
      if (named) {
        ps.setInt("p_" + (i - 1), i);
      } else {
        ps.setInt(i, i);
      }
    }
    ResultSet rs = ps.executeQuery();
    while (rs.next()) {
      for (int i = 1; i <= bindCount; i++) {
        b.consume(rs.getInt(i));
      }
    }
    rs.close();
    ps.close();
    return ps;
  }
}
