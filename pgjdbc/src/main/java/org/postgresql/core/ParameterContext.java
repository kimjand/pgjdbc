/*
 * Copyright (c) 2020, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.core;

import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link org.postgresql.core.Parser} stores information about placeholder occurrences in
 * ParameterContext. In case of standard JDBC placeholders {@code '?'} the position in the SQL text
 * is recorded. For named placeholders {@code ":paramName"} the name is recorded as well as the
 * position. {@code PgPreparedStatement} can then use the name to lookup the parameter corresponding
 * index. These values are also used by toString() methods to provide a human readable
 * representation of the SQL text.
 */
public class ParameterContext {

  enum BindStyle {
    POSITIONAL, NAMED
  }

  /**
   * EMPTY_CONTEXT is immutable. Calling the add-methods will result in
   * UnsupportedOperationException begin thrown.
   */
  public static final ParameterContext EMPTY_CONTEXT = new ParameterContext() {
    @Override
    public int addPositionalParameter(int position) throws SQLException {
      throw new UnsupportedOperationException();
    }

    @Override
    public int addNamedParameter(int position, String bindName) throws SQLException {
      throw new UnsupportedOperationException();
    }
  };

  private @Nullable BindStyle bindStyle = null;
  private @Nullable List<Integer> placeholderPositions = null;
  private @Nullable List<String> placeholderNames = null;
  private @Nullable List<Integer> placeholderAtPosition = null;

  /**
   * Adds a positional parameter to this ParameterContext. Once a positional parameter have been
   * added all subsequent parameters must be positional. Positional parameters cannot be reused, and
   * their order of appearance will correspond to the parameters sent to the PostgreSQL backend.
   *
   * @param position in the SQL text where the parser captured the placeholder.
   * @return 1-indexed position in the order of appearance of positional parameters
   * @throws SQLException if positional and named parameters are mixed.
   */
  public int addPositionalParameter(@NonNegative int position) throws SQLException {
    checkAndSetBindStyle(BindStyle.POSITIONAL);
    int bindIndex = checkAndAddPosition(position);

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
  public String getPlaceholderName(@NonNegative int i) {
    if (placeholderNames == null) {
      throw new IllegalStateException("Call hasNamedParameters() first.");
    }
    return placeholderNames.get(i);
  }

  /**
   * @param i 0-indexed position in the order of appearance
   * @return The position of the placeholder in the SQL text for this placeholder index
   */
  public int getPlaceholderPosition(@NonNegative int i) {
    if (placeholderPositions == null) {
      throw new IllegalStateException("Call hasParameters() first.");
    }
    return placeholderPositions.get(i);
  }

  /**
   * @param i 0-indexed position in the order of appearance
   * @return The position of the placeholder in the order of first appearance of each placeholder
   */
  public int getPlaceholderAtPosition(@NonNegative int i) {
    if (placeholderAtPosition == null || placeholderAtPosition.isEmpty()) {
      throw new IllegalStateException("Call hasParameters() first.");
    }
    return placeholderAtPosition.get(i);
  }

  public int getLastPlaceholderPosition() {
    if (placeholderPositions == null || placeholderPositions.isEmpty()) {
      throw new IllegalStateException("Call hasParameters() first.");
    }
    return placeholderPositions.get(placeholderPositions.size() - 1);
  }

  /**
   * Adds a named parameter to this ParameterContext. Once a named Parameter have been added all
   * subsequent parameters must be named. Using named parameters enable reuse of the same parameters
   * in several locations of the SQL text. The parameters only have to be sent to the PostgreSQL
   * backend once per name specified. The values will be sent in the order of the first appearance
   * of their placeholder.
   *
   * @param position in the SQL text where the parser captured the placeholder.
   * @param bindName is the placeholder name captured by the parser.
   * @return 1-indexed position in the order of first appearance of named parameters
   * @throws SQLException if positional and named parameters are mixed.
   */
  public int addNamedParameter(@NonNegative int position, String bindName) throws SQLException {
    checkAndSetBindStyle(BindStyle.NAMED);
    checkAndAddPosition(position);

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
    return placeholderNames != null ? placeholderNames.size() : placeholderCount();
  }

  public List<Integer> getPlaceholderPositions() {
    return placeholderPositions == null ? Collections.emptyList() : placeholderPositions;
  }

  /**
   * @return Returns an unmodifiableList containing captured placeholder names.
   */
  public List<String> getPlaceholderNames() {
    if (placeholderNames == null) {
      throw new IllegalStateException("Call hasNamedParameters() first.");
    }
    return Collections.unmodifiableList(this.placeholderNames);
  }

  private void checkAndSetBindStyle(BindStyle bindStyle) throws SQLException {
    if (this.bindStyle == null) {
      this.bindStyle = bindStyle;
    } else if (this.bindStyle != bindStyle) {
      throw new SQLException("Multiple bind styles cannot be combined. Saw " + this.bindStyle
          + " first but attempting to also use: " + bindStyle);
    }
  }

  private int checkAndAddPosition(@NonNegative int position) throws SQLException {
    if (placeholderPositions == null) {
      placeholderPositions = new ArrayList<>();
    }
    placeholderPositions.add(position);
    if (hasParameters() && position <= getLastPlaceholderPosition()) {
      throw new IllegalArgumentException("Parameters must be processed in increasing order."
          + "position = " + position + ", LastPlaceholderPosition = "
          + getLastPlaceholderPosition());
    }
    return placeholderPositions.size() - 1;
  }
}
