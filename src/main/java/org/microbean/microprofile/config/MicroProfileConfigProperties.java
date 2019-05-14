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
 * <h2>Thread Safety</h2>
 *
 * <p>This class is safe for concurrent use by multiple threads.</p>
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

  /**
   * Returns {@code true} if this {@link MicroProfileConfigProperties}
   * {@linkplain Properties#containsKey(Object) directly contains} the
   * supplied {@code key}, or if the supplied {@code key} is a {@link
   * String} and is contained in the {@link Set} of configuration
   * property names returned by the {@link Config#getPropertyNames()}
   * method.
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple
   * threads.</p>
   *
   * @param key the key to seek; may be {@code null}
   *
   * @return {@code true} if this {@link MicroProfileConfigProperties}
   * {@linkplain Properties#containsKey(Object) directly contains} the
   * supplied {@code key}, or if the supplied {@code key} is a {@link
   * String} and is contained in the {@link Set} of configuration
   * property names returned by the {@link Config#getPropertyNames()}
   * method; {@code false} otherwise
   */
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

  /**
   * Invokes the {@link #containsValue(Object)} method with the
   * supplied {@link Object} and returns the result.
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple
   * threads.</p>
   *
   * @param value the value to seek; may be {@code null}
   *
   * @return {@code true} if the {@link #containsValue(Object)} method
   * returns {@code true}; {@code false} otherwise
   *
   * @see #containsValue(Object)
   */
  @Override
  public synchronized final boolean contains(final Object value) {
    return this.containsValue(value);
  }

  /**
   * Returns {@code true} if this {@link MicroProfileConfigProperties}
   * contains the supplied value {@link Object}.
   *
   * <p>First the {@link Properties#containsValue(Object)} method is
   * invoked with the supplied {@link Object}.  If that returns {@code
   * true}, then {@code true} is returned.</p>
   *
   * <p>Next, {@linkplain Config#getPropertyNames() all known property
   * names in the <code>Config</code> wrapped by this
   * <code>MicroProfileConfigProperties</code>} are acquired.  This
   * set is iterated over and {@link Config#getOptionalValue(String,
   * Class)} is called for each one.  If the resulting {@link
   * Optional} {@linkplain Optional#isPresent() is present}, then
   * {@code true} is returned.</p>
   *
   * <p>In all other cases {@code false} is returned.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple
   * threads.</p>
   *
   * @param value the value to seek; may be {@code null}
   *
   * @return {@code true} if this {@link MicroProfileConfigProperties}
   * contains the supplied value; {@code false} otherwise
   *
   * @see Config#getPropertyNames()
   *
   * @see Properties#containsValue(Object)
   */
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

  /**
   * Returns the value indexed under the supplied {@code key}, or
   * {@code null} if the value does not exist.  Note that a {@code
   * null} return value could result from a key's being explicitly
   * mapped to {@code null}, or from a key's absence.
   *
   * <p>This implementation first calls {@link
   * Properties#containsKey(Object)} with the supplied {@code key}.  If
   * that method invocation returns {@code true}, then the {@link
   * Properties#get(Object)} method is invoked and its result is
   * returned.</p>
   *
   * <p>Otherwise, the {@link Config#getOptionalValue(String, Class)}
   * method is called and its resulting {@link Optional} result
   * {@linkplain Optional#get() is acquired} and returned, or, if it
   * is {@linkplain Optional#isPresent() not present}, {@code null} is
   * returned.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple
   * threads.</p>
   *
   * @param key the key of the value to return; may be {@code null}
   *
   * @return an appropriate value, or {@code null}
   *
   * @see Properties#containsKey(Object)
   *
   * @see Properties#get(Object)
   *
   * @see Config#getOptionalValue(String, Class)
   */
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

  /**
   * Returns {@code true} if this {@link MicroProfileConfigProperties}
   * is empty.  In all normal cases, this method will return {@code
   * false}, since normally {@link Config} instances expose at least
   * one configuration property value.
   *
   * <p>This implementation calls the {@link Properties#isEmpty()}
   * method.  If that method returns {@code false}, then {@code false}
   * is returned.</p>
   *
   * <p>Otherwise this method calls the {@link
   * Config#getPropertyNames()} method, calls {@link
   * Iterable#iterator()} on the resulting {@link Iterable}, and, if
   * it is non-{@code null}, calls the {@link Iterator#hasNext()}
   * method on it, returning its result.</p>
   *
   * <p>In all other cases this method returns {@code true}.</p>
   *
   * <p>This method is a much faster way of checking if this {@link
   * MicroProfileConfigProperties}' size is {@code 0}.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple
   * threads.</p>
   *
   * @return {@code true}, rarely, if this {@link
   * MicroProfileConfigProperties} is empty; {@code false} otherwise
   *
   * @see Properties#isEmpty()
   *
   * @see Config#getPropertyNames()
   */
  @Override
  public synchronized final boolean isEmpty() {
    boolean returnValue = super.isEmpty();
    if (returnValue) {
      final Iterable<?> propertyNames = this.config.getPropertyNames();
      if (propertyNames != null) {
        final Iterator<?> iterator = propertyNames.iterator();
        returnValue = iterator != null && iterator.hasNext();
      }
    }
    return returnValue;
  }

  /**
   * Returns the size of this {@link MicroProfileConfigProperties} as
   * expressed by the size of its {@linkplain #keySet() key set}.
   *
   * <p>This method returns {@code int}s that are greater than or equal to zero.</p>
   *
   * <p>This method rarely returns {@code 0} given the fact that a
   * {@link Config} normally expresses at least one configuration
   * property value.</p>
   *
   * <p>Use the {@link #isEmpty()} method for a much, much faster way
   * to check for a size of {@code 0}.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple
   * threads.</p>
   *
   * @return the size of this {@link MicroProfileConfigProperties}
   *
   * @see #keySet()
   *
   * @see #isEmpty()
   */
  @Override
  public synchronized final int size() {
    final int returnValue;
    final Set<?> keySet = this.keySet();
    if (keySet == null) {
      returnValue = 0;
    } else {
      returnValue = keySet.size();
    }
    return returnValue;
  }

  /**
   * Invokes the {@link #keySet()} method and returns its return
   * value.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple
   * threads.</p>
   *
   * @return the result of invoking the {@link #keySet()} method.
   *
   * @see #keySet()
   */
  @Override
  public synchronized final Enumeration<Object> keys() {
    return new IteratorEnumeration<>(this.keySet());
  }

  /**
   * Returns a non-{@code null}, {@linkplain
   * Collections#unmodifiableSet(Set) immutable <code>Set</code>} of
   * {@link Object}s that contains the keys stored directly by this
   * {@link MicroProfileConfigProperties} or that are contained in the
   * return value of a {@link Config#getPropertyNames()} invocation.
   *
   * <p>The {@link Set} of {@link Object}s returned by this method is
   * a disconnected snapshot in time of the keys that were thought to
   * be in this {@link MicroProfileConfigProperties} at the time the
   * snapshot was constructed.  Changes to this {@link
   * MicroProfileConfigProperties} are not reflected in the {@link
   * Set}.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple
   * threads.</p>
   *
   * <p>The {@link Set} returned by this method is safe for concurrent
   * use by multiple threads.</p>
   *
   * @return a non-{@code null}, {@linkplain
   * Collections#unmodifiableSet(Set) immutable <code>Set</code>} of
   * {@link Object}s that contains the keys stored directly by this
   * {@link MicroProfileConfigProperties} or that are contained in the
   * return value of a {@link Config#getPropertyNames()} invocation
   *
   * @see Properties#keySet()
   *
   * @see Config#getPropertyNames()
   */
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

  /**
   * Returns a non-{@code null} {@link Enumeration} constructed atop
   * the {@link Collection#iterator() <code>Iterator</code> supplied
   * by the <code>Collection</code>} returned by an invocation of this
   * {@link MicroProfileConfigProperties}' {@link #values()} method.
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple
   * threads.</p>
   *
   * @return a non-{@code null} {@link Enumeration} over the values
   * directly contained by this {@link MicroProfileConfigProperties}
   * or indirectly accessible via calls to {@link
   * Config#getOptionalValue(String, Class)}
   *
   * @see #values()
   *
   * @see Config#getPropertyNames()
   *
   * @see Config#getOptionalValue(String, Class)
   */
  @Override
  public synchronized final Enumeration<Object> elements() {
    return new IteratorEnumeration<>(this.values());
  }

  /**
   * Returns a non-{@code null}, {@linkplain
   * Collections#unmodifiableCollection(Collection) immutable
   * <code>Collection</code>} of this {@link
   * MicroProfileConfigProperties}' values.
   *
   * <p>The values returned are those stored directly (via calls to
   * {@link #put(Object, Object)}, for example) or stored implicitly
   * as configuration values accessible via calls to {@link
   * Config#getOptionalValue(String, Class)}.</p>
   *
   * <p>Changes in this {@link MicroProfileConfigProperties} are not
   * reflected in the returned {@link Collection}.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple
   * threads.</p>
   *
   * @return a non-{@code null}, {@linkplain
   * Collections#unmodifiableCollection(Collection) immutable
   * <code>Collection</code>} of this {@link
   * MicroProfileConfigProperties}' values
   *
   * @see Config#getPropertyNames()
   *
   * @see Config#getOptionalValue(String, Class)
   */
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
          final Collection<Object> values = new ArrayList<>(super.values());
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

  /**
   * Returns a non-{@code null} {@linkplain
   * Collections#unmodifiableSet(Set) immutable <code>Set</code>} of
   * {@linkplain SimpleImmutableEntry immutable <code>Entry</code>}
   * instances representing this {@link MicroProfileConfigProperties}'
   * entries.
   *
   * <p>The entries returned are those that result from calls to
   * {@link #put(Object, Object)} and similar methods, and from
   * configuration property values accessible via calls to {@link
   * Config#getOptionalValue(String, Class)}.</p>
   *
   * <p>This method calls the {@link #keySet()} and {@link
   * #get(Object)} methods.
   *
   * <p>Iteration order of the returned {@link Set} is not defined,
   * with the exception that entries stored directly come at the head
   * of the returned {@link Set}.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple
   * threads.</p>
   *
   * @return a non-{@code null} {@linkplain
   * Collections#unmodifiableSet(Set) immutable <code>Set</code>} of
   * {@linkplain SimpleImmutableEntry immutable <code>Entry</code>}
   * instances representing this {@link MicroProfileConfigProperties}'
   * entries
   *
   * @see Config#getPropertyNames()
   *
   * @see Config#getOptionalValue(String, Class)
   */
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

  /**
   * Installs an instance of {@link MicroProfileConfigProperties}
   * somewhat irrevocably as {@linkplain
   * System#setProperties(Properties) the system properties}, with the
   * current {@linkplain System#getProperties() system properties} as
   * its defaults.
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple
   * threads.</p>
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


  /*
   * Inner and nested classes.
   */
  

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
  
}
