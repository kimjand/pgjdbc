/*
 * Copyright (c) 2020, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.core;

import org.postgresql.jdbc.PlaceholderStyles;
import org.postgresql.util.internal.Nullness;

import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link org.postgresql.core.Parser} stores information about placeholder occurrences in
 * ParameterContext. In case of standard JDBC placeholders {@code '?'} the position in the SQL text
 * is recorded. For named placeholders {@code ":paramName"} the name is recorded as well as the
 * position. {@code PgPreparedStatement} can then use the name to look up the parameter
 * corresponding index. Native placeholders of the form {@code "$number"} are also supported. These
 * recorded values are also used by toString() methods to provide a human-readable representation of
 * the SQL text.
 */
public class ParameterContext {

  private final PlaceholderStyles allowedPlaceholderStyles;

  public ParameterContext(PlaceholderStyles allowedPlaceholderStyles) {
    this.allowedPlaceholderStyles = allowedPlaceholderStyles;
  }

  public PlaceholderStyles getAllowedPlaceholderStyles() {
    return this.allowedPlaceholderStyles;
  }

  public enum BindStyle {
    POSITIONAL(false, "?"), NAMED(true, ":"), NATIVE(true, "$");

    public final boolean isNamedParameter;
    public final String prefix;

    BindStyle(boolean isNamedParameter, String prefix) {
      this.isNamedParameter = isNamedParameter;
      this.prefix = prefix;
    }
  }

  /**
   * EMPTY_CONTEXT is immutable. Calling the add-methods will result in
   * UnsupportedOperationException being thrown.
   */
  public static final ParameterContext EMPTY_CONTEXT = new ParameterContext(PlaceholderStyles.ANY) {
    @Override
    public int addPositionalParameter(int position) throws SQLException {
      throw new UnsupportedOperationException();
    }

    @Override
    public int addNamedParameter(int position, BindStyle bindStyle, String bindName)
        throws SQLException {
      throw new UnsupportedOperationException();
    }
  };
  static final String uninitializedName = "<UNINITIALIZED>";
  private @Nullable BindStyle bindStyle = null;
  private @Nullable List<Integer> placeholderPositions = null;
  private @Nullable List<String> placeholderNames = null;
  private @Nullable Map<String, Integer> placeholderNameToNativePositionMap = null;
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

  public BindStyle getBindStyle() {
    if (bindStyle == null) {
      throw new IllegalStateException(
          "No bindStyle was registered, did you call hasNamedParameters() first?");
    }
    return this.bindStyle;
  }

  /**
   * @param placeholderName name of the placeholder to lookup
   * @return The backend parameter position corresponding to this name
   */
  public @Nullable Integer getPlaceholderIndex(@NonNull String placeholderName) {
    switch (this.getBindStyle()) {
      case NAMED:
        if (placeholderNameToNativePositionMap == null) {
          return null;
        }
        break;

      case NATIVE:
        if (placeholderNameToNativePositionMap == null) {
          placeholderNameToNativePositionMap = new HashMap<>(placeholderCount());
          for (int i = 0; i < placeholderCount(); i++) {
            Nullness.castNonNull(placeholderNameToNativePositionMap).put(NativeQuery.bindName(i + 1), i);
          }
        }
        break;

      default:
        throw new IllegalArgumentException(
            "bindStyle " + bindStyle + " does not support getPlaceholderIndex");
    }
    return Nullness.castNonNull(placeholderNameToNativePositionMap).get(placeholderName);
  }

  /**
   * @param index 0-indexed position in the order of first appearance
   * @return The name of the placeholder at this backend parameter position
   */
  public String getPlaceholderName(@NonNull Integer index) {
    if (!this.hasNamedParameters()) {
      throw new IllegalStateException(
          "No placeholder names are available, did you call hasParameters() first?");
    }
    return Nullness.castNonNull(placeholderNames).get(index);
  }

  public String getPlaceholderNameForToString(@NonNull Integer index) {
    if (this.getBindStyle() == BindStyle.NAMED) {
      return BindStyle.NAMED.prefix + this.getPlaceholderName(index);
    }

    return this.getPlaceholderName(index);
  }

  /**
   * @param i 0-indexed position in the order of appearance
   * @return The position of the placeholder in the SQL text for this placeholder index
   */
  public int getPlaceholderPosition(@NonNegative int i) {
    if (placeholderPositions == null) {
      throw new IllegalStateException(
          "No placeholder occurrences are available, did you call hasParameters() first?");
    }
    return placeholderPositions.get(i);
  }

  /**
   * @param i 0-indexed position in the order of appearance
   * @return The position of the placeholder in the order of first appearance of each placeholder
   */
  public int getPlaceholderAtPosition(@NonNegative int i) {
    if (placeholderAtPosition == null || placeholderAtPosition.isEmpty()) {
      throw new IllegalStateException(
          "No placeholder positions are available, did you call hasParameters() first?");
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
   * @param position  in the SQL text where the parser captured the placeholder.
   * @param bindStyle is the bindStyle to be used for this parameter, styles can not be mixed in a
   *                  statement.
   * @param bindName  is the name to be used when binding a value for the parameter represented by
   *                  this placeholder.
   * @return 1-indexed position in the order of first appearance of named parameters
   * @throws SQLException if positional and named parameters are mixed.
   */
  public int addNamedParameter(@NonNegative int position, @NonNull BindStyle bindStyle,
      @NonNull String bindName) throws SQLException {
    if (!bindStyle.isNamedParameter) {
      throw new IllegalArgumentException(
          "bindStyle " + bindStyle + " is not not a valid option for addNamedParameter");
    }
    checkAndSetBindStyle(bindStyle);
    checkAndAddPosition(position);

    if (placeholderNames == null) {
      placeholderNames = new ArrayList<>();
    }

    int bindIndex;

    if (bindStyle == BindStyle.NAMED) {
      if (placeholderNameToNativePositionMap == null) {
        placeholderNameToNativePositionMap = new HashMap<>();
      }
      bindIndex = placeholderNameToNativePositionMap.computeIfAbsent(bindName, f -> {
        // placeholderNames was initialized at line 174
        int newIndex = Nullness.castNonNull(placeholderNames).size();
        placeholderNames.add(bindName);
        return newIndex;
      });
    } else if (bindStyle == BindStyle.NATIVE) {
      bindIndex = Integer.parseInt(bindName.substring(1)) - 1;
      while (placeholderNames.size() <= bindIndex) {
        placeholderNames.add(ParameterContext.uninitializedName);
      }
      placeholderNames.set(bindIndex, bindName);
    } else {
      throw new IllegalArgumentException(
          "bindStyle " + bindStyle + " is not a valid option for addNamedParameter");
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
    if (hasParameters() && position <= getLastPlaceholderPosition()) {
      throw new IllegalArgumentException("Parameters must be processed in increasing order."
          + "position = " + position + ", LastPlaceholderPosition = "
          + getLastPlaceholderPosition());
    }
    if (placeholderPositions == null) {
      placeholderPositions = new ArrayList<>();
    }
    placeholderPositions.add(position);
    return placeholderPositions.size() - 1;
  }
}
