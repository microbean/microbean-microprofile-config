/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2019 microBean.
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

import java.io.Serializable;

import java.lang.reflect.Type;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.microprofile.config.spi.Converter;

import org.junit.Test;

import javax.enterprise.util.TypeLiteral;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestConverters {

  public TestConverters() {
    super();
  }

  @Test
  public void testGetBozoConversionType() {
    final Type type = new TypeLiteral<Collection<? extends Integer[]>>() {
        private static final long serialVersionUID = 1L;
      }.getType();
    assertEquals(type, Converters.getConversionType(BozoConverter.class));
  }

  @Test
  public void testGetOrdinaryConversionType() {
    assertEquals(String.class, Converters.getConversionType(OrdinaryConverter.class));
  }

  @Test
  public void testSplit() {
    final String text = "true\\,false";
    final String[] parts = ConversionHub.split(text);
    assertNotNull(parts);
    assertEquals(1, parts.length);
    assertEquals("true,false", parts[0]);
  }

  private static interface Bar<Z> {

  }
  
  private static interface Foo<T> extends Converter<T> {

  }
  
  private static interface StupidConverterSubInterface<X, Q> extends Foo<Q>, Bar<X> {

  }

  private static class AbstractBozoConverter<J> implements Bar<J> {

  }
  
  private static final class BozoConverter<B> extends AbstractBozoConverter<B> implements StupidConverterSubInterface<B, Collection<? extends Integer[]>>, Serializable {

    private static final long serialVersionUID = 1L;
    
    @Override
    public final Collection<? extends Integer[]> convert(final String rawValue) {
      return Collections.singleton(new Integer[] { Integer.valueOf(42) });
    }
    
  }

  private static final class OrdinaryConverter implements Converter<String> {

    private static final long serialVersionUID = 1L;
    
    @Override
    public final String convert(final String rawValue) {
      return rawValue;
    }
    
  }
  
}
