(ns avi.buffer
  (:import [java.io FileNotFoundException])
  (:require [packthread.core :refer :all]
            [clojure.string :as string]
            [avi.pervasive :refer :all]
            [avi.string :as s]
            [avi.world :refer :all]))

(defn- try-load
  [filename]
  (try
    (string/split (read-file *world* filename) #"\n")
    (catch FileNotFoundException e
      [""])))

(defn open
  [filename height]
  (let [lines (if filename
                (try-load filename)
                [""])]
    {:name filename,
     :viewport-top 0
     :viewport-height height
     :lines lines,
     :cursor [0 0],
     :last-explicit-j 0
     :undo-log ()}))

;; --

(defn write
  [{lines :lines,
    filename :name}]
  (write-file *world* filename (string/join "\n" lines)))

(defn- adjust-viewport-to-contain-cursor
  [buffer]
  (+> buffer
    (let [height (:viewport-height buffer)
          viewport-top (:viewport-top buffer)
          viewport-bottom (dec (+ viewport-top height))
          [cursor-i] (:cursor buffer)]
        (cond
          (< cursor-i viewport-top)
          (assoc :viewport-top cursor-i)

          (> cursor-i viewport-bottom)
          (assoc :viewport-top (inc (- cursor-i height)))))))

(defn line
  [buffer i]
  (get-in buffer [:lines i]))

(defn j-within-line
  [buffer i]
  (let [j (:last-explicit-j buffer)
        line-length (count (line buffer i))
        j-not-after-end (min (dec line-length) j)
        j-within-line (max 0 j-not-after-end)]
    j-within-line))

(defn line-count
  [buffer]
  (count (:lines buffer)))

(defn move-to-line
  [buffer i]
  {:pre [(>= i 0) (< i (line-count buffer))]}
  (+> buffer
      (assoc :cursor [i (j-within-line buffer i)])
      (adjust-viewport-to-contain-cursor)))

(defn- adjust-cursor-to-viewport
  [buffer]
  (+> buffer
      (let [height (:viewport-height buffer)
            viewport-top (:viewport-top buffer)
            viewport-bottom (dec (+ viewport-top height))
            [cursor-i] (:cursor buffer)]
        (cond
          (< cursor-i viewport-top)
          (move-to-line viewport-top)

          (> cursor-i viewport-bottom)
          (move-to-line viewport-bottom)))))

(defn move-cursor
  [buffer cursor & [j]]
  (+> buffer
      (assoc :cursor cursor)
      (if j
        (assoc :last-explicit-j j))
      adjust-viewport-to-contain-cursor))

(defn resize
  [buffer height]
  (+> buffer
      (assoc :viewport-height height)
      (adjust-viewport-to-contain-cursor)))

(defn scroll
  [buffer scroll-fn]
  (+> buffer
      (update-in [:viewport-top] scroll-fn)
      (adjust-cursor-to-viewport)))

(defn on-last-line?
  [buffer]
  (let [[i] (:cursor buffer)
        line-count (line-count buffer)]
    (= i (dec line-count))))

(defn- clamp-viewport-top
  [{top :viewport-top,
    height :viewport-height,
    :as buffer}
   new-top]
  (let [line-count (line-count buffer)
        max-top (max 0 (- line-count height))]
    (min max-top (max 0 new-top))))

(defn- clamp-cursor-row
  [{top :viewport-top,
    height :viewport-height,
    :as buffer}
   new-top]
  (max 0 (min (dec (line-count buffer)) new-top)))

(defn move-and-scroll-half-page
  [{top :viewport-top,
    height :viewport-height,
    [i] :cursor,
    :as buffer}
   which-way]
  (+> buffer
      (let [distance (quot height 2)
            direction (case which-way
                        :down +1
                        :up -1)
            scroll-adjust (* direction distance)]
        (move-to-line (clamp-cursor-row buffer (+ i scroll-adjust)))
        (scroll (constantly (clamp-viewport-top buffer (+ top scroll-adjust)))))))

(defn cursor-to-bottom-of-viewport
  [{top :viewport-top,
    height :viewport-height,
    :as buffer}
   count-from-bottom]
  (+> buffer
      (let [bottom-of-viewport (dec (+ top height))
            bottom-of-file (dec (line-count buffer))
            count-from-bottom-of-viewport (- bottom-of-viewport count-from-bottom)
            count-from-bottom-of-file (- bottom-of-file count-from-bottom)
            new-line (max top (min count-from-bottom-of-viewport count-from-bottom-of-file))]
        (move-to-line new-line))))

(defn cursor-to-top-of-viewport
  [{top :viewport-top,
    :as buffer}
   count-from-top]
  (move-to-line buffer (+ top count-from-top)))

(defn cursor-to-middle-of-viewport
  [{top :viewport-top,
    height :viewport-height,
    :as buffer}]
  (+> buffer
      (let [middle-of-viewport (dec (+ top (quot height 2)))
            middle-of-file (quot (dec (line-count buffer)) 2)
            new-line (min middle-of-viewport middle-of-file)]
        (move-to-line new-line))))

;; Changes, undo, redo

(defn start-transaction
  [{lines :lines,
    cursor :cursor,
    :as buffer}]
  (+> buffer
    (update-in [:undo-log] conj {:lines lines, :cursor cursor})))

(defn- change
  [{lines :lines,
    cursor :cursor,
    :as buffer} modify-lines-fn]
  (+> buffer
    start-transaction
    (assoc :lines (modify-lines-fn lines))))

(defn undo
  [{undo-log :undo-log, :as buffer}]
  (if-not (seq undo-log)
    (fail :beep "Already at the oldest change")
    (+> buffer
      (merge (first (:undo-log buffer)))
      (update-in [:undo-log] rest)
      adjust-viewport-to-contain-cursor)))

;; -- changing buffer contents --

(defn- update-line
  [buffer i modify-fn]
  (change buffer #(update-in % [i] modify-fn)))

(defn insert-text
  [{[i j] :cursor,
    lines :lines,
    :as buffer} text]
  (+> buffer
      (let [original-line (get-in buffer [:lines i])
            resulting-text (splice original-line j j text)
            new-lines (string/split resulting-text #"\n" -1)
            resulting-i (+ i (dec (count new-lines)))
            resulting-j (if (= 1 (count new-lines))
                          (+ j (count text))
                          0)]
        (update-in [:lines] #(splice % i (inc i) new-lines))
        (move-cursor [resulting-i resulting-j] resulting-j))))

(defn insert-blank-line
  [{[i] :cursor,
    lines :lines,
    :as buffer} new-line-i]
  (+> buffer
      (change #(splice % new-line-i new-line-i [""]))))

(defn delete-char-under-cursor
  [{[i j] :cursor,
    :as buffer}]
  (+> buffer
      (update-line i #(splice % j (inc j)))))

(defn delete-current-line
  [{[i] :cursor,
    lines :lines,
    :as buffer}]
  (+> buffer
      (if (= 1 (line-count buffer))
        (do
          (change (constantly [""]))
          (move-cursor [0 0] 0))
        (let [new-lines (splice lines i (inc i))
              new-i (if (= i (dec (line-count buffer)))
                      (dec i)
                      i)
              target-line (get new-lines new-i)
              new-j (s/index-of-first-non-blank target-line)]
          (change (constantly new-lines))
          (move-cursor [new-i new-j])))))

(defn- backspace-at-beginning-of-line
  [{[i j] :cursor,
    lines :lines,
    :as buffer}]
  (+> buffer
      (let [new-line (str (get lines (dec i)) (get lines i))
            new-lines (splice lines (dec i) (inc i) [new-line])
            i (dec i)
            j (count (get lines i))]
        (move-cursor [i j] j)
        (change (constantly new-lines)))))

(defn backspace
  [{[i j] :cursor,
    lines :lines,
    :as buffer}]
  (+> buffer
      (if (= 0 j)
        (backspace-at-beginning-of-line)
        (do
          (move-cursor [i (dec j)])
          (update-line i #(splice % (dec j) j))))))
