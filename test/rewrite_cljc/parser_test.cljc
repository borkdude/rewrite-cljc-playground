(ns ^{:doc "Tests for EDN parser."
      :author "Yannick Scherer"}
    rewrite-cljc.parser-test
  (:refer-clojure :exclude [read-string])
  (:require [clojure.test :refer [deftest is are]]
            [clojure.tools.reader.edn :refer [read-string]]
            [clojure.tools.reader :refer [*alias-map*]]
            [rewrite-cljc.node :as node]
            [rewrite-cljc.parser :as p])
  #?(:clj (:import clojure.lang.ExceptionInfo)))

(deftest t-parsing-the-first-few-whitespaces
  (are [?ws ?parsed]
       (let [n (p/parse-string ?ws)]
         (is (= :whitespace (node/tag n)))
         (is (= ?parsed (node/string n))))
    "   "     "   "
    "   \n  " "   "))

(deftest t-parsing-whitespace-strings
  (are [?ws ?children]
      (let [n (p/parse-string-all ?ws)]
        (is (= :forms (node/tag n)))
        (is (= (.replace ?ws "\r\n" "\n") (node/string n)))
        (is (= ?children (map (juxt node/tag node/string) (node/children n)))))
    "   \n   "     [[:whitespace "   "]
                    [:newline "\n"]
                    [:whitespace "   "]]
    " \t \r\n \t " [[:whitespace " \t "]
                    [:newline "\n"]
                    [:whitespace " \t "]]))

#?(:clj
   (deftest t-parsing-unicode-whitespace-strings
     (are [?ws ?children]
         (let [n (p/parse-string-all ?ws)]
           (is (= :forms (node/tag n)))
           (is (= (.replace ?ws "\r\n" "\n") (node/string n)))
           (is (= ?children (map (juxt node/tag node/string) (node/children n)))))
       "\u2028"       [[:whitespace "\u2028"]])))

(deftest t-parsing-simple-data
  (are [?s ?r]
      (let [n (p/parse-string ?s)]
        (is (= :token (node/tag n)))
        (is (= ?s (node/string n)))
        (is (= ?r (node/sexpr n))))
    "0"                          0
    "0.1"                        0.1
    "12e10"                      1.2e11
    "2r1100"                     12
    "1N"                         1N
    ":key"                       :key
    "\\\\"                       \\
    "\\a"                        \a
    "\\space"                    \space
    "\\'"                        \'
    ":1.5"                       :1.5
    ":1.5.0"                     :1.5.0
    ":ns/key"                    :ns/key
    ":key:key"                   :key:key
    ":x'"                        :x'
    "sym"                        'sym
    "sym#"                       'sym#
    "sym'"                       'sym'
    "sym'sym"                    'sym'sym
    "sym:sym"                    'sym:sym
    "\"string\""                 "string"))

(deftest t-parsing-garden-selectors
  ;; https://github.com/noprompt/garden
  (are [?s ?r]
      (let [n (p/parse-string ?s)
            r (node/sexpr n)]
        (is (= ?s (node/string n)))
        (is (= :token (node/tag n)))
        (is (keyword? r))
        (is (= ?r r)))
    ":&:hover"    :&:hover
    ;; clj clojure reader can't parse :&::before but we can create a keyword for it
    ":&::before"  (keyword "&::before")))

