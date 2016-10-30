(ns avi.edit-context.t-locations
  (:require [avi.edit-context
             [lines :as lines]
             [locations :refer :all]]
            [avi.test-helpers :refer :all]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [com.gfredericks.test.chuck.generators :as gen']
            [com.gfredericks.test.chuck.properties :as prop']
            [midje.sweet :refer :all]))

(def location-generator
  (gen/tuple
    (gen/choose 1 50)
    (gen/choose 0 50)))

(facts "about comparing simple locations"
  (location< [1 2] [1 4]) => true
  (location< [1 2] [2 2]) => true
  (location< [1 4] [2 2]) => true
  (location<= [1 2] [1 2]) => true
  (property "location< and location> are symmetric"
    (prop/for-all [a location-generator
                   b location-generator]
      (= (location< a b) (location> b a))))
  (property "location< implies location<="
    (prop'/for-all [a location-generator
                      b location-generator]
       (if (location< a b)
         (location<= a b)
         true)))
  (property "location> implies lociation>="
    (prop'/for-all [a location-generator
                    b location-generator]
      (if (location> a b)
        (location>= a b)
        true))))

(def line-generator (gen/such-that #(= -1 (.indexOf % "\n")) gen/string-ascii))
(def lines-generator (gen/such-that #(not (zero? (count %))) (gen/vector line-generator)))
(def lines-and-position-generator
  (gen'/for [lines lines-generator
             i-base gen/pos-int
             :let [i (mod i-base (count lines))]
             j-base gen/pos-int
             :let [j (mod j-base (inc (count (get lines i))))]]
    {:avi.document/lines lines
     :position [i j]}))

(def line-length-generator
  (gen/vector gen/pos-int 1 35))

(facts "about advance"
  (property "advance at eof is always nil"
    (prop'/for-all [line-length line-length-generator
                    :let [i (dec (count line-length))
                          j (last line-length)]]
      (nil? (advance [i j] #(get line-length %)))))
  (property "advance position always increases" 55
    (prop/for-all [{:keys [:avi.document/lines position]} lines-and-position-generator]
      (or (nil? (advance position (lines/line-length lines)))
          (location< position (advance position (lines/line-length lines))))))
  (property "advance never skips a line" 55 
    (prop/for-all [{lines :avi.document/lines [i j] :position} lines-and-position-generator]
      (or (nil? (advance [i j] (lines/line-length lines)))
          (= i (first (advance [i j] (lines/line-length lines))))
          (= (inc i) (first (advance [i j] (lines/line-length lines)))))))
  (property "advance on last character of any line but last goes to newline position"
    (prop'/for-all [line-lengths (gen/vector (gen'/bounded-int 1 25))
                    :when (>= (count line-lengths) 2)
                    i (gen'/bounded-int 0 (- (count line-lengths) 2))
                    :let [j (dec (line-lengths i))]]
      (= (advance [i j] line-lengths) [i (inc j)]))))

(facts "about retreat"
  (property "retreat from [0 0] is always nil"
    (prop/for-all [line-length line-length-generator]
      (nil? (retreat [0 0] line-length))))
  (property "retreat position always decreases"
    (prop/for-all [{:keys [:avi.document/lines position]} lines-and-position-generator]
      (or (nil? (retreat position (lines/line-length lines)))
          (location< (retreat position (lines/line-length lines)) position))))
  (property "retreat at bol goes to newline position"
    (prop'/for-all [line-length line-length-generator
                    :when (>= (count line-length) 2)
                    i (gen'/bounded-int 1 (dec (count line-length)))]
      (= (retreat [i 0] line-length)
         [(dec i) (line-length (dec i))])))
  (property "retreat never skips a line"
    (prop/for-all [{lines :avi.document/lines [i j] :position} lines-and-position-generator]
      (or (nil? (retreat [i j] (lines/line-length lines)))
          (= i (first (retreat [i j] (lines/line-length lines))))
          (= (dec i) (first (retreat [i j] (lines/line-length lines))))))))

(def replacement-text
  (gen'/for [:parallel [line-count (gen/choose 0 10)
                        last-length (gen/choose 0 25)]]
    (str (repeat line-count \newline)
         (repeat last-length \-))))

(facts "about adjust-for-replacement"
  (property "adjust-for-replacement does not change locations before a"
    (prop'/for-all [[l a b] (gen/fmap sort (gen/vector location-generator 3))
                    :when (not (= l a))
                    text replacement-text
                    bias (gen/elements [:left :right])]
      (= l (adjust-for-replacement l a b text bias))))
  (property "adjust-for-replacement deletes locations between a and b"
    (prop'/for-all [[a l b] (gen/fmap sort (gen/vector location-generator 3))
                    :when (and (not (= a l)) (not (= l b)))
                    text replacement-text
                    bias (gen/elements [:left :right])]
      (nil? (adjust-for-replacement l a b text bias))))
  (property "adjust-for-replacement does not change column when l line > b line"
    (prop'/for-all [[a b l] (gen/fmap sort (gen/vector location-generator 3))
                    :when (not (= (first b) (first l)))
                    text replacement-text
                    bias (gen/elements [:left :right])]
      (= (second l)
         (second (adjust-for-replacement l a b text bias)))))
  (fact "adjust-for-replacement adjusts line when l line >= b line"
    (adjust-for-replacement [4 2] [2 2] [3 7] "\n\n\n\n\n\n\n---" :left) => [10 2])
  (fact "adjust-for-replacement uses bias when a=b"
    (adjust-for-replacement [3 3] [3 3] [3 3] "\n\n\n------------" :left) => [3 3]
    (adjust-for-replacement [3 3] [3 3] [3 3] "\n\n\nxxx" :right) => [6 3])
  (fact "adjust-for-replacement adjusts column when l > b ∧ l line = b line"
    (adjust-for-replacement [3 5] [3 3] [3 3] "            " :left) => [3 17]))
