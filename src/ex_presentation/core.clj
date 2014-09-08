(ns ex-presentation.core
  (:use [clojure.core.async :exclude [reduce take map into partition merge partition-by]])
  (:require [clojure.core.async :as async]
            [clojure.pprint :refer [pprint]])
  (:gen-class))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; TAKE AND PUT ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;
;;; TITLE: non-blocking
;;;

(def ch (chan))
(def out (atom nil))

(take! ch #(reset! out (str %)))
(put! ch "something")

;; Wait for invoke the callback of `take!'
(println @out)
(close! ch)


;;;
;;; TITLE: blocking
;;;

(def ch (chan))

(do
  (thread
    (Thread/sleep 1000)
    (>!! ch "something"))
  (time (println (<!! ch))))

(close! ch)


;;;
;;; TITLE: inversion of control
;;;

(def ch (chan))

(go (println (<! ch)))
(go (>! ch "something"))

(close! ch)


;;;
;;; TITLE: can't put the `nil' in the channel.
;;;

(def ch (chan))
(put! ch nil)




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; CHANNEL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;
;;; TITLE: timeout channel
;;;

(do
  (def tch (timeout 1000))
  (put! tch "something")
  (println (<!! tch))
  ;; Channel will be closed after one second...
  (time (<!! tch)))


;;;
;;; TITLE: `thread' return the channel.
;;;

(def result-ch (thread
                 (Thread/sleep 1000)
                 "result of `thread' block"))

(println (<!! result-ch))
(close! result-ch)


;;;
;;; TITLE: `go' return the channel.
;;;

(def result-ch (go
                 (Thread/sleep 1000)
                 "result of `go' block"))

(println (<!! result-ch))
(close! result-ch)


;;;
;;; TITLE: closed channel
;;;

(def ch (chan))
(put! ch "something")
(close! ch)

;; Can read in the closed channel.
(println (<!! ch))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; BUFFER ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;
;;; TITLE: fixed buffer
;;;

(def fch (chan 1))
(dotimes [i 2]
  (go (>! fch (str "something-" i))
      (println (str "done " i))))

(println (<!! fch))
(println (<!! fch))
(close! fch)


;;;
;;; TITLE: dropping buffer
;;;

(def dch (chan (dropping-buffer 2)))

(dotimes [i 3] (put! dch (str "something-" (inc i))))
(go (dotimes [i 3]
      (println (<! dch))))

(close! dch)


;;;
;;; TITLE: sliding buffer
;;;

(def sch (chan (sliding-buffer 2)))

(dotimes [i 3] (put! sch (str "something-" (inc i))))
(go (dotimes [i 3]
      (println (<! sch))))

(close! sch)




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ALTS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;
;;; TITLE: ...
;;;

(def chs (repeatedly 2 chan))

(go-loop []
  (let [[v ch] (alts! chs)]
    (when-not (nil? v)
      (println (str v " from " ch))
      (recur))))

(doseq [[idx ch] (map-indexed vector chs)]
  (put! ch (str "something-" idx)))

(doseq [ch chs] (close! ch))


;;;
;;; TITLE: default value
;;;

(def ch (chan))

(put! ch "something")
(println (alts!! [ch] :default :nothing))

(close! ch)




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; MULT AND PUB ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;
;;; TITLE: mult/tab
;;;

(def bc-ch (chan))
(def m (mult bc-ch))
(def chs (repeatedly 2 chan))

(doseq [[idx ch] (map-indexed vector chs)]
  (tap m ch)
  (go-loop []
    (when-let [v (<! ch)]
      (println (str "ch-" idx ": " v))
      (recur))))

(>!! bc-ch "something-1")
(>!! bc-ch "something-2")

(doseq [ch chs] (close! ch))
(close! bc-ch)


;;;
;;; TITLE: put/sub
;;;

(def to-pub (chan))
(def p (pub to-pub :tag))
(def ch-a (chan))
(def ch-b (chan))

(sub p :a ch-a)
(go-loop []
  (when-let [v (<! ch-a)]
    (println "ch-a" v)
    (recur)))

(sub p :b ch-b)
(go-loop []
  (when-let [v (<! ch-b)]
    (println "ch-b" v)
    (recur)))

(>!! to-pub {:tag :a :msg "tag-a"})
(>!! to-pub {:tag :b :msg "tag-b"})

(close! ch-a)
(close! ch-b)
(close! to-pub)




(def chs (repeatedly 100 chan))
(doseq [[idx ch] (map-indexed vector chs)]
  (go (>! ch (str "something-" idx))))

(go-loop []
  (let [[v ch] (alts! chs)]
    (when-not (nil? v)
      (assert (re-matches #"something-[0-9]+" v))
      (println v)
      (recur))))

(doseq [ch chs] (close! ch))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  )
