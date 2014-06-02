(ns avi.insert-mode
  (:require [packthread.core :refer :all]
            [avi.editor :as e]
            [avi.buffer :as b]
            [avi.eventmap :as em]))

(def eventmap
  (em/eventmap
    ("<Esc>"
      [editor]
      (+> editor
          (let [b (e/current-buffer editor)
                [i j] (:cursor b)
                new-j (max (dec j) 0)]
            (in e/current-buffer
                (b/move-cursor [i new-j] new-j)))
          (e/enter-mode :normal)))

    ("<BS>"
      [editor]
      (+> editor
          (let [[i j] (:cursor (e/current-buffer editor))]
            (if (= [0 0] [i j])
              e/beep
              (in e/current-buffer
                  (b/backspace))))))

    ("<Enter>"
      [editor]
      (+> editor
          (in e/current-buffer
              (b/insert-text "\n"))))

    (:else
      [editor event]
      (+> editor
          (let [[event-type event-data] event]
            (if-not (= event-type :keystroke)
              e/beep
              (in e/current-buffer
                  (b/insert-text event-data))))))))

(defmethod e/respond :insert
  [editor event]
  (em/invoke-event-handler eventmap editor event))

(defmethod e/enter-mode :insert
  [editor mode]
  (+> editor
      (assoc :mode :insert,
             :status-line "--INSERT--")))
