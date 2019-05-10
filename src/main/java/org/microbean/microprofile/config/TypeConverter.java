/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2018–2019 microBean.
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

/**
 * An interface indicating that implementations might be able to
 * convert {@link String} values to a variety of differently-typed
 * objects.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #convert(String, Type)
 */
public interface TypeConverter {

  /**
   * Converts the supplied {@link String} value to an object of the
   * supplied {@link Type}, and returns it.
   *
   * <p>Implementations of this method are permitted to return {@code
   * null}.</p>
   *
   * @param <T> the type of the return value, assumed to be assignable
   * to references of the supplied type
   *
   * @param rawValue the {@link String} value to convert; may be
   * {@code null}
   *
   * @param type the {@link Type} to which the value should be
   * converted; must not be {@code null}; the type of the return value
   * resulting from invocations of implementations of this method
   * should be assignable to references of this type
   *
   * @return the converted object
   *
   * @exception NullPointerException if {@code type} is {@code null}
   *
   * @exception IllegalArgumentException if conversion could not occur
   * for any reason
   */
  public <T> T convert(final String rawValue, final Type type);
  
}
