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

import org.eclipse.microprofile.config.spi.Converter;

/**
 * A {@link Comparator} that considers only the {@linkplain Converter
 * priority, if any, available from the <code>Converter</code>
 * instances} being compared, in accordance with the minimal
 * requirements of the MicroProfile Config specification as spelled
 * out (only) in the {@linkplain Converter <code>Converter</code> API
 * documentation}.
 *
 * <p><strong>This {@link Comparator} implementation is, and must be,
 * inconsistent with {@link Object#equals(Object) equals()}</strong>,
 * as implied by the same specification.</p>
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #compare(Converter, Converter)
 *
 * @see Converter
 */
public final class ConverterComparator implements Comparator<Converter<?>> {


  /*
   * Static fields.
   */


  /**
   * The sole instance of this class.
   */
  public static final ConverterComparator INSTANCE = new ConverterComparator();


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link ConverterComparator}.
   */
  private ConverterComparator() {
    super();
  }


  /*
   * Instance methods.
   */


  /**
   * Compares two {@link Converter}s, returning {@code -1} if the
   * first has a {@linkplain Converter priority} greater than that of
   * the second and {@code 1} if the second has a {@linkplain
   * Converter priority} greater than that of the first, and {@code 0}
   * in all other cases.
   *
   * <p><strong>This method may return {@code 0} when both {@link
   * Converter}s are not otherwise semantically equal.</strong></p>
   *
   * @param firstConverter the first of two {@link Converter}s; may be
   * {@code null}
   *
   * @param secondConverter the second of two {@link Converter}s; may
   * be {@code null}
   *
   * @return {@code -1} if the first has a {@linkplain Converter
   * priority} greater than that of the second and {@code 1} if the
   * second has a {@linkplain Converter priority} greater than that of
   * the first, and {@code 0} in all other cases
   *
   * @see Converter
   */
  @Override
  public final int compare(final Converter<?> firstConverter, final Converter<?> secondConverter) {
    final int returnValue;
    if (firstConverter == secondConverter) {
      returnValue = 0;
    } else if (firstConverter == null) {
      returnValue = 1;
    } else if (secondConverter == null) {
      returnValue = -1;
    } else {
      final int firstPriority = Converters.getPriority(firstConverter);
      final int secondPriority = Converters.getPriority(secondConverter);
      if (firstPriority > secondPriority) {
        returnValue = -1;
      } else if (firstPriority < secondPriority) {
        returnValue = 1;
      } else {
        returnValue = 0;
      }
    }
    return returnValue;
  }

}
