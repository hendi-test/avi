(ns avi.edit-context.transactions
  (:require [avi.beep :as beep]
            [packthread.core :refer :all]))

(defn start-transaction
  [{lines :lines,
    point :point,
    :as edit-context}]
  (when (:avi.document/in-transaction? edit-context)
    (throw (Exception. "attempt to nest a transaction")))
  (+> edit-context
    (update-in [:undo-log] conj {:lines lines, :point point})
    (assoc :avi.document/in-transaction? true)))

(defn commit
  [edit-context]
  (+> edit-context
      (assoc :avi.document/in-transaction? false
             :redo-log ())))
