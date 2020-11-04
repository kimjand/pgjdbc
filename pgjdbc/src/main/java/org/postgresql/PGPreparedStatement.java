/*
 * Copyright (c) 2020, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLType;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;

public interface PGPreparedStatement extends PGStatement {
  /**
   * @see java.sql.PreparedStatement#setNull(int, int)
   */
  void setNull(String parameterName, int sqlType) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setBoolean(int, boolean)
   */
  void setBoolean(String parameterName, boolean x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setByte(int, byte)
   */
  void setByte(String parameterName, byte x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setShort(int, short)
   */
  void setShort(String parameterName, short x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setInt(int, int)
   */
  void setInt(String parameterName, int x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setLong(int, long)
   */
  void setLong(String parameterName, long x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setFloat(int, float)
   */
  void setFloat(String parameterName, float x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setDouble(int, double)
   */
  void setDouble(String parameterName, double x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setBigDecimal(int, BigDecimal)
   */
  void setBigDecimal(String parameterName, BigDecimal x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setString(int, String)
   */
  void setString(String parameterName, String x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setBytes(int, byte[])
   */
  void setBytes(String parameterName, byte[] x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setDate(int, Date)
   */
  void setDate(String parameterName, java.sql.Date x)
      throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setTime(int, Time)
   */
  void setTime(String parameterName, java.sql.Time x)
      throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setTimestamp(int, Timestamp)
   */
  void setTimestamp(String parameterName, java.sql.Timestamp x)
      throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setAsciiStream(int, InputStream, int)
   */
  void setAsciiStream(String parameterName, java.io.InputStream x, int length)
      throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setUnicodeStream(int, InputStream, int)
   */
  @Deprecated
  void setUnicodeStream(String parameterName, java.io.InputStream x,
      int length) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setBinaryStream(int, InputStream, int)
   */
  void setBinaryStream(String parameterName, java.io.InputStream x,
      int length) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setObject(int, Object, int)
   */
  void setObject(String parameterName, Object x, int targetSqlType)
      throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setObject(int, Object)
   */
  void setObject(String parameterName, Object x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setCharacterStream(int, Reader, int)
   */
  void setCharacterStream(String parameterName,
      java.io.Reader reader,
      int length) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setRef(int, Ref)
   */
  void setRef(String parameterName, Ref x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setBlob(int, Blob) (int, Blob)
   */
  void setBlob(String parameterName, Blob x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setClob(int, Clob)
   */
  void setClob(String parameterName, Clob x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setArray(int, Array)
   */
  void setArray(String parameterName, Array x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setDate(int, Date, Calendar)
   */
  void setDate(String parameterName, java.sql.Date x, Calendar cal) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setTime(int, Time, Calendar)
   */
  void setTime(String parameterName, java.sql.Time x, Calendar cal) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setTimestamp(int, Timestamp, Calendar)
   */
  void setTimestamp(String parameterName, java.sql.Timestamp x, Calendar cal) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setNull(int, int, String)
   */
  void setNull(String parameterName, int sqlType, String typeName) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setRowId(int, RowId)
   */
  void setRowId(String parameterName, RowId x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setNString(int, String)
   */
  void setNString(String parameterName, String value) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setNCharacterStream(int, Reader, long)
   */
  void setNCharacterStream(String parameterName, Reader value, long length) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setNClob(int, NClob)
   */
  void setNClob(String parameterName, NClob value) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setClob(int, Reader, long)
   */
  void setClob(String parameterName, Reader reader, long length) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setBlob(int, InputStream, long)
   */
  void setBlob(String parameterName, InputStream inputStream, long length) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setNClob(int, Reader, long)
   */
  void setNClob(String parameterName, Reader reader, long length) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setSQLXML(int, SQLXML)
   */
  void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setObject(int, Object, int, int)
   */
  void setObject(String parameterName, Object x, int targetSqlType, int scaleOrLength)
      throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setAsciiStream(int, InputStream, long)
   */
  void setAsciiStream(String parameterName, java.io.InputStream x, long length)
      throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setBinaryStream(int, InputStream, long)
   */
  void setBinaryStream(String parameterName, java.io.InputStream x,
      long length) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setCharacterStream(int, Reader, long)
   */
  void setCharacterStream(String parameterName,
      java.io.Reader reader,
      long length) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setAsciiStream(int, InputStream)
   */
  void setAsciiStream(String parameterName, java.io.InputStream x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setBinaryStream(int, InputStream)
   */
  void setBinaryStream(String parameterName, java.io.InputStream x) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setCharacterStream(int, Reader)
   */
  void setCharacterStream(String parameterName,
      java.io.Reader reader) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setNCharacterStream(int, Reader)
   */
  void setNCharacterStream(String parameterName, Reader value) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setClob(int, Reader)
   */
  void setClob(String parameterName, Reader reader) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setBlob(int, InputStream)
   */
  void setBlob(String parameterName, InputStream inputStream) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setNClob(int, Reader)
   */
  void setNClob(String parameterName, Reader reader) throws SQLException;

  /**
   * @see java.sql.PreparedStatement#setObject(int, Object, SQLType, int)
   */
  default void setObject(String parameterName, Object x, SQLType targetSqlType,
      int scaleOrLength) throws SQLException {
    throw new SQLFeatureNotSupportedException("setObject not implemented");
  }

  /**
   * @see java.sql.PreparedStatement#setObject(int, Object, SQLType)
   */
  default void setObject(String parameterName, Object x, SQLType targetSqlType)
      throws SQLException {
    throw new SQLFeatureNotSupportedException("setObject not implemented");
  }

  /**
   * Returns A List of placeholder names, in order corresponding to the first occurrence of each
   * name.
   */
  List<String> getParameterNames() throws SQLException;
}
