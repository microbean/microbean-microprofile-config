/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2018­2020 microBean™.
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

import java.lang.ref.WeakReference;

import java.io.IOException;

import java.lang.reflect.Type;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.Config;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;

class ConfigBuilder implements org.eclipse.microprofile.config.spi.ConfigBuilder {

  private volatile boolean addDefaultSources;

  private volatile boolean addDiscoveredSources;

  private volatile boolean addDiscoveredConverters;

  private final Collection<Converter<?>> converters;

  private final Collection<ConfigSource> sources;

  private volatile ClassLoader classLoader;

  ConfigBuilder() {
    super();
    this.converters = new LinkedList<>();
    this.sources = new LinkedList<>();
    this.classLoader = AccessController.doPrivileged((PrivilegedAction<ClassLoader>)() -> Thread.currentThread().getContextClassLoader());
  }

  @Override
  public final ConfigBuilder addDefaultSources() {
    this.addDefaultSources = true;
    return this;
  }

  @Override
  public final ConfigBuilder addDiscoveredSources() {
    this.addDiscoveredSources = true;
    return this;
  }

  @Override
  public final ConfigBuilder addDiscoveredConverters() {
    this.addDiscoveredConverters = true;
    return this;
  }

  @Override
  public final Config build() {
    List<ConfigSource> sources = null;

    if (this.addDefaultSources) {
      sources = new LinkedList<>();
      Collection<? extends ConfigSource> defaultConfigSources = null;
      try {
        defaultConfigSources = org.microbean.microprofile.config.Config.getDefaultConfigSources(this.classLoader);
      } catch (final IOException ioException) {
        throw new RuntimeException(ioException.getMessage(), ioException);
      }
      assert defaultConfigSources != null;
      sources.addAll(defaultConfigSources);
    }

    ClassLoader classLoader = null;

    if (this.addDiscoveredSources) {
      classLoader = this.classLoader;
      if (sources == null) {
        sources = new LinkedList<>();
      }
      sources.addAll(org.microbean.microprofile.config.Config.getDiscoveredConfigSources(classLoader));
    }

    synchronized (this.sources) {
      if (!this.sources.isEmpty()) {
        if (sources == null) {
          sources = new LinkedList<>();
        }
        sources.addAll(this.sources);
      }
    }
    if (sources != null) {
      Collections.sort(sources, ConfigSourceComparator.INSTANCE);
    }

    final Map<Type, Converter<?>> converters = new HashMap<>();
    if (this.addDiscoveredConverters) {
      if (classLoader == null) {
        classLoader = this.classLoader;
      }
      converters.putAll(ConversionHub.getDiscoveredConverters(classLoader));
    }

    synchronized (this.converters) {
      if (!this.converters.isEmpty()) {
        for (final Converter<?> converter : this.converters) {
          assert converter != null;
          final Type conversionType = Converters.getConversionType(converter);
          if (conversionType == null) {
            throw new IllegalStateException("Could not determine the conversion type for converter: " + converter);
          }
          final int priority = Converters.getPriority(converter);
          final int oldPriority;
          final Converter<?> existingConverter = converters.get(conversionType);
          if (existingConverter == null) {
            oldPriority = Integer.MIN_VALUE;
          } else {
            oldPriority = Converters.getPriority(existingConverter);
          }
          if (priority > oldPriority) {
            converters.put(conversionType, converter);
          }
        }
      }
    }

    return new org.microbean.microprofile.config.Config(sources, new ConversionHub(converters));
  }

  @Override
  public final <T> ConfigBuilder withConverter(final Class<T> type, final int ordinal, final Converter<T> converter) {
    return this.withConverter((Type)type, ordinal, converter);
  }

  public final <T> ConfigBuilder withConverter(final Type type, final int ordinal, final Converter<T> converter) {
    // The specification says nothing about null arguments.
    if (type != null && converter != null) {
      synchronized (this.converters) {
        this.converters.add(new PrioritizedConverter<>(converter, type, ordinal));
      }
    }
    return this;
  }

  @Override
  public final ConfigBuilder withConverters(final Converter<?>... converters) {
    // The specification says nothing about null arguments.
    // The specification says nothing about concurrency.
    if (converters != null) {
      synchronized (this.converters) {
        if (converters.length > 0) {
          for (final Converter<?> converter : converters) {
            if (converter != null) {
              this.converters.add(converter);
            }
          }
        }
      }
    }
    return this;
  }

  @Override
  public final ConfigBuilder withSources(final ConfigSource... sources) {
    // The specification says nothing about null arguments.
    // The specification says nothing about concurrency.
    if (sources != null) {
      synchronized (this.sources) {
        for (final ConfigSource configSource : sources) {
          if (configSource != null) {
            this.sources.add(configSource);
          }
        }
      }
    }
    return this;
  }

  @Override
  public final ConfigBuilder forClassLoader(final ClassLoader classLoader) {
    if (classLoader == null) {
      // See https://github.com/eclipse/microprofile-config/issues/426
      this.classLoader = AccessController.doPrivileged((PrivilegedAction<ClassLoader>)() -> Thread.currentThread().getContextClassLoader());
    } else {
      this.classLoader = classLoader;
    }
    return this;
  }

}
