(ns avi.core-test
  (:require [midje.sweet :refer :all]
            [avi.test-helpers :refer :all]))

(def ten-lines
  (str "One\nTwo\nThree\nFour\nFive\nSix\n"
       "Seven\nEight\nNine\nTen"))

(facts "regarding being started with a file"
  (fact "It starts with a named buffer with the file's contents."
    (editor)
     => (looks-like
          "One                 "
          "Two                 "
          "Three               "
          ".                   "
          "~                   " [:blue]
          "~                   " [:blue]
          "test.txt            " [:black :on :white]
          "                    "))
  (fact "It can display files longer than the screen."
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

(facts "regarding being started with no file"
  (fact "It starts with an empty, unnamed buffer."
    (editor :editing :nothing)
     => (looks-like
          "                    "
          "~                   " [:blue]
          "~                   " [:blue]
          "~                   " [:blue]
          "~                   " [:blue]
          "~                   " [:blue]
          "[No Name]           " [:black :on :white]
          "                    ")))

(facts "regarding being started with a non-existant file"
  (fact "It starts with an empty, named buffer."
    (editor :editing :not-found)
     => (looks-like
          "                    "
          "~                   " [:blue]
          "~                   " [:blue]
          "~                   " [:blue]
          "~                   " [:blue]
          "~                   " [:blue]
          "test.txt            " [:black :on :white]
          "                    ")))

(facts "regarding scrolling"
  (fact "line-wise cursor movement will keep the cursor in the viewport"
    (fact "can scroll down some lines"
      (editor :editing ten-lines :after "7j")
       => (looks-like
            "Three               "
            "Four                "
            "Five                "
            "Six                 "
            "Seven               "
            "Eight               "
            "test.txt            " [:black :on :white]
            "                    ")
      (cursor :editing ten-lines :after "7j") => [5 0])
        
    (fact "viewport stays when moving back up"
      (editor :editing ten-lines :after "7jk")
       => (looks-like
            "Three               "
            "Four                "
            "Five                "
            "Six                 "
            "Seven               "
            "Eight               "
            "test.txt            " [:black :on :white]
            "                    ")
      (cursor :editing ten-lines :after "7jk") => [4 0])

    (fact "can scroll up some lines"
      (editor :editing ten-lines :after "7j6k")
       => (looks-like
            "Two                 "
            "Three               "
            "Four                "
            "Five                "
            "Six                 "
            "Seven               "
            "test.txt            " [:black :on :white]
            "                    ")
      (cursor :editing ten-lines :after "7j6k") => [0 0])))

(facts "regarding quitting"
  (fact "It doesn't start in the 'finished' state."
    (:mode (editor)) =not=> :finished
    (:mode (editor :after ":")) =not=> :finished
    (:mode (editor :after ":q")) =not=> :finished)
  (fact "It exits after `:q<CR>`."
    (:mode (editor :after ":q\r")) => :finished))

(facts "regarding screen resizes"
  (fact "It updates the editor size."
    (:size (editor :after [:resize [17 42]])) => [17 42])
  (fact "It updates the buffer's size."
    (editor :editing ten-lines :after [:resize [12 20] \G])
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
  (fact "It adjusts the viewport for the cursor."
    (cursor :editing ten-lines :after [\G :resize [5 20]]) => [2 0]))