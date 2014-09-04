(ns ex-presentation.core
  (:require [clojure.core.async :as async])
  (:gen-class))


(defn example-take-and-put [v]
  (let [ch (async/chan)]
    (async/take! ch #(println %))
    (async/put! ch v)))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ;; TODO:
  ;;  - Please run the examples.
  (example-take-and-put "test"))
