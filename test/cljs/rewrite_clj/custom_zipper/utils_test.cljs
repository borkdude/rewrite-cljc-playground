(ns rewrite-clj.custom-zipper.utils-test
  (:require [clojure.test :refer-macros [deftest is are testing run-tests]]
            [rewrite-clj.node :as node]
            [rewrite-clj.zip.base :as base]
            [rewrite-clj.custom-zipper.core :as z]
            [rewrite-clj.custom-zipper.utils :refer [remove-right
                                                     remove-left
                                                     remove-and-move-right
                                                     remove-and-move-left]]))

(let [a (node/token-node 'a)
      b (node/token-node 'b)
      c (node/token-node 'c)
      d (node/token-node 'd)
      loc (z/down (base/edn* (node/forms-node [a b c d])))]
  (deftest t-remove-right
    (let [loc' (remove-right loc)]
      (is (= 'a (base/sexpr loc')))
      (is (= "acd" (base/root-string loc')))))
  (deftest t-remove-left
    (let [loc' (-> loc z/right z/right remove-left)]
      (is (= 'c (base/sexpr loc')))
      (is (= "acd" (base/root-string loc')))))
  (deftest t-remove-and-move-right
    (let [loc' (remove-and-move-right (z/right loc))]
      (is (= 'c (base/sexpr loc')))
      (is (= "acd" (base/root-string loc')))))
  (deftest t-remove-and-move-left
    (let [loc' (-> loc z/right remove-and-move-left)]
      (is (= 'a (base/sexpr loc')))
      (is (= "acd" (base/root-string loc'))))))

(deftest t-remove-and-move-left-tracks-current-position-correctly
  (are [?n ?pos]
       (let [root (base/of-string "[a bb ccc]" {:track-position? true})
             zloc (nth (iterate z/next root) ?n)]
         (is (= ?pos (z/position (remove-and-move-left zloc)))))
    3  [1 3]
    5  [1 6]
    2  [1 2]))

(deftest t-remove-and-move-right-does-not-affect-position
  (are [?n ?pos]
       (let [root (base/of-string "[a bb ccc]" {:track-position? true})
             zloc (nth (iterate z/next root) ?n)]
         (is (= ?pos (z/position (remove-and-move-right zloc)))))
    3  [1 4]
    1  [1 2]
    2  [1 3]))

(deftest t-remove-left-tracks-current-position-correctly
  (are [?n ?pos]
       (let [root (base/of-string "[a bb ccc]" {:track-position? true})
             zloc (nth (iterate z/next root) ?n)]
         (is (= ?pos (z/position (remove-left zloc)))))
    3  [1 3]
    5  [1 6]))
