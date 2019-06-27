/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2018–2019 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.microprofile.config;

import java.util.Comparator;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * A {@link Comparator} of {@link ConfigSource}s that considers only
 * their {@linkplain ConfigSource#getOrdinal() ordinals} and
 * {@linkplain ConfigSource#getName() names} in accordance with the
 * minimal requirements of the MicroProfile Config specification as
 * spelled out (only) in the {@linkplain ConfigSource
 * <code>ConfigSource</code> API documentation}.
 *
 * <p><strong>This {@link Comparator} implementation is, and must be,
 * inconsistent with {@link Object#equals(Object) equals()}</strong>,
 * as implied by the same specification.</p>
 *
 * <p>Note that the requirement to use a {@link ConfigSource}'s
 * {@linkplain ConfigSource#getName() name} as a secondary sorting key
 * is defined only in the {@linkplain ConfigSource#getOrdinal()
 * javadocs of the <code>ConfigSource.getOrdinal()</code> method}.</p>
 *
 * <p>Note further that there are no guidelines or requirements about
 * what format a {@link ConfigSource}'s {@linkplain
 * ConfigSource#getName() name} must take, so in effect the ordering
 * of two {@link ConfigSource}s that have a common {@linkplain
 * ConfigSource#getOrdinal() ordinal} is undefined.</p>
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #compare(ConfigSource, ConfigSource)
 *
 * @see ConfigSource
 *
 * @see ConfigSource#getOrdinal()
 *
 * @see ConfigSource#getName()
 */
public final class ConfigSourceComparator implements Comparator<ConfigSource> {


  /*
   * Static fields.
   */

  
  /**
   * The sole instance of this class.
   */
  public static final ConfigSourceComparator INSTANCE = new ConfigSourceComparator();


  /*
   * Constructors.
   */  
  

  /**
   * Creates a new {@link ConfigSourceComparator}.
   */
  private ConfigSourceComparator() {
    super();
  }


  /*
   * Instance methods.
   */
  

  /**
   * Compares two {@link ConfigSource}s, returning {@code -1} if the
   * first has an {@linkplain ConfigSource#getOrdinal() ordinal}
   * greater than that of the second and {@code 1} if the second has
   * an {@linkplain ConfigSource#getOrdinal() ordinal} greater than
   * that of the first, and then the result of invoking {@link
   * String#compareTo(String)} on the return values of the {@link
   * ConfigSource}s' respective {@link ConfigSource#getName()}
   * methods.
   *
   * <p><strong>This method may return {@code 0} when both {@link
   * ConfigSource}s are not otherwise semantically equal.</strong></p>
   *
   * @param firstConfigSource the first of two {@link ConfigSource}s;
   * may be {@code null}
   *
   * @param secondConfigSource the second of two {@link
   * ConfigSource}s; may be {@code null}
   *
   * @return {@code -1} if the first {@link ConfigSource} has an
   * {@linkplain ConfigSource#getOrdinal() ordinal} greater than that
   * of the second, {@code 1} if the second {@link ConfigSource} has
   * an {@linkplain ConfigSource#getOrdinal() ordinal} greater than
   * that of the first, and the result of invoking {@link
   * String#compareTo(String)} on the return values of the {@link
   * ConfigSource}s' respective {@link ConfigSource#getName()} methods
   * otherwise
   *
   * @see ConfigSource
   *
   * @see ConfigSource#getName()
   *
   * @see ConfigSource#getOrdinal()
   */
  @Override
  public final int compare(final ConfigSource firstConfigSource, final ConfigSource secondConfigSource) {
    final int returnValue;
    if (firstConfigSource == secondConfigSource) {
      returnValue = 0;
    } else if (firstConfigSource == null) {
      returnValue = 1;
    } else if (secondConfigSource == null) {
      returnValue = -1;
    } else {
      final int firstOrdinal = firstConfigSource.getOrdinal();
      final int secondOrdinal = secondConfigSource.getOrdinal();
      if (firstOrdinal > secondOrdinal) {
        returnValue = -1;
      } else if (firstOrdinal < secondOrdinal) {
        returnValue = 1;
      } else {
        final String firstName = firstConfigSource.getName();
        String secondName = secondConfigSource.getName();
        if (firstName == null) {
          if (secondName == null) {
            returnValue = 0;
          } else {
            returnValue = 1;
          }
        } else if (secondName == null) {
          returnValue = -1;
        } else {
          returnValue = firstName.compareTo(secondName);
        }
      }
    }
    return returnValue;
  }

}
