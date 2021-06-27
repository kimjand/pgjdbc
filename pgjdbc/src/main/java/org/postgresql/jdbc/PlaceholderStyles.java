/*
 * Copyright (c) 2016, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.jdbc;

/**
 *
 * @see org.postgresql.PGProperty#PLACEHOLDER_STYLES
 */
public enum PlaceholderStyles {
  ANY("any"),
  NAMED("named"),
  NATIVE("native"),
  NONE("none");

  private final String value;

  PlaceholderStyles(String value) {
    this.value = value;
  }

  public static PlaceholderStyles of(String mode) {
    for (PlaceholderStyles placeholderStyles : values()) {
      if (placeholderStyles.value.equals(mode)) {
        return placeholderStyles;
      }
    }
    return NONE;
  }

  public String value() {
    return value;
  }
}
