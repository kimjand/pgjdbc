/*
 * Copyright (c) 2020, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.core;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ParameterContext {
  public static final ParameterContext EMPTY_CONTEXT = new ParameterContext();
  private @Nullable BindStyle bindStyle = null;
  private @Nullable List<Integer> placeholderPositions = null;
  private @Nullable List<String> placeholderNames = null;
  private @Nullable List<Integer> placeholderAtPosition = null;

  public static ParameterContext buildNamed(List<Integer> placeholderPositions,
      List<String> placeholderNames) throws SQLException {
    final ParameterContext ctx = new ParameterContext();
    assert placeholderPositions.size() == placeholderNames.size();
    for (int i = 0; i < placeholderPositions.size(); i++) {
      ctx.addNamedParameter(placeholderPositions.get(i), placeholderNames.get(i));
    }

    return ctx;
  }

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

    if (placeholderAtPosition == null) {
      placeholderAtPosition = new ArrayList<>();
    }

    placeholderPositions.add(position);
    int bindIndex = placeholderPositions.size() - 1;
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
    assert placeholderNames != null;
    return placeholderNames.get(i);
  }

  /**
   * @param i 0-indexed position in the order of appearance
   * @return The position of the placeholder in the SQL text for this placeholder index
   */
  public int getPlaceholderPosition(int i) {
    assert placeholderPositions != null;
    return placeholderPositions.get(i);
  }

  /**
   * @param i 0-indexed position in the order of appearance
   * @return The position of the placeholder in the order of first appearance of each placeholder
   */
  public int getPlaceholderAtPosition(int i) {
    assert placeholderAtPosition != null;
    return placeholderAtPosition.get(i);
  }

  public int getLatestPlaceholderPosition() {
    assert placeholderPositions != null;
    return placeholderPositions.get(placeholderPositions.size() - 1);
  }

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

    if (placeholderAtPosition == null) {
      placeholderAtPosition = new ArrayList<>();
    }

    if (placeholderNames == null) {
      placeholderNames = new ArrayList<>();
    }

    placeholderPositions.add(position);
    int bindIndex = placeholderNames.indexOf(bindName);
    if (bindIndex == -1) {
      bindIndex = placeholderNames.size();
      placeholderNames.add(bindName);
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
    assert placeholderPositions != null;
    return placeholderPositions;
  }

  enum BindStyle {
    POSITIONAL, NAMED
  }
}
