(ns avi.core-test
  (:require [midje.sweet :refer :all]
            [avi.test-helpers :refer :all]))

(facts "regarding displaying in the terminal"
  (fact "It can display buffer content longer than will fit in buffer viewport."
    (editor :editing ten-lines)
     => (looks-like
          "One                 "
          "Two                 "
          "Three               "
          "Four                "
          "Five                "
          "Six                 "
          "test.txt            " [:black :on :white]
          "                    ")))

(facts "regarding resizing of terminal window"
  (fact "When the terminal window is resized, it updates the editor viewport size."
    (:size (editor :after "<Resize [17 42]>")) => [17 42])
  (fact "When the terminal window is resized, it updates the buffer viewport size."
    (editor :editing ten-lines :after "<Resize [12 20]>G")
     => (looks-like
          "One                 "
          "Two                 "
          "Three               "
          "Four                "
          "Five                "
          "Six                 "
          "Seven               "
          "Eight               "
          "Nine                "
          "Ten                 "
          "test.txt            " [:black :on :white]
          "                    "))
  (fact "When the terminal window is resized, the cursor stays inside the buffer viewport."
    (cursor :editing ten-lines :after "G<Resize [5 20]>") => [2 0]))
