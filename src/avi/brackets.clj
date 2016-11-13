(ns avi.brackets
  (:require [avi.pervasive :refer :all]
            [packthread.core :refer :all]
            [packthread.lenses :as l]
            [avi.beep :as beep]
            [avi.edit-context :as ec]
            [avi.edit-context
             [lines :as lines]
             [locations :as loc]]
            [avi.editor :as e]))

(def ^:private bracket-map
  {\( \)
   \[ \]
   \{ \}})

(def ^:private reverse-bracket-map
  (->> bracket-map
       (map (fn [[a b]] [b a]))
       (into {})))

(def ^:private open-brackets (into #{} (keys bracket-map)))
(def ^:private brackets (into #{} (concat (keys bracket-map) (vals bracket-map))))

(defn- go-to-matching-bracket
  [{[i j] :avi.lenses/point :as edit-context}]
  (let [lines (ec/lines edit-context)]
   (+> edit-context
     (let [bracket (get-in lines [i j])
           open-bracket? (open-brackets bracket)
           scan (if open-bracket?
                  (loc/forward [i j] (lines/line-length lines))
                  (loc/backward [i j] (lines/line-length lines)))
           brackets (if open-bracket?
                      bracket-map
                      reverse-bracket-map)
           new-point (->> scan
                           (reductions
                             (fn [stack [i j]]
                               (let [char (get-in lines [i j])]
                                 (cond-> stack
                                   (get brackets char) (conj (get brackets char))
                                   (= char (first stack)) rest)))
                             ())
                           (drop 1)
                           (map vector scan)
                           (drop-while #(not (empty? (second %))))
                           first
                           first)]
       (cond
         (not (brackets bracket))
         beep/beep

         (not new-point)
         beep/beep

         :else
         (assoc :avi.lenses/point new-point))))))

(def normal-commands
  {"%" (fn+> [editor _]
         (in e/edit-context
           go-to-matching-bracket))})
