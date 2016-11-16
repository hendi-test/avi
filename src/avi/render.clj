(ns avi.render
 (:import [java.util Arrays])
 (:require [clojure.set :refer [map-invert]]
           [avi.editor :as e]
           [avi.edit-context.lines :as lines]
           [avi.color :as color]
           [avi.layout :as layout]
           [avi.layout.panes :as p]))

(defn- point-position
  [{:keys [:avi.editor/mode] :as editor}]
  (if (= mode :command-line)
    (let [[_ [height]] (::layout/shape editor)]
      [(dec height) (inc (count (:command-line editor)))])
    (p/point-position editor)))

(defn copy-blit!
  [{rendition-width :width,
    rendered-chars :chars,
    rendered-attrs :attrs}
   {:keys [::width ::foreground ::background ::text]
    [i j] ::position}]
  (let [start (+ j (* i rendition-width))
        text-size (count text)
        attrs (color/make foreground background)]
    (.getChars text 0 (min width text-size) rendered-chars start)
    (Arrays/fill rendered-attrs start (+ start width) attrs)))

(defn fill-rendition-line!
  [{:keys [width] rendered-chars :chars, rendered-attrs :attrs :as rendition} n [[i j] [rows cols]] [attrs text]]
  (let [start (+ j (* (+ n i) width))
        text-size (count text)]
    (.getChars text 0 (min cols text-size) rendered-chars start)
    (Arrays/fill rendered-attrs start (+ start cols) attrs)))

(defmethod layout/render! ::p/pane
  [editor rendition {:keys [::p/lens] [[i j] [rows cols] :as shape] ::layout/shape}]
  (let [from-line i
        to-line (dec (+ i rows))
        {:keys [:avi.lenses/viewport-top]
         document-number :avi.lenses/document} (get-in editor [:avi.lenses/lenses lens])
        document (get-in editor (e/current-document-path editor))
        text (get-in editor [:avi.documents/documents document-number :avi.documents/text])
        lines (lines/content text)]
    (doseq [i (range (inc (- to-line from-line)))]
      (let [document-line (get lines (+ i viewport-top))
            line-color (if document-line
                         (color/make :white :black)
                         (color/make :blue :black))
            line-text (or document-line "~")]
        (fill-rendition-line! rendition i shape [line-color line-text])))
    (let [file-name (or (:avi.documents/name document) "[No Name]")
          {:keys [:avi.lenses/viewport-top] [i j] :avi.lenses/point} (get-in editor [:avi.lenses/lenses lens])
          num-lines (count lines)
          pos-txt (if (= viewport-top 0)
                    (str "Top")
                    (if-not (< (+ viewport-top (dec rows)) num-lines)
                      (str "End")
                      (str (int (/ (* viewport-top 100) (- num-lines (dec rows)))) "%")))
          status-txt (str "  [" (inc i) "," (inc j) "]  " pos-txt)
          filelen (- cols (count status-txt))
          fmt-str (if (> filelen 0) (str "%-"filelen"."filelen"s" ) (str "%s"))
          msg-txt (str (format fmt-str file-name) status-txt)]
       (fill-rendition-line! rendition (dec rows) shape [(color/make :black :white) (str msg-txt)]))))

(defmethod layout/render! ::p/vertical-bar
  [editor rendition {[[i j] [rows cols] :as shape] ::layout/shape}]
  (doseq [n (range rows)]
    (copy-blit! rendition {::position [(+ i n) j]
                           ::width cols
                           ::text "|"
                           ::foreground :black
                           ::background :white})))

(defn render-message-line!
  [editor rendition]
  (let [[_ [rows cols]] (::layout/shape editor)
        i (dec rows)
        blit (merge (cond
                      (and (:prompt editor) (:command-line editor))
                      {::position [i 0]
                       ::width cols
                       ::text (str (:prompt editor) (:command-line editor))
                       ::foreground :white
                       ::background :black}

                      (:message editor)
                      (let [[foreground background text] (:message editor)]
                        {::position [i 0]
                         ::width cols
                         ::text text
                         ::foreground foreground
                         ::background background})))]
    (when blit
      (copy-blit! rendition blit))))

(defn render
  [editor]
  (let [[_ [height width]] (::layout/shape editor)
        default-attrs (color/make :white :black)
        rendered-chars (char-array (* height width) \space)
        rendered-attrs (byte-array (* height width) default-attrs)
        rendition {:width width
                   :chars rendered-chars
                   :attrs rendered-attrs
                   :point (point-position editor)}]
    (run!
      #(layout/render! editor rendition %)
      (eduction layout/all-renderables [editor]))
    (render-message-line! editor rendition)
    rendition))

(defn rendered
  [editor]
  (assoc editor :rendition (render editor)))

(defn wrap
  [responder]
  (fn [editor event]
    (-> editor
      (responder event)
      rendered)))
