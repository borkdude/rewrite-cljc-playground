(ns ^:no-doc rewrite-clj.impl.parser.token
  (:require [rewrite-clj.node :as node]
            [rewrite-clj.reader :as r])
  #?(:cljs (:import [goog.string StringBuffer])))

;; TODO: these are cljs optimizations
;; the code is less readable, but should work for clj as well.

(defn- join-2 [a b]
  (-> (StringBuffer.) (.append a) (.append b) .toString))

(defn- ^boolean allowed-default? [c]
  false)

(defn- ^boolean allowed-suffix? [c]
  (or (identical? c \')
      (identical? c \:)))



(defn- read-to-boundary
  [#?(:cljs ^not-native reader :clj reader) allowed?]
  (r/read-until
   reader
   #(and (not (allowed? %))
         (r/whitespace-or-boundary? %))))




(defn- read-to-char-boundary
  [#?(:cljs ^not-native reader :clj reader)]
  (let [c (r/next reader)]
    (join-2 c (if (not (identical? c \\))
                (read-to-boundary reader allowed-default?)
                ""))))



(defn- symbol-node
  "Symbols allow for certain boundary characters that have
   to be handled explicitly."
  [#?(:cljs ^not-native reader :clj reader) value value-string]
  (let [suffix (read-to-boundary
                 reader
                 allowed-suffix?)]
    (if (empty? suffix)
      (node/token-node value value-string)
      (let [s (join-2 value-string suffix)]
        (node/token-node
          (r/string->edn s)
          s)))))




(defn parse-token
  "Parse a single token."
  [#?(:cljs ^not-native reader :clj reader)]
  (let [first-char (r/next reader)
        s (join-2 first-char (if (identical? first-char \\)
                               (read-to-char-boundary reader)
                               (read-to-boundary reader allowed-default?)))
        v (r/string->edn s)]
    (if (symbol? v)
      (symbol-node reader v s)
      (node/token-node v s))))
