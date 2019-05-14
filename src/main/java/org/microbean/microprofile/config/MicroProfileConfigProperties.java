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

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * A {@link Properties} implementation that wraps a {@link Config} and
 * is suitable for {@linkplain System#setProperties(Properties)
 * installing as <code>System</code> properties}.
 *
 * <p><strong>This class intentionally violates several tenets of the
 * {@link Map} interface and {@link Properties} class.</strong>
 * Deviations and oddities are noted below.</p>
 *
 * <ul>
 *
 * <li>The {@link #clear()} method, while supported, cannot, and
 * therefore does not, remove any items from the {@link Config} that
 * is expressed by this class.  It will remove all items manually
 * {@linkplain #put(Object, Object) put} into a {@link
 * MicroProfileConfigProperties} instance directly.</li>
 *
 * <li>Because the MicroProfile Config specification does not say
 * anything about concurrent access to methods like {@link
 * Config#getPropertyNames()}, all iteration methods in this class
 * should be regarded as producing unmodifiable snapshots of a rough
 * estimate of the keys present in a {@link
 * MicroProfileConfigProperties} instance.</li>
 *
 * <li>All iteration return values are immutable and will throw {@link UnsupportedOperationException} where appropriate.</li>
 *
 * <li>Because the MicroProfile Config specification does not say
 * anything about the underlying dynamic nature of the configuration
 * systems that a given {@link Config} abstracts, it is possible, for
 * example, for the {@link #containsKey(Object)} method to return
 * {@code true}, and then for {@link #get(Object)} to be unable to
 * retrieve a value.</li>
 *
 * <li>Because the MicroProfile Config specification does not say
 * anything about what implementations must do with regards to caching
 * or freshness, it is strictly speaking undefined whether fresh or
 * stale results will be retrieved by instances of this class.</li>
 *
 * </ul>
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see Config
 */
public class MicroProfileConfigProperties extends Properties {

  /**
   * The version of this class for {@linkplain Serializable
   * serialization} purposes.
   */
  private static final long serialVersionUID = 1L;

  /**
   * The {@link Config} providing configuration property values.
   *
   * <p>This field is never {@code null}.</p>
   */
  private final Config config;

  /**
   * A flag indicating whether an iteration is in progress; useful to
   * avoid infinite loops when this {@link
   * MicroProfileConfigProperties} is {@linkplain
   * System#setProperties(Properties) installed as <code>System</code>
   * properties}
   */
  // @GuardedBy("this")
  private boolean iterating;

  /**
   * Creates a new {@link MicroProfileConfigProperties} representing
   * the {@link Config} that results from {@link
   * ConfigProvider#getConfig()}.
   *
   * @see ConfigProvider#getConfig()
   */
  public MicroProfileConfigProperties() {
    this(null, null);
  }

  /**
   * Creates a new {@link MicroProfileConfigProperties} wrapping the
   * supplied {@link Config}.
   *
   * @param config the {@link Config} to express; may be {@code null}
   * in which case the return value of {@link
   * ConfigProvider#getConfig()} will be used instead
   *
   * @see ConfigProvider#getConfig()
   */
  public MicroProfileConfigProperties(final Config config) {
    this(config, null);
  }

  /**
   * Creates a new {@link MicroProfileConfigProperties} using the
   * supplied {@link Properties} as its {@linkplain
   * Properties#defaults defaults} and wrapping the {@link Config}
   * that results from {@link ConfigProvider#getConfig()}.
   *
   * @param defaults the default {@link Properties}; may be {@code null}
   *
   * @see Properties#defaults
   *
   * @see ConfigProvider#getConfig()
   */
  public MicroProfileConfigProperties(final Properties defaults) {
    this(null, defaults);
  }

  /**
   * Creates a new {@link MicroProfileConfigProperties} using the
   * supplied {@link Properties} as its {@linkplain
   * Properties#defaults defaults} and wrapping the supplied {@link
   * Config}.
   *
   * @param config the {@link Config} to express; may be {@code null}
   * in which case the return value of {@link
   * ConfigProvider#getConfig()} will be used instead
   *
   * @param defaults the default {@link Properties}; may be {@code
   * null}
   *
   * @see ConfigProvider#getConfig()
   */
  public MicroProfileConfigProperties(final Config config, final Properties defaults) {
    super(defaults);
    this.config = config == null ? ConfigProvider.getConfig() : config;
  }

  @Override
  public synchronized final boolean containsKey(final Object key) {
    boolean returnValue = super.containsKey(key);
    if (!returnValue) {
      final Iterable<?> propertyNames = this.config.getPropertyNames();
      if (propertyNames != null) {
        if (key == null) {
          for (final Object propertyName : propertyNames) {
            if (propertyName == null) {
              returnValue = true;
              break;
            }
          }
        } else {
          for (final Object propertyName : propertyNames) {
            if (key.equals(propertyName)) {
              returnValue = true;
              break;
            }
          }
        }
      }
    }
    return returnValue;
  }

  @Override
  public synchronized final boolean contains(final Object value) {
    return this.containsValue(value);
  }
  
  @Override
  public synchronized final boolean containsValue(final Object value) {
    boolean returnValue = super.containsValue(value);
    if (!super.containsValue(value)) {
      final Iterable<? extends String> propertyNames = this.config.getPropertyNames();
      if (propertyNames == null) {
        returnValue = false;
      } else {
        for (final String propertyName : propertyNames) {
          final Optional<?> propertyValue = this.config.getOptionalValue(propertyName, String.class);
          if (propertyValue != null && propertyValue.isPresent()) {
            returnValue = true;
            break;
          }
        }
      }
    }
    return returnValue;
  }
  
  @Override
  public synchronized final Object get(final Object key) {
    final Object returnValue;
    if (super.containsKey(key)) {
      returnValue = super.get(key);
    } else {
      final Optional<String> configValue = this.config.getOptionalValue(key.toString(), String.class);
      if (configValue == null || !configValue.isPresent()) {
        returnValue = null;
      } else {
        returnValue = configValue.get();
      }
    }
    return returnValue;
  }

  @Override
  public synchronized final boolean isEmpty() {
    boolean returnValue = super.isEmpty();
    if (!returnValue) {
      final Iterable<?> propertyNames = this.config.getPropertyNames();
      if (propertyNames != null) {
        final Iterator<?> iterator = propertyNames.iterator();
        returnValue = iterator != null && iterator.hasNext();
      }
    }
    return returnValue;
  }
  
  @Override
  public synchronized final int size() {
    final int returnValue;
    final Set<?> keys = this.keySet();
    if (keys == null) {
      returnValue = 0;
    } else {
      returnValue = keys.size();
    }
    return returnValue;
  }

  @Override
  public synchronized final Enumeration<Object> keys() {
    return new IteratorEnumeration<>(this.keySet());
  }

  @Override
  public synchronized final Set<Object> keySet() {
    final Set<Object> returnValue;
    if (this.iterating) {
      returnValue = Collections.unmodifiableSet(super.keySet());
    } else {
      this.iterating = true;
      try {
        final Iterable<?> configKeys = this.config.getPropertyNames();
        if (configKeys == null) {
          returnValue = Collections.unmodifiableSet(super.keySet());
        } else {
          final Set<Object> keys = new LinkedHashSet<>();
          final Set<?> superKeys = super.keySet();
          if (superKeys != null && !superKeys.isEmpty()) {
            keys.addAll(superKeys);
          }
          synchronized (configKeys) {
            for (final Object configKey : configKeys) {
              keys.add(configKey);
            }
          }
          returnValue = Collections.unmodifiableSet(keys);
        }
      } finally {
        this.iterating = false;
      }
    }
    return returnValue;
  }

  @Override
  public synchronized final Enumeration<Object> elements() {
    final Enumeration<Object> returnValue;
    if (this.iterating) {
      returnValue = super.elements();
    } else {
      this.iterating = true;
      try {
        final Iterable<?> configKeys = this.config.getPropertyNames();
        if (configKeys == null) {
          returnValue = super.elements();
        } else {
          final Collection<Object> values = new ArrayList<>();
          synchronized (configKeys) {
            for (final Object configKey : configKeys) {
              final Optional<?> configValue = this.config.getOptionalValue(String.valueOf(configKey), String.class);
              if (configValue == null || !configValue.isPresent()) {
                values.add(null);
              } else {
                values.add(configValue.get());
              }
            }
          }
          returnValue = new IteratorEnumeration<>(values);
        }
      } finally {
        this.iterating = false;
      }
    }
    return returnValue;
  }

  @Override
  public synchronized final Collection<Object> values() {
    final Collection<Object> returnValue;
    if (this.iterating) {
      returnValue = Collections.unmodifiableCollection(super.values());
    } else {
      this.iterating = true;
      try {
        final Iterable<?> configKeys = this.config.getPropertyNames();
        if (configKeys == null) {
          returnValue = Collections.unmodifiableCollection(super.values());
        } else {
          final Collection<Object> values = new ArrayList<>();
          synchronized (configKeys) {
            for (final Object configKey : configKeys) {
              final Optional<?> configValue = this.config.getOptionalValue(String.valueOf(configKey), String.class);
              if (configValue == null || !configValue.isPresent()) {
                values.add(null);
              } else {
                values.add(configValue.get());
              }
            }
          }
          returnValue = Collections.unmodifiableCollection(values);
        }
      } finally {
        this.iterating = false;
      }
    }
    return returnValue;
  }

  @Override
  public synchronized final Set<Entry<Object, Object>> entrySet() {
    final Set<Entry<Object, Object>> returnValue;
    final Set<Object> keySet = this.keySet();
    if (keySet == null || keySet.isEmpty()) {
      returnValue = Collections.emptySet();
    } else {
      final Set<Entry<Object, Object>> entrySet = new LinkedHashSet<>();
      for (final Object key : keySet) {
        entrySet.add(new SimpleImmutableEntry<>(key, this.get(key)));
      }
      returnValue = Collections.unmodifiableSet(entrySet);
    }
    return returnValue;
  }

  private static final class IteratorEnumeration<T> implements Enumeration<T>, Iterator<T> {

    private final Iterator<? extends T> iterator;

    private final Enumeration<? extends T> enumeration;

    private IteratorEnumeration(final Iterable<? extends T> iterable) {
      this(iterable == null ? (Iterator<? extends T>)null : iterable.iterator());
    }
    
    private IteratorEnumeration(final Iterator<? extends T> iterator) {
      super();
      this.iterator = iterator;
      this.enumeration = null;
    }

    private IteratorEnumeration(final Enumeration<? extends T> enumeration) {
      super();
      this.iterator = null;
      this.enumeration = enumeration;
    }

    @Override
    public final boolean hasMoreElements() {
      if (this.enumeration == null) {
        return this.iterator != null && this.iterator.hasNext();
      } else {
        return this.enumeration.hasMoreElements();
      }
    }

    @Override
    public final T nextElement() {
      if (this.enumeration == null) {
        if (this.iterator == null) {
          throw new NoSuchElementException();
        }
        return this.iterator.next();
      } else {
        return this.enumeration.nextElement();
      }
    }
    
    @Override
    public final boolean hasNext() {
      if (this.iterator == null) {
        return this.enumeration != null && this.enumeration.hasMoreElements();
      } else {
        return this.iterator.hasNext();
      }
    }

    @Override
    public final T next() {
      if (this.iterator == null) {
        if (this.enumeration == null) {
          throw new NoSuchElementException();
        }
        return this.enumeration.nextElement();
      } else {
        return this.iterator.next();
      }
    }

    @Override
    public void remove() {
      if (this.iterator == null) {
        throw new UnsupportedOperationException();
      } else {
        this.iterator.remove();
      }
    }
    
  }

  /**
   * Installs an instance of {@link MicroProfileConfigProperties}
   * somewhat irrevocably as {@linkplain
   * System#setProperties(Properties) the system properties}, with the
   * current {@linkplain System#getProperties() system properties} as
   * its defaults.
   *
   * @exception SecurityException if permission is not granted
   */
  public static final void installAsSystemProperties() {
    AccessController.doPrivileged(new PrivilegedAction<Void>() {
        @Override
        public final Void run() {
          System.setProperties(new MicroProfileConfigProperties(System.getProperties()));
          return null;
        }
      });
  }
  
}
