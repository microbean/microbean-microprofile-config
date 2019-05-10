# microBean MicroProfile Config

[![Build Status](https://travis-ci.com/microbean/microbean-microprofile-config.svg?branch=master)](https://travis-ci.com/microbean/microbean-microprofile-config)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.microbean/microbean-microprofile-config/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.microbean/microbean-microprofile-config)

An implementation of version 1.3 of the [MicroProfile Config
specification](https://github.com/eclipse/microprofile-config/tree/master/spec/src/main/asciidoc).

# Features

* Fully MicroProfile Config 1.3 compliant (passes the
  [TCK](https://github.com/eclipse/microprofile-config/tree/master/tck))
* Thread safe
* Conversion can work on `Type`s, not just `Class`es
* `PropertyEditor`s are used as lowest priority `Converter`s when
  available
* `Config` instances may be built without registration
* CDI support permits multiple injected `Config`s based on qualifiers
* Observer method injected `ConfigProperty`-annotated parameters are
  validated, not just field injection points
* No unnecessary dependencies
* Fully documented

