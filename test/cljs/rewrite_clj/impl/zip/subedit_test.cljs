(ns rewrite-clj.impl.zip.subedit-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [rewrite-clj.impl.zip.base :as base]
            [rewrite-clj.impl.zip.move :as m]
            [rewrite-clj.impl.zip.subedit :refer-macros [subedit-> edit->]]
            [rewrite-clj.impl.custom-zipper.core :as z]))

(let [root (base/of-string "[1 #{2 [3 4] 5} 6]")]
  (deftest t-modifying-subtrees
    (let [loc (subedit-> root
                         m/next
                         m/next
                         m/next
                         (z/replace 'x))]
      (is (= :vector (base/tag loc)))
      (is (= "[1 #{x [3 4] 5} 6]" (base/string loc)))))
  (deftest t-modifying-the-whole-tree
    (let [loc (edit-> (-> root m/next m/next m/next)
                      m/prev m/prev
                      (z/replace 'x))]
      (is (= :token (base/tag loc)))
      (is (= "2" (base/string loc)))
      (is (= "[x #{2 [3 4] 5} 6]" (base/root-string loc))))))