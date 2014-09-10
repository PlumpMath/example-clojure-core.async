(ns ex-presentation.core
  (:use [clojure.core.async :exclude [reduce take map into partition merge partition-by]])
  (:require [clojure.core.async :as async]
            [clojure.string     :as str]
            [org.httpkit.client :as http])
  (:gen-class))


(defn line [& worlds]
  (str (apply str worlds) "\n"))

(defn append-line [to & worlds]
  (swap! to str (apply line worlds)))



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

(do (thread (Thread/sleep 1000)
            (>!! ch "something"))
    (time (println (<!! ch))))

(close! ch)


;;;
;;; TITLE: inversion of control
;;;

(def ch (chan))
(def out (atom nil))

(go (reset! out (<! ch)))
(go (>! ch "something"))

(println @out)
(close! ch)


;;;
;;; TITLE: can't put the `nil' in the channel.
;;;

(def ch (chan))
(put! ch nil)


;;;
;;; TITLE:
;;;

(def ch (chan 1000))
(def out (atom nil))
(dotimes [i 1000]
  (>!! ch i))
(close! ch)

(dotimes [i 4]
  (thread (loop []
            (when-let [v (<!! ch)]
              (append-line out "thread-" (inc i) ": " v)
              (recur)))))

(println (count (str/split-lines @out)))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; CHANNEL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;
;;; TITLE: promise/future
;;;

(def a-promise (promise))
(deliver a-promise "something")
(println @a-promise)
(println @(future (Thread/sleep 1000)
                  "something"))

;;;
;;; TITLE: timeout channel
;;;

(do (def tch (timeout 1000))
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
(def out (atom ""))

(dotimes [i 2]
  (go (>! fch (str "something-" i))
      (append-line out "done " i)))

(println @out)
(println (<!! fch))
(println @out)

(println (<!! fch))

(close! fch)


;;;
;;; TITLE: dropping buffer
;;;

(def dch (chan (dropping-buffer 2)))
(def out (atom ""))

(dotimes [i 3] (put! dch (str "something-" (inc i))))
(go (dotimes [i 3]
      (append-line out (<! dch))))

(println @out)
(close! dch)


;;;
;;; TITLE: sliding buffer
;;;

(def sch (chan (sliding-buffer 2)))
(def out (atom ""))

(dotimes [i 3] (put! sch (str "something-" (inc i))))
(go (dotimes [i 3]
      (append-line out (<! sch))))

(println @out)
(close! sch)




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ALTS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;
;;; TITLE: alts
;;;

(def chs (repeatedly 2 chan))
(def out (atom ""))

(go-loop []
  (let [[v ch] (alts! chs)]
    (when-not (nil? v)
      (append-line out v " from " ch)
      (recur))))

(doseq [[idx ch] (map-indexed vector chs)]
  (put! ch (str "ch-" idx)))

(println @out)
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
(def out (atom ""))

(doseq [[idx ch] (map-indexed vector chs)]
  (tap m ch)
  (go-loop []
    (when-let [v (<! ch)]
      (append-line out "ch-" idx ": " v)
      (recur))))

(>!! bc-ch "something-1")
(println @out)

(>!! bc-ch "something-2")
(println @out)

(doseq [ch chs] (close! ch))
(close! bc-ch)


;;;
;;; TITLE: put/sub
;;;

(def to-pub (chan))
(def p (pub to-pub :tag))
(def ch-a (chan))
(def ch-b (chan))
(def out (atom ""))

(sub p :a ch-a)
(go-loop []
  (when-let [v (<! ch-a)]
    (append-line out "ch-a: " v)
    (recur)))

(sub p :b ch-b)
(go-loop []
  (when-let [v (<! ch-b)]
    (append-line out "ch-b: " v)
    (recur)))

(>!! to-pub {:tag :a :msg "tag-a"})
(println @out)

(>!! to-pub {:tag :b :msg "tag-b"})
(println @out)

(close! ch-a)
(close! ch-b)
(close! to-pub)




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; MERGE/TAKE/INTO ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;
;;; TITLE: merge
;;;

(def chs (repeatedly 10 chan))
(doseq [[idx ch] (map-indexed vector chs)]
  (put! ch idx))

(def ch (async/merge chs))
(dotimes [i 10]
  (println (<!! ch)))

(doseq [ch chs] (close! ch))
(close! ch)


;;;
;;; TITLE: take
;;;
(def ch (chan 15))
(dotimes [i 15]
  (>!! ch i))

(def take-ch (async/take 10 ch))
(dotimes [i 15]
  (println (<!! take-ch)))

(close! ch)


;;;
;;; TITLE: into
;;;
(def ch (chan 10))
(dotimes [i 10]
  (>!! ch i))
(close! ch)

(def into-ch (async/into [] ch))
(<!! into-ch)
