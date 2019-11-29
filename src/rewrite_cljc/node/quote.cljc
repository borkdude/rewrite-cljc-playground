(ns ^:no-doc rewrite-cljc.node.quote
  (:require [rewrite-cljc.node.protocols :as node]))

;; ## Node

(defrecord QuoteNode [tag prefix sym children]
  node/Node
  (tag [_] tag)
  (printable-only? [_] false)
  (sexpr [_]
    (list sym (first (node/sexprs children))))
  (length [_]
    (+ (count prefix) (node/sum-lengths children)))
  (string [_]
    (str prefix (node/concat-strings children)))

  node/InnerNode
  (inner? [_] true)
  (children [_] children)
  (replace-children [this children']
    (node/assert-single-sexpr children')
    (assoc this :children children'))
  (leader-length [_]
    (count prefix))

  Object
  (toString [this]
    (node/string this)))

(node/make-printable! QuoteNode)

;; ## Constructors

(defn- ->node
  [t prefix sym children]
  (node/assert-single-sexpr children)
  (->QuoteNode t prefix sym children))

(defn quote-node
  "Create node representing a quoted form with `children`.
   Takes either a seq of nodes or a single one."
  [children]
  (if (sequential? children)
    (->node
      :quote "'" 'quote
      children)
    (recur [children])))

(defn syntax-quote-node
  "Create node representing a syntax-quoted form with `children`.
   Takes either a seq of nodes or a single one."
  [children]
  (if (sequential? children)
    (->node
      :syntax-quote "`" 'quote
      children)
    (recur [children])))

(defn unquote-node
  "Create node representing an unquoted form with `children`. (`~...`)
   Takes either a seq of nodes or a single one."
  [children]
  (if (sequential? children)
    (->node
      :unquote "~" 'unquote
      children)
    (recur [children])))

(defn unquote-splicing-node
  "Create node representing an unquote-spliced form with `children`. (`~@...`)
   Takes either a seq of nodes or a single one."
  [children]
  (if (sequential? children)
    (->node
      :unquote-splicing "~@" 'unquote-splicing
      children)
    (recur [children])))