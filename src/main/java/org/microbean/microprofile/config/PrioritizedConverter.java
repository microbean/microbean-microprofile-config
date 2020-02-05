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

import java.lang.reflect.Type;

import java.util.Objects;

import org.eclipse.microprofile.config.spi.Converter;

final class PrioritizedConverter<T> implements Converter<T> {

  private static final long serialVersionUID = 1L;
  
  private final Converter<T> delegate;

  private final Type conversionType;

  private final Integer priority;

  PrioritizedConverter(final Converter<T> delegate, final Type conversionType, final int priority) {
    this(delegate, conversionType, Integer.valueOf(priority));
  }
  
  PrioritizedConverter(final Converter<T> delegate, final Type conversionType, final Integer priority) {
    super();
    this.delegate = Objects.requireNonNull(delegate);
    this.conversionType = Objects.requireNonNull(conversionType);
    this.priority = priority;
  }

  final int getPriority() {
    final int returnValue;
    if (this.priority == null) {
      returnValue = 100; // per specification
    } else {
      returnValue = this.priority.intValue();
    }
    return returnValue;
  }

  Type getConversionType() {
    return this.conversionType;
  }
  
  @Override
  public final T convert(final String value) {
    return this.delegate.convert(value);
  }

  @Override
  public int hashCode() {
    int hashCode = 17;

    final Object delegate = this.delegate;
    int c = delegate == null ? 0 : delegate.hashCode();
    hashCode = 37 * hashCode + c;

    final Object conversionType = this.conversionType;
    c = conversionType == null ? 0 : conversionType.hashCode();
    hashCode = 37 * hashCode + c;

    hashCode = 37 * hashCode + this.getPriority();
    
    return hashCode;
  }

  @Override
  public boolean equals(final Object other) {
    if (other == this) {
      return true;
    } else if (other instanceof PrioritizedConverter) {
      final PrioritizedConverter<?> her = (PrioritizedConverter<?>)other;

      if (this.delegate == null) {
        if (her.delegate != null) {
          return false;
        }
      } else if (!this.delegate.equals(her.delegate)) {
        return false;
      }

      if (this.conversionType == null) {
        if (her.conversionType != null) {
          return false;
        }
      } else if (!this.conversionType.equals(her.conversionType)) {
        return false;
      }

      return this.getPriority() == her.getPriority();
    } else {
      return false;
    }
  }
  
}
