(ns ^:no-doc rewrite-clj.internal.zip.base
  (:refer-clojure :exclude [print])
  (:require [rewrite-clj.node :as node]
            [rewrite-clj.parser :as p]
            [rewrite-clj.internal.zip.whitespace :as ws]
            [rewrite-clj.internal.custom-zipper.core :as z]))

;; ## Zipper
(defn edn*
  "Create zipper over the given Clojure/EDN node.
   If `:track-position?` is set, this will create a custom zipper that will
   return the current row/column using `rewrite-clj.zip/position`. (Note that
   this custom zipper will be incompatible with `clojure.zip`'s functions.)"
  ([node]
   (edn* node {}))
  ([node {:keys [track-position?]}]
   (if track-position?
     (z/custom-zipper node)
     (z/zipper node))))

(defn edn
  "Create zipper over the given Clojure/EDN node and move to the first
   non-whitespace/non-comment child.
   If `:track-position?` is set, this will create a custom zipper that will
   return the current row/column using `rewrite-clj.zip/position`. (Note that
   this custom zipper will be incompatible with `clojure.zip`'s functions.)"
  ([node] (edn node {}))
  ([node {:keys [track-position?] :as options}]
   (if (= (node/tag node) :forms)
     (let [top (edn* node options)]
       (or (-> top z/down ws/skip-whitespace)
           top))
     (recur (node/forms-node [node]) options))))

;; ## Inspection

(defn tag
  "Get tag of node at the current zipper location."
  [zloc]
  (some-> zloc z/node node/tag))

(defn sexpr
  "Get sexpr represented by the given node."
  [zloc]
  (some-> zloc z/node node/sexpr))

(defn ^{:added "0.4.4"} child-sexprs
  "Get children as s-expressions."
  [zloc]
  (some-> zloc z/node node/child-sexprs))

(defn length
  "Get length of printable string for the given zipper location."
  [zloc]
  (or (some-> zloc z/node node/length) 0))

(defn ^{:deprecated "0.4.0"} value
  "DEPRECATED. Return a tag/s-expression pair for inner nodes, or
   the s-expression itself for leaves."
  [zloc]
  ;; ignore deprecated warning - calling deprecated from deprecated
  (some-> zloc z/node node/value))

;; ## Read
(defn of-string
  "Create zipper from String."
  ([s] (of-string s {}))
  ([s options]
   (some-> s p/parse-string-all (edn options))))

;; TODO: clj only
#_(defn of-file
    "Create zipper from File."
    ([f] (of-file f {}))
    ([f options]
     (some-> f p/parse-file-all (edn options))))

;; ## Write

(defn ^{:added "0.4.0"} string
  "Create string representing the current zipper location."
  [zloc]
  (some-> zloc z/node node/string))

(defn ^{:added "0.4.0"} root-string
  "Create string representing the zipped-up zipper."
  [zloc]
  (some-> zloc z/root node/string))

(defn- print!
  [^String s writer]
  ;; TODO: cljs has no print? using pr for now
  (pr s)
  (if writer
    ;; TODO: clj only
    #_(.write ^java.io.Writer writer s)
    ;; TODO: is that right? maybe should just ignore writer for cljs
    (-write *out* s)
    (recur s *out*)))

(defn print
  "Print current zipper location."
  [zloc & [writer]]
  (some-> zloc
          string
          (print! writer)))

(defn print-root
  "Zip up and print root node."
  [zloc & [writer]]
  (some-> zloc
          root-string
          (print! writer)))
