/*
 * Copyright (c) 2003, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.sql.SQLException;
import java.util.List;

public interface PGPreparedStatement extends PGStatement {
  void setString(String parameterName, String x) throws SQLException;

  void setInt(String parameterName, int i) throws SQLException;

  void setDate(String parameterName, java.sql.@Nullable Date x) throws SQLException;

  List<String> getParameterNames() throws SQLException;
}
