(ns ^:no-doc rewrite-clj.impl.interop
  #?(:cljs (:require [goog.string :as gstring]
                     goog.string.format)))

(defn simple-format
  "Interop version of string format
  Note that there a big differences between Java's format and Google Closure's format - we don't address them.
  %d and %s are known to work in both."
  [template & args]
  #?(:clj (apply format template args)
     :cljs (apply gstring/format template args)))

(defn str->int
  [s]
  #?(:clj (Long/parseLong s)
     :cljs (js/parseInt s)))

(defn int->str
  [n base]
  #?(:clj (.toString (biginteger n) base)
     :cljs (.toString n base)))

(defn min-int[]
  #?(:clj  Long/MIN_VALUE
     :cljs Number.MIN_SAFE_INTEGER))

(defn max-int[]
  #?(:clj Long/MAX_VALUE
     :cljs Number.MAX_SAFE_INTEGER))

(defn clojure-whitespace?
  [^java.lang.Character c]
  #?(:clj (and c (or (= c \,) (Character/isWhitespace c)))
     :cljs (and c (< -1 (.indexOf #js [\return \newline \tab \space ","] c)))))

(defn meta-available?
  [data]
  #?(:clj (instance? clojure.lang.IMeta data)
     :cljs (implements? IWithMeta data)))
