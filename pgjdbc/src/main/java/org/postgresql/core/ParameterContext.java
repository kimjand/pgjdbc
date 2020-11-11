/*
 * Copyright (c) 2020, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.core;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * org.postgresql.core.Parser stores information about placeholder occurrences in ParameterContext.
 * In case of standard JDBC placeholders ('?') the position in the SQL text is recorded.
 * For named placeholders (':paramName') the name is recorded as well as the position.
 * PgPreparedStatement can then use the name to lookup the parameter corresponding index.
 * These values are also used by toString() methods to provide a human readable representation of
 * the SQL text.
 */
public class ParameterContext {
  public static final ParameterContext EMPTY_CONTEXT = new ParameterContext();
  private static final List<Integer> NO_PLACEHOLDERS = Collections.emptyList();

  static {
    EMPTY_CONTEXT.placeholderPositions = NO_PLACEHOLDERS;
  }

  private @Nullable BindStyle bindStyle = null;
  private @Nullable List<Integer> placeholderPositions = null;
  private @Nullable List<String> placeholderNames = null;
  private @Nullable List<Integer> placeholderAtPosition = null;

  /**
   * Adds a positional parameter to this ParameterContext.
   * Once a positional parameter have been added all subsequent parameters must be positional.
   * Positional parameters cannot be reused, and their order of appearance will correspond to the
   * parameters sent to the PostgreSQL backend.
   * @param position in the SQL text where the parser captured the placeholder.
   * @return 1-indexed position in the order of appearance of positional parameters
   * @throws SQLException if positional and named parameters are mixed.
   */
  public int addPositionalParameter(int position) throws SQLException {
    if (bindStyle == BindStyle.NAMED) {
      throw new SQLException("Positional and named parameters cannot be combined! Saw named"
          + " parameter first.");
    }

    if (bindStyle == null) {
      bindStyle = BindStyle.POSITIONAL;
    }

    if (placeholderPositions == null) {
      placeholderPositions = new ArrayList<>();
    }

    placeholderPositions.add(position);
    int bindIndex = placeholderPositions.size() - 1;

    if (placeholderAtPosition == null) {
      placeholderAtPosition = new ArrayList<>();
    }

    placeholderAtPosition.add(bindIndex);

    return bindIndex + 1;
  }

  public boolean hasParameters() {
    return placeholderPositions != null && !placeholderPositions.isEmpty();
  }

  public boolean hasNamedParameters() {
    return placeholderNames != null && !placeholderNames.isEmpty();
  }

  /**
   * @param i 0-indexed position in the order of first appearance
   * @return The name of the placeholder at this backend parameter position
   */
  public String getPlaceholderName(int i) {
    if (placeholderNames == null) {
      throw new IllegalStateException("Call hasNamedParameters() first.");
    }
    return placeholderNames.get(i);
  }

  /**
   * @param i 0-indexed position in the order of appearance
   * @return The position of the placeholder in the SQL text for this placeholder index
   */
  public int getPlaceholderPosition(int i) {
    if (placeholderPositions == null) {
      throw new IllegalStateException("Call hasParameters() first.");
    }
    return placeholderPositions.get(i);
  }

  /**
   * @param i 0-indexed position in the order of appearance
   * @return The position of the placeholder in the order of first appearance of each placeholder
   */
  public int getPlaceholderAtPosition(int i) {
    if (placeholderAtPosition == null) {
      throw new IllegalStateException("Call hasParameters() first.");
    }
    return placeholderAtPosition.get(i);
  }

  public int getLastPlaceholderPosition() {
    if (placeholderPositions == null) {
      throw new IllegalStateException("Call hasParameters() first.");
    }
    return placeholderPositions.get(placeholderPositions.size() - 1);
  }

  /**
   * Adds a named parameter to this ParameterContext.
   * Once a named Parameter have been added all subsequent parameters must be named.
   * Using named parameters enable reuse of the same parameters in several locations of the SQL
   * text. The parameters only have to be sent to the PostgreSQL backend once per name specified.
   * The values will be sent in the order of the first appearance of their placeholder.
   * @param position in the SQL text where the parser captured the placeholder.
   * @param bindName is the placeholder name captured by the parser.
   * @return 1-indexed position in the order of first appearance of named parameters
   * @throws SQLException if positional and named parameters are mixed.
   */
  public int addNamedParameter(int position, String bindName) throws SQLException {
    if (bindStyle == BindStyle.POSITIONAL) {
      throw new SQLException("Positional and named parameters cannot be combined! Saw "
          + "positional parameter first.");
    }

    if (bindStyle == null) {
      bindStyle = BindStyle.NAMED;
    }

    if (placeholderPositions == null) {
      placeholderPositions = new ArrayList<>();
    }
    placeholderPositions.add(position);

    if (placeholderNames == null) {
      placeholderNames = new ArrayList<>();
    }
    int bindIndex = placeholderNames.indexOf(bindName);
    if (bindIndex == -1) {
      bindIndex = placeholderNames.size();
      placeholderNames.add(bindName);
    }

    if (placeholderAtPosition == null) {
      placeholderAtPosition = new ArrayList<>();
    }
    placeholderAtPosition.add(bindIndex);
    return bindIndex + 1;
  }

  /**
   * @return Returns the number of placeholder appearances in the SQL text
   */
  public int placeholderCount() {
    return placeholderPositions == null ? 0 : placeholderPositions.size();
  }

  /**
   * @return Returns the number of parameter to be sent to the backend.
   */
  public int nativeParameterCount() {
    if (placeholderNames != null) {
      return placeholderNames.size();
    }

    return placeholderCount();
  }

  public List<Integer> getPlaceholderPositions() {
    if (placeholderPositions == null) {
      return NO_PLACEHOLDERS;
    }
    return placeholderPositions;
  }

  enum BindStyle {
    POSITIONAL, NAMED
  }
}
