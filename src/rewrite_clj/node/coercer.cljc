(ns ^:no-doc rewrite-clj.node.coercer
  (:require
   #?@(:clj
       [[rewrite-clj.node
         comment forms integer keyword quote string uneval
         [meta :refer [meta-node]]
         [protocols :as node :refer [NodeCoerceable coerce]]
         [reader-macro :refer [reader-macro-node var-node]]
         [seq :refer [vector-node list-node set-node map-node]]
         [token :refer [token-node]]
         [whitespace :as ws]]]
       :cljs
       [[clojure.string :as string]
        [rewrite-clj.node.comment :refer [CommentNode]]
        [rewrite-clj.node.forms :refer [FormsNode]]
        [rewrite-clj.node.integer :refer [IntNode]]
        [rewrite-clj.node.keyword :refer [KeywordNode]]
        [rewrite-clj.node.quote :refer [QuoteNode]]
        [rewrite-clj.node.stringz :refer [StringNode string-node]]
        [rewrite-clj.node.uneval :refer [UnevalNode]]
        [rewrite-clj.node.meta :refer [MetaNode meta-node]]
        [rewrite-clj.node.protocols :refer [NodeCoerceable coerce]]
        [rewrite-clj.node.reader-macro :refer [ReaderNode ReaderMacroNode DerefNode reader-macro-node var-node]]
        [rewrite-clj.node.seq :refer [SeqNode vector-node list-node set-node map-node]]
        [rewrite-clj.node.token :refer [TokenNode token-node]]
        [rewrite-clj.node.whitespace :refer [WhitespaceNode NewlineNode] :as ws]]))
   #?(:clj
      (:import [rewrite_clj.node.comment CommentNode]
               [rewrite_clj.node.forms FormsNode]
               [rewrite_clj.node.integer IntNode]
               [rewrite_clj.node.keyword KeywordNode]
               [rewrite_clj.node.meta MetaNode]
               [rewrite_clj.node.quote QuoteNode]
               [rewrite_clj.node.reader_macro ReaderNode ReaderMacroNode DerefNode]
               [rewrite_clj.node.seq SeqNode]
               [rewrite_clj.node.stringz StringNode]
               [rewrite_clj.node.token TokenNode]
               [rewrite_clj.node.uneval UnevalNode]
               [rewrite_clj.node.whitespace WhitespaceNode NewlineNode])))

;; ## Helpers

(defn node-with-meta
  [n value]
  (if #?(:clj (instance? clojure.lang.IMeta value)
         :cljs (satisfies? IWithMeta value))
    (let [mta (meta value)]
      (if (empty? mta)
        n
        (meta-node (coerce mta) n)))
    n))

(let [comma (ws/whitespace-nodes ", ")
      space (ws/whitespace-node " ")]
  (defn- map->children
    [m]
    (->> (mapcat
          (fn [[k v]]
            (list* (coerce k) space (coerce v) comma))
          m)
         (drop-last (count comma))
         (vec))))

(defn- record-node
  [m]
  (reader-macro-node
   [(token-node #?(:clj (symbol (.getName ^Class (class m)))
                   :cljs (symbol (string/replace (pr-str (type m)) "/" "."))))
    (map-node (map->children m))]))

;; ## Tokens

(extend-protocol NodeCoerceable
  #?(:clj Object :cljs default)
  (coerce [v]
    (node-with-meta
     ;; TODO: might be misleading, should never be a record for clj
     (if (record? v)
       (record-node v)
       (token-node v))
     v)))

(extend-protocol NodeCoerceable
  nil
  (coerce [v]
    (token-node nil)))

;; TODO: cljs only - is this needed?
#?(:cljs (extend-protocol NodeCoerceable
           number
           (coerce [n]
             (node-with-meta
              (token-node n)
              n))))

;; TODO: cljs only - is this needed?
#?(:cljs (extend-protocol NodeCoerceable
           string
           (coerce [n]
             (node-with-meta
              (string-node n)
              n))))

;; ## Seqs

(defn- seq-node
  [f sq]
  (node-with-meta
    (->> (map coerce sq)
         (ws/space-separated)
         (vec)
         (f))
    sq))

(extend-protocol NodeCoerceable
  #?(:clj clojure.lang.IPersistentVector :cljs PersistentVector)
  (coerce [sq]
    (seq-node vector-node sq))
  #?(:clj clojure.lang.IPersistentList :cljs List)
  (coerce [sq]
    (seq-node list-node sq))
  #?(:clj clojure.lang.IPersistentSet :cljs PersistentHashSet)
  (coerce [sq]
    (seq-node set-node sq)))

;; ## Maps

(extend-protocol NodeCoerceable
  #?(:clj clojure.lang.IPersistentMap :cljs PersistentHashMap)
  (coerce [m]
    (node-with-meta
     ;; TODO: might be misleading, will never be a record here for cljs
     (if (record? m)
       (record-node m)
       (map-node (map->children m)))
     m)))

;; ## Vars

(extend-protocol NodeCoerceable
  #?(:clj clojure.lang.Var :cljs Var)
  (coerce [v]
    (-> (str v)
        (subs 2)
        (symbol)
        (token-node)
        (vector)
        (var-node))))

;; ## Existing Nodes

(extend-protocol NodeCoerceable
  CommentNode     (coerce [v] v)
  FormsNode       (coerce [v] v)
  IntNode         (coerce [v] v)
  KeywordNode     (coerce [v] v)
  MetaNode        (coerce [v] v)
  QuoteNode       (coerce [v] v)
  ReaderNode      (coerce [v] v)
  ReaderMacroNode (coerce [v] v)
  DerefNode       (coerce [v] v)
  StringNode      (coerce [v] v)
  UnevalNode      (coerce [v] v)
  NewlineNode     (coerce [v] v)
  SeqNode         (coerce [v] v)
  TokenNode       (coerce [v] v)
  WhitespaceNode  (coerce [v] v))
