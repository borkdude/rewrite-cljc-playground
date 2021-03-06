(ns rewrite-cljc.zip-test
  "This test namespace originated from rewrite-cljs."
  (:require [clojure.test :refer [deftest is]]
            [rewrite-cljc.zip :as z]))

(deftest of-string-simple-sexpr
  (let [sexpr "(+ 1 2)"]
   (is (= sexpr (-> sexpr z/of-string z/root-string)))))


(deftest manipulate-sexpr
  (let [sexpr "
 ^{:dynamic true} (+ 1 1
   (+ 2 2)
   (reduce + [1 3 4]))"
        expected "
 ^{:dynamic true} (+ 1 1
   (+ 2 2)
   (reduce + [6 7 [1 2]]))"]
    (is (= expected (-> (z/of-string sexpr {:track-position? true})
                        ;; TODO: update to [4 9]
                        (z/find-tag-by-pos {:row 4 :col 19} :vector)
                        (z/replace [5 6 7])
                        (z/append-child [1 2])
                        z/down
                        z/remove
                        z/root-string)))))

(deftest namespaced-keywords
  (is (= ":dill" (-> ":dill" z/of-string z/root-string)))
  (is (= "::dill" (-> "::dill" z/of-string z/root-string)))
  (is (= ":dill/dall" (-> ":dill/dall" z/of-string z/root-string)))
  (is (= "::dill/dall" (-> "::dill/dall" z/of-string z/root-string)))
  (is (= ":%dill.*" (-> ":%dill.*" z/of-string z/root-string))))
