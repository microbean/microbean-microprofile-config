/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2018 microBean.
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

final class ConfigSourceComparator implements Comparator<ConfigSource> {

  static final ConfigSourceComparator INSTANCE = new ConfigSourceComparator();
  
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
