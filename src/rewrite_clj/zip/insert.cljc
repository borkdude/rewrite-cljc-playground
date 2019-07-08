(ns ^:no-doc rewrite-clj.zip.insert
  (:require [rewrite-clj.zip.base :as base]
            [rewrite-clj.zip.whitespace :as ws]
            [rewrite-clj.node :as node]
            [rewrite-clj.custom-zipper.core :as z]))

(def ^:private space
  (node/spaces 1))

(defn- insert
  "Generic insertion helper. If the node reached by `move-fn`
   is a whitespace, insert an additional space."
  [move-fn insert-fn prefix zloc item]
  (let [item-node (node/coerce item)
        next-node (move-fn zloc)]
    (->> (concat
          (if (and next-node (not (ws/whitespace? next-node)))
            [space])
          [item-node]
          (if (not (ws/whitespace? zloc))
            prefix))
         (reduce insert-fn zloc))))

(defn insert-right
  "Return zipper with `item` inserted to the right of the current node in `zloc`.
  Will insert a space if necessary."
  [zloc item]
  (insert
    z/right
    z/insert-right
    [space]
    zloc item))

(defn insert-left
  "Return zipper with `item` inserted to the left of the current node in `zloc`.
  Will insert a space if necessary."
  [zloc item]
  (insert
    z/left
    z/insert-left
    [space]
    zloc item))

(defn insert-child
  "Return zipper with `item` inserted as the first child of the current node in `zloc`."
  [zloc item]
  (insert
    z/down
    z/insert-child
    []
    zloc item))

(defn append-child
  "Return zipper with `item` appended as last child of the current node in `zloc`.
  Will insert a space if necessary."
  [zloc item]
  (insert
    #(some-> % z/down z/rightmost)
    z/append-child
    []
    zloc item))
