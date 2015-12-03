(ns avi.buffer.motion.word
  (:require [avi.buffer
              [lines :as lines]
              [locations :as l]]
            [avi.buffer.motion.resolve :as resolve]
            [schema.core :as s]))

(defn word-char?
  [ch]
  (or (Character/isAlphabetic (int ch))
      (Character/isDigit (int ch))
      (#{\_} ch)))

(s/defmethod resolve/resolve-motion :word :- (s/maybe l/Location)
  [{:keys [lines] [i j] :point} [_ _ [_ n]]]
  (->> (l/forward [i j] (lines/line-length lines))
    (drop-while (comp word-char? #(get-in lines %)))
    (drop-while (complement (comp word-char? #(get-in lines %))))
    first))