(deftest t-parsing-auto-resolves
  (are [?s ?r]
      (binding [*ns* (create-ns 'rewrite-cljc.parser-test)]
        (let [n (p/parse-string ?s)]
          (is (= :token (node/tag n)))
          (is (= ?s (node/string n)))
          (is (= ?r (node/sexpr n)))))
    "::1.5.1"                    ::1.5.1
    "::key"                      ::key
    "::xyz/key"                  :xyz/key))

;; TODO: no ratios in cljs; they will be evaluated on sexpr, need to add notes in docs
(deftest t-ratios
  (are [?s ?r]
       (let [n (p/parse-string ?s)]
         (is (= :token (node/tag n)))
         (is (= ?s (node/string n)))
         (is (= ?r (node/sexpr n))))
    "3/4"                                      #?(:clj 3/4 :cljs 0.75)))

;; TODO: what to check here? this test is not checking anything
(deftest t-big-integers
  (are [?s ?r]
      (let [n (p/parse-string ?s)]
        (is (= :token (node/tag n)))
        (is (= ?s (node/string n)))
        (is (= ?r (node/sexpr n))))
    "1234567890123456789012345678901234567890" 1234567890123456789012345678901234567890N))

(deftest t-parsing-symbolic-inf-values
  (are [?s ?r]
      (let [n (p/parse-string ?s)]
        (is (= :token (node/tag n)))
        (is (= ?s (node/string n)))
        (is (= ?r (node/sexpr n))))
    "##Inf" '##Inf
    "##-Inf" '##-Inf))

(deftest t-parsing-symbolic-NaN-value
  (let [n (p/parse-string "##NaN")
        e (node/sexpr n)]
    (is (= :token (node/tag n)))
    (is (= "##NaN" (node/string n)))
    #?(:cljs (is (js/Number.isNaN e))
       :default (is (Double/isNaN e)))))

(deftest t-parsing-reader-prefixed-data
  (are [?s ?t ?ws ?sexpr]
       (let [n (p/parse-string ?s)
             children (node/children n)
             c (map node/tag children)]
         (is (= ?t (node/tag n)))
         (is (= :token (last c)))
         (is (= ?sexpr (node/sexpr n)))
         (is (= 'sym (node/sexpr (last children))))
         (is (= ?ws (vec (butlast c)))))
    "@sym"                 :deref            []              '(deref sym)
    "@  sym"               :deref            [:whitespace]   '(deref sym)
    "'sym"                 :quote            []              '(quote sym)
    "'  sym"               :quote            [:whitespace]   '(quote sym)
    "`sym"                 :syntax-quote     []              '(quote sym)
    "`  sym"               :syntax-quote     [:whitespace]   '(quote sym)
    "~sym"                 :unquote          []              '(unquote sym)
    "~  sym"               :unquote          [:whitespace]   '(unquote sym)
    "~@sym"                :unquote-splicing []              '(unquote-splicing sym)
    "~@  sym"              :unquote-splicing [:whitespace]   '(unquote-splicing sym)
    "#=sym"                :eval             []              '(eval 'sym)
    "#=  sym"              :eval             [:whitespace]   '(eval 'sym)
    "#'sym"                :var              []              '(var sym)
    "#'\nsym"              :var              [:newline]      '(var sym)))

(deftest t-eval
  (let [n (p/parse-string "#=(+ 1 2)")]
    (is (= :eval (node/tag n)))
    (is (= "#=(+ 1 2)" (node/string n)))
    (is (= '(eval '(+ 1 2)) (node/sexpr n)))))

(deftest t-uneval
  (let [s "#' #_    (+ 1 2) sym"
        n (p/parse-string s)
        [ws0 uneval ws1 sym] (node/children n)]
    (is (= :var (node/tag n)))
    (is (= s (node/string n)))
    (is (= :whitespace (node/tag ws0)))
    (is (= :whitespace (node/tag ws1)))
    (is (= :token (node/tag sym)))
    (is (= 'sym (node/sexpr sym)))
    (is (= :uneval (node/tag uneval)))
    (is (= "#_    (+ 1 2)" (node/string uneval)))
    (is (node/printable-only? uneval))
    (is (thrown-with-msg? ExceptionInfo #"unsupported operation" (node/sexpr uneval)))))

(deftest t-parsing-regular-expressions
  (are [?s ?p]
       (let [n (p/parse-string ?s)]
         (is (= :regex (node/tag n)))
         (is (= ?p (node/sexpr n))))
    "#\"regex\""       '(re-pattern "regex")
    "#\"regex\\.\""    '(re-pattern "regex\\.")
    "#\"[reg|k].x\""   '(re-pattern "[reg|k].x")
    "#\"a\\nb\""       '(re-pattern "a\\nb")
    "#\"a\nb\""        '(re-pattern "a\nb")))

(deftest t-parsing-strings
  (are [?s ?tag ?sexpr]
       (let [n (p/parse-string ?s)]
         (is (= ?tag (node/tag n)))
         (is (= ?s (node/string n)))
         (is (= ?sexpr (node/sexpr n))))
    "\"123\""       :token       "123"
    "\"123\\n456\"" :token       "123\n456"
    "\"123\n456\""  :multi-line  "123\n456"))

(deftest t-parsing-seqs
  (are [?s ?t ?w ?c]
       (let [n (p/parse-string ?s)
             children (node/children n)
             fq (frequencies (map node/tag children))]
         (is (= ?t (node/tag n)))
         (is (= (.trim ?s) (node/string n)))
         (node/sexpr n)     = (read-string ?s)
         (is (= ?w (:whitespace fq 0)))
         (is (= ?c (:token fq 0))))
    "(1 2 3)"          :list        2  3
    "()"               :list        0  0
    "( )"              :list        1  0
    "() "              :list        0  0
    "[1 2 3]"          :vector      2  3
    "[]"               :vector      0  0
    "[ ]"              :vector      1  0
    "[] "              :vector      0  0
    "#{1 2 3}"         :set         2  3
    "#{}"              :set         0  0
    "#{ }"             :set         1  0
    "#{} "             :set         0  0
    "{:a 0 :b 1}"      :map         3  4
    "{}"               :map         0  0
    "{ }"              :map         1  0
    "{} "              :map         0  0))

(deftest t-parsing-metadata
  (are [?s ?t ?mt]
       (let [s (str ?s " s")
             n (p/parse-string s)
             [mta ws sym] (node/children n)]
         (is (= ?t (node/tag n)))
         (is (= s (node/string n)))
         (is (= 's (node/sexpr n)))
         (is (= {:private true} (meta (node/sexpr n))))
         (is (= ?mt (node/tag mta)))
         (is (= :whitespace (node/tag ws)))
         (is (= :token (node/tag sym)))
         (is (= 's (node/sexpr sym))))
    "^:private"          :meta  :token
    "^{:private true}"   :meta  :map
    "#^:private"         :meta* :token
    "#^{:private true}"  :meta* :map))

(deftest t-parsing-multiple-metadata-forms
  (are [?s ?t ?mt]
       (let [s (str ?s " s")
             n (p/parse-string s)
             [mta ws n'] (node/children n)
             ;; TODO: verify sym?
             [mta2 ws2 _sym] (node/children n')]
          ;; outer meta
         (is (= ?t (node/tag n)))
         (is (= s (node/string n)))
         (is (= 's (node/sexpr n)))
         (is (= {:private true :awe true} (meta (node/sexpr n))))
         (is (= ?mt (node/tag mta)))
         (is (= :whitespace (node/tag ws)))

          ;; inner meta
         (is (= ?t (node/tag n')))
         (is (= {:awe true} (meta (node/sexpr n'))))
         (is (= ?mt (node/tag mta2)))
         (is (= :whitespace (node/tag ws2))))
    "^:private ^:awe"                 :meta  :token
    "^{:private true} ^{:awe true}"   :meta  :map
    "#^:private #^:awe"               :meta* :token
    "#^{:private true} #^{:awe true}" :meta* :map))

(deftest t-parsing-reader-macros
  (are [?s ?t ?children]
       (let [n (p/parse-string ?s)]
         (is (= ?t (node/tag n)))
         (is (= ?s (node/string n)))
         (is (= ?children (map node/tag (node/children n)))))
    "#'a"             :var             [:token]
    "#=(+ 1 2)"       :eval            [:list]
    "#macro 1"        :reader-macro    [:token :whitespace :token]
    "#macro (* 2 3)"  :reader-macro    [:token :whitespace :list]
    "#?(:clj bar)"    :reader-macro    [:token :list]
    "#? (:clj bar)"   :reader-macro    [:token :whitespace :list]
    "#?@ (:clj bar)"  :reader-macro    [:token :whitespace :list]
    "#?foo baz"       :reader-macro    [:token :whitespace :token]
    "#_abc"           :uneval          [:token]
    "#_(+ 1 2)"       :uneval          [:list]))

(deftest t-parsing-anonymous-fn
  (are [?s ?t ?sexpr-match ?children]
      (let [n (p/parse-string ?s)]
        (is (= ?t (node/tag n)))
        (is (= ?s (node/string n)))
        (is (re-matches ?sexpr-match (str (node/sexpr n))))
        (is (= ?children (map node/tag (node/children n)))))
    "#(+ % 1)"
    :fn  #"\(fn\* \[p1_.*#\] \(\+ p1_.*# 1\)\)"
    [:token :whitespace
     :token :whitespace
     :token]

    "#(+ %& %2 %1)"
    :fn  #"\(fn\* \[p1_.*# p2_.*# & rest_.*#\] \(\+ rest_.*# p2_.*# p1_.*#\)\)"
    [:token :whitespace
     :token :whitespace
     :token :whitespace
     :token]))

(deftest t-parsing-comments
  (are [?s]
       (let [n (p/parse-string ?s)]
         (is (node/printable-only? n))
         (is (= :comment (node/tag n)))
         (is (= ?s (node/string n))))
    "; this is a comment\n"
    ";; this is a comment\n"
    "; this is a comment"
    ";; this is a comment"
    ";"
    ";;"
    ";\n"
    ";;\n"))

(deftest t-parsing-prefix-namespaced-maps
  (are [?s ?children ?sexpr]
      (let [n (p/parse-string ?s)]
        (is (= :namespaced-map (node/tag n)))
        (is (= (count ?s) (node/length n)))
        (is (= ?s (node/string n)))
        (is (= ?children (str (node/children n))))
        (is (= ?sexpr (node/sexpr n))))
    "#:abc{:x 1, :y 1}"
    "[<token: :abc> <map: {:x 1, :y 1}>]"
    {:abc/x 1, :abc/y 1}

    "#:abc   {:x 1, :y 1}"
    "[<token: :abc> <whitespace: \"   \"> <map: {:x 1, :y 1}>]"
    {:abc/x 1, :abc/y 1}

    "#:foo{:kw 1, :n/kw 2, :_/bare 3, 0 4}"
    "[<token: :foo> <map: {:kw 1, :n/kw 2, :_/bare 3, 0 4}>]"
    {:foo/kw 1, :n/kw 2, :bare 3, 0 4}

    "#:abc{:a {:b 1}}"
    "[<token: :abc> <map: {:a {:b 1}}>]"
    {:abc/a {:b 1}}

    "#:abc{:a #:def{:b 1}}"
    "[<token: :abc> <map: {:a #:def{:b 1}}>]"
    {:abc/a {:def/b 1}}))

(deftest t-parsing-auto-resolve-namespaced-maps
  (are [?s ?children ?sexpr]
      (binding [*ns* (create-ns 'rewrite-cljc.parser-test)]
        (let [n (p/parse-string ?s)]
          (is (= :namespaced-map (node/tag n)))
          (is (= (count ?s) (node/length n)))
          (is (= ?s (node/string n)))
          (is (= ?children (str (node/children n))))
          (is (= ?sexpr (node/sexpr n)))))
    "#::{:x 1, :y 1}"
    "[<token: ::> <map: {:x 1, :y 1}>]"
    {::x 1, ::y 1}

    "#::   {:x 1, :y 1}"
    "[<token: ::> <whitespace: \"   \"> <map: {:x 1, :y 1}>]"
    {::x 1, ::y 1}

    ;; TODO: failing under cljs advanced
    "#::{:kw 1, :n/kw 2, :_/bare 3, 0 4}"
    "[<token: ::> <map: {:kw 1, :n/kw 2, :_/bare 3, 0 4}>]"
    {::kw 1, :n/kw 2, :bare 3, 0 4}

    ;; TODO: failing under cljs advanced
    "#::{:a {:b 1}}"
    "[<token: ::> <map: {:a {:b 1}}>]"
    {::a {:b 1}}

    ;; TODO: failing under cljs advanced
    "#::{:a #::{:b 1}}"
    "[<token: ::> <map: {:a #::{:b 1}}>]"
    {::a {::b 1}}))

(defn parsing-auto-resolve-alias-namespaced-maps[]
  (are [?s ?children ?sexpr]
      (let [n (p/parse-string ?s)]
        (is (= :namespaced-map (node/tag n)))
        (is (= (count ?s) (node/length n)))
        (is (= ?s (node/string n)))
        (is (= ?children (str (node/children n))))
        (is (= ?sexpr (node/sexpr n))))
    "#::node{:x 1, :y 1}"
    "[<token: ::node> <map: {:x 1, :y 1}>]"
    '{::node/x 1, ::node/y 1}

    "#::node   {:x 1, :y 1}"
    "[<token: ::node> <whitespace: \"   \"> <map: {:x 1, :y 1}>]"
    '{::node/x 1, ::node/y 1}

    "#::node{:kw 1, :n/kw 2, :_/bare 3, 0 4}"
    "[<token: ::node> <map: {:kw 1, :n/kw 2, :_/bare 3, 0 4}>]"
    '{::node/kw 1, :n/kw 2, :bare 3, 0 4}

    "#::node{:a {:b 1}}"
    "[<token: ::node> <map: {:a {:b 1}}>]"
    '{::node/a {:b 1}}

    "#::node{:a #::node{:b 1}}"
    "[<token: ::node> <map: {:a #::node{:b 1}}>]"
    '{::node/a {::node/b 1}}))

#?(:clj
   (deftest t-parsing-auto-resolve-alias-namespaced-maps-clj-style
     (binding [*ns* (find-ns 'rewrite-cljc.parser-test)]
       (parsing-auto-resolve-alias-namespaced-maps))))

(deftest t-parsing-auto-resolve-alias-namespaced-maps-cljs-style
  (binding [*alias-map* '{node rewrite-cljc.node}]
    (parsing-auto-resolve-alias-namespaced-maps)))

(deftest t-parsing-namespaced-map-does-not-require-ns-when-not-calling-sexpr[]
  (are [?s ?children]
      (let [n (p/parse-string ?s)]
        (is (= :namespaced-map (node/tag n)))
        (is (= (count ?s) (node/length n)))
        (is (= ?s (node/string n)))
        (is (= ?children (str (node/children n)))))
    "#::a{:a #::a{:b 1}}"
    "[<token: ::a> <map: {:a #::a{:b 1}}>]"

    "#:a{:a #:a{:b 1}}"
    "[<token: :a> <map: {:a #:a{:b 1}}>]"

    "#::{:a #::{:b 1}}"
    "[<token: ::> <map: {:a #::{:b 1}}>]"))

(deftest t-parsing-exceptions
  (are [?s ?p]
      (is (thrown-with-msg? ExceptionInfo ?p (p/parse-string ?s)))
    "#"                     #".*Unexpected EOF.*"
    "#("                    #".*Unexpected EOF.*"
    "(def"                  #".*Unexpected EOF.*"
    "[def"                  #".*Unexpected EOF.*"
    "#{def"                 #".*Unexpected EOF.*"
    "{:a 0"                 #".*Unexpected EOF.*"
    "\"abc"                 #".*EOF.*"
    "#\"abc"                #".*Unexpected EOF.*"
    "(def x 0]"             #".*Unmatched delimiter.*"
    "##wtf"                 #".*Invalid token: ##wtf"
    "#="                    #".*:eval node expects 1 value.*"
    "#^"                    #".*:meta node expects 2 values.*"
    "^:private"             #".*:meta node expects 2 values.*"
    "#^:private"            #".*:meta node expects 2 values.*"
    "#_"                    #".*:uneval node expects 1 value.*"
    "#'"                    #".*:var node expects 1 value.*"
    "#macro"                #".*:reader-macro node expects 2 values.*"
    "#:"                    #".*Unexpected EOF.*"
    "#::"                   #".*Unexpected EOF.*"
    "#::nsarg"              #".*Unexpected EOF.*"
    "#:{:a 1}"              #".*:namespaced-map expected namespace prefix*"
    "#::[a]"                #".*:namespaced-map expected namespace alias or map.*"
    "#:[a]"                 #".*:namespaced-map expected namespace prefix.*"
    "#:: token"             #".*:namespaced-map expects a map.*"
    "#::alias [a]"          #".*:namespaced-map expects a map.*"
    "#:prefix [a]"          #".*:namespaced-map expects a map.*"))

(deftest t-parsing-multiple-forms
  (let [s "1 2 3"
        n (p/parse-string-all s)
        children (node/children n)]
    (is (= :forms (node/tag n)))
    (is (= s (node/string n)))
    (is (= '(do 1 2 3) (node/sexpr n)))
    (is (= [:token :whitespace
            :token :whitespace
            :token]
           (map node/tag children))))
  (let [s ";; Hi!\n(def pi 3.14)"
        n (p/parse-string-all s)
        children (node/children n)]
    (is (= :forms (node/tag n)))
    (is (= s (node/string n)))
    (is (= '(def pi 3.14) (node/sexpr n)))
    (is (= [:comment :list] (map node/tag children)))
    (node/string (first children))))

#?(:clj
   (deftest t-parsing-files
     (let [f (doto (java.io.File/createTempFile "rewrite.test" "")
               (.deleteOnExit))
           s "âbcdé"
           c ";; Hi"
           o (str c "\n\n" (pr-str s))]
       (spit f o)
       (is (= o (slurp f)))
       (let [n (p/parse-file-all f)
             children (node/children n)]
         (is (= :forms (node/tag n)))
         (is (= o (node/string n)))
         (is (= s (node/sexpr n)))
         (is (= [:comment :newline :token] (map node/tag children)))
         (is (= [";; Hi\n" "\n" (pr-str s)] (map node/string children)))))))

(defn- nodes-with-meta
  "Create map associating row/column number pairs with the node at that position."
  [n]
  (let [start-pos ((juxt :row :col) (meta n))
        end-pos ((juxt :end-row :end-col) (meta n))
        entry {start-pos {:node n, :end-pos end-pos}}]
    (if (node/inner? n)
      (->> (node/children n)
           (map nodes-with-meta)
           (into entry))
      entry)))

(let [s "(defn f\n  [x]\n  (println x))"
      positions (->> (p/parse-string-all s)
                     (nodes-with-meta))]
  (deftest t-rowcolumn-metadata
    (are [?pos _?end ?t ?s ?sexpr]
         (let [{:keys [node _end-pos]} (positions ?pos)]
           (is (= ?t (node/tag node)))
           (is (= ?s (node/string node)))
           (is (= ?sexpr (node/sexpr node)))
           ;; TODO: hmmm... did not see this one before. something to address? or maybe this is from cljs version before custom zipper?
           #_(deftest t-reliable-decision-on-end-pos-not-currently-possible
               (is (= ?end end-pos))))
      [1 1]  [3 14] :list   s              '(defn f [x] (println x))
      [1 2]  [1 5]  :token  "defn"         'defn
      [1 7]  [1 8]  :token  "f"            'f
      [2 3]  [2 5]  :vector "[x]"          '[x]
      [2 4]  [2 5]  :token  "x"            'x
      [3 3]  [3 13] :list   "(println x)"  '(println x)
      [3 4]  [3 10] :token  "println"      'println
      [3 12] [3 13] :token  "x"            'x)))
