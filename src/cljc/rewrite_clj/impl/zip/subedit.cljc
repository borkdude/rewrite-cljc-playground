(ns ^:no-doc rewrite-clj.impl.zip.subedit
  (:require [rewrite-clj.impl.zip.base :as base]
            [rewrite-clj.impl.custom-zipper.core :as z])
  #?(:cljs (:require-macros [rewrite-clj.impl.zip.subedit])))

;; ## Edit Scope

(defn- path
  "Generate a seq representing a path to the current node
   starting at the root. Each element represents one `z/down`
   and the value of each element will be the number of `z/right`s
   to run."
  [zloc]
  (->> (iterate z/up zloc)
       (take-while z/up)
       (map (comp count z/lefts))
       (reverse)))

(defn- move-step
  "Move one down and `n` steps to the right."
  [loc n]
  (nth
    (iterate z/right (z/down loc))
    n))

(defn- move-to
  "Move to the node represented by the given path."
  [zloc path]
  (let [root (-> zloc z/root base/edn*)]
    (reduce move-step root path)))

(defn edit-node
  "Apply the given function to the current zipper location. The resulting
   zipper will be located at the same path (i.e. the same number of
   downwards and right movements from the root) as the original node."
  [zloc f]
  (let [zloc' (f zloc)]
    (assert (not (nil? zloc')) "function applied in 'edit-node' returned nil.")
    (move-to zloc' (path zloc))))

;; ## Sub-Zipper

#?(:clj
   (defmacro edit->
     "Like `->`, applying the given function to the current zipper location.
   The resulting zipper will be located at the same path (i.e. the same
   number of downwards and right movements from the root) as the original
   node."
     [zloc & body]
     `(edit-node ~zloc #(-> % ~@body))))

#?(:clj
   (defmacro edit->>
     "Like `->>`, applying the given function to the current zipper location.
   The resulting zipper will be located at the same path (i.e. the same
   number of downwards and right movements from the root) as the original
   node."
     [zloc & body]
     `(edit-node ~zloc #(->> % ~@body))))

#?(:clj
   (defmacro subedit->
     "Like `->`, applying modifications to the current sub-tree, zipping
   up to the current location afterwards."
     [zloc & body]
     `(subedit-node ~zloc #(-> % ~@body))))

#?(:clj
   (defmacro subedit->>
     "Like `->>`, applying modifications to the current sub-tree, zipping
   up to the current location afterwards."
     [zloc & body]
     `(subedit-node ~zloc #(->> % ~@body))))

(defn subzip
  "Create zipper whose root is the current node."
  [zloc]
  (let [zloc' (some-> zloc z/node base/edn*)]
    (assert zloc' "could not create subzipper.")
    zloc'))

(defn subedit-node
  "Apply the given function to the current sub-tree. The resulting
   zipper will be located on the root of the modified sub-tree."
  [zloc f]
  (let [zloc' (f (subzip zloc))]
    (assert (not (nil? zloc')) "function applied in 'subedit-node' returned nil.")
    (z/replace zloc (z/root zloc'))))
