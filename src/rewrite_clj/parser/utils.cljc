(ns ^:no-doc rewrite-clj.parser.utils
  (:require [clojure.tools.reader.reader-types :as r]
            [rewrite-clj.interop :as interop]))

(defn whitespace?
  "Check if a given character is a whitespace."
  [^java.lang.Character c]
  (interop/clojure-whitespace? c))

(defn linebreak?
  "Check if a given character is a linebreak."
  [^java.lang.Character c]
  (and c (or (= c \newline) (= c \return))))

(defn space?
  "Check if a given character is a non-linebreak whitespace."
  [^java.lang.Character c]
  (and (not (linebreak? c)) (whitespace? c)))

(defn ignore
  "Ignore next character of Reader."
  [#?(:cljs ^not-native reader :clj reader)]
  (r/read-char reader)
  nil)

(defn throw-reader
  [#?(:cljs ^not-native reader :clj reader) & msg]
  (let [c (r/get-column-number reader)
        l (r/get-line-number reader)]
    (throw (ex-info
            (str (apply str msg) " [at line " l ", column " c "]") {}))))

(defn read-eol
  [#?(:cljs ^not-native reader :clj reader)]
  (loop [char-seq []]
    (if-let [c (r/read-char reader)]
      (if (linebreak? c)
        (apply str (conj char-seq c))
        (recur (conj char-seq c)))
      (apply str char-seq))))
