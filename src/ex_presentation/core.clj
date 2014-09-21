(ns ex-presentation.core
  ;; CIDER는 특정 네임스페이스 안에서 주어진 함수들을 실행하기 위해 모든 명령에 이 `ns'를 같이 실행시킨다.
  ;; `ns' 함수에는 기본적으로 "(refer 'clojure.core)"가 포함되어 `clojure.core' 네임스페이스와 중복되면 사라진다.
  ;; 아래와 같은 경우 `with-out-str' 매크로가 사리지는 문제가 발생하였다.
  ;; 임시적으로 문제 해결을 위해 "(:refer-clojure :exclude [with-out-str])"와 같이 `with-out-str'를 가져오지 못 하도록 지정하였다.
  (:refer-clojure :exclude [with-out-str])
  (:use [clojure.core.async :exclude [reduce take map into partition merge partition-by]])
  (:require [clojure.core.async :as async]
            [clojure.string     :as string]))


(throw (Exception. "Stop!"))

(defmacro with-out-str [& body]
  `(let [out# (java.io.StringWriter.)
         ~'println (fn [args#]
                     (binding [*out* out#]
                       (println args#)))]
     (binding [*out* out#]
       ~@body)
     (def ~'out out#)
     (str out#)))

(assert (contains? (into #{} (keys (ns-publics 'ex-presentation.core)))
                   'with-out-str))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; TAKE AND PUT ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;
;;; TITLE: take! and put!
;;;

(with-out-str
  (def ch (chan))         ; create new channel.
  (take! ch #(println %)) ; will remove(consume) a "something" in the channel.
  (put! ch "something")   ; put "something" in the channel.
  (close! ch)             ; close channel.
  )
;; Please, wait for evaluate the callback. It's done soon.
(str out)


;;;
;;; TITLE: limit
;;;

(def ch (chan))
(dotimes [i 1025] (take! ch (fn [_])))
(close! ch)

(def ch (chan))
(dotimes [i 1025] (put! ch "something"))
(close! ch)


;;;
;;; TITLE: Invoke the callback on the current thread when channel has data.
;;;

(def ch (chan))

(with-out-str (time (do (put! ch "something")
                        (take! ch (fn [v]
                                    (Thread/sleep 1000)
                                    (println v)))
                        (println "waiting until that callback is done!"))))
;; Please, wait for evaluate the callback. It's done soon.
(str out)

(with-out-str (time (do (put! ch "something abc")
                        (take! ch (fn [v]
                                    (Thread/sleep 1000)
                                    (println v))
                               false)
                        (println "Fixed that... Now, \"take!\" has returned immediately."))))
;; Please, wait for evaluate the callback. It's done soon.
(str out)

(close! ch)


;;;
;;; TITLE: <!! and >!!
;;;

(def ch (chan))

(do (thread (Thread/sleep 1000)
            (>!! ch "something")) ; ">!!" is blocked until to put "something" in the channel.
    (time (println (<!! ch)))
    (println "done"))

(do (thread (Thread/sleep 1000)
            (println (<!! ch))) ; "<!!" is blocked until to take "something" in the channel.
    (time (>!! ch "something"))
    (println "done"))

(close! ch)


;;;
;;; TITLE: <! and >!
;;;

(def ch (chan))
(def out (atom nil))

(>!! ch "something")                    ; Will be blocked.

(go (append-line out (<! ch))           ; "go macro" is use the parking instead of the blocking.
    (append-line out "invoke after \"<!\""))
(append-line out "invoke before \"<!\"")
(go (>! ch "something"))
(println @out)

(close! ch)


;;;
;;; TITLE: can't put the `nil' in the channel.
;;;

(def ch (chan))
(put! ch nil)


;;;
;;; TITLE: ... ("take"와 "put"은 대칭이다?)
;;;

(def ch (chan 1000))
(def out (atom nil))
(dotimes [i 1000] (>!! ch i))
(close! ch)

(dotimes [i 4]
  (thread (loop []
            (when-let [v (<!! ch)]
              (append-line out "thread-" (inc i) ": " v)
              (recur)))))

(println (count (string/split-lines @out)))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; CHANNEL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;
;;; TITLE: timeout channel
;;;

(do (def tch (timeout 1000))
    (put! tch "something")
    (println (<!! tch))
    (time (<!! tch)))     ; Channel will be closed after one second...


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

(println (<!! ch))                   ; Can read in the closed channel.




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; BUFFER ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;
;;; TITLE: fixed buffer
;;;

(def fch (chan 1))
(def out (atom ""))

(go (>! fch (str "something-1"))        ; invoke immediately.
    (append-line out "done 1"))

(println @out)

(go (>! fch (str "something-2")) ; invoke after taking a data in the channel.
    (append-line out "done 2"))

(println @out)

(do (append-line out (<!! fch))
    (println @out))

(println (<!! fch))

(close! fch)


;;;
;;; TITLE: dropping buffer
;;;

(def dch (chan (dropping-buffer 2)))
(def out (atom ""))

(dotimes [i 3]
  (>!! dch (str "something-" (inc i)))) ; It was not blocked by dropping the last data.

(go (dotimes [i 3]
      (append-line out (<! dch))))

(println @out)
(close! dch)


;;;
;;; TITLE: sliding buffer
;;;

(def sch (chan (sliding-buffer 2)))
(def out (atom ""))

(dotimes [i 3]
  (>!! sch (str "something-" (inc i)))) ; It was not blocked by dropping the first data.
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

(def ch-1 (chan))
(def ch-2 (chan))
(def out (atom ""))
(def ch-sym->name #({ch-1 "ch-1", ch-2 "ch-2"} %))

(go-loop []
  (let [[v ch] (alts! [ch-1 ch-2])]
    (when-not (nil? v)
      (append-line out v " from " (ch-sym->name ch))
      (recur))))

(>!! ch-1 "something-a")
(println @out)

(>!! ch-2 "something-b")
(println @out)

(doseq [ch [ch-1 ch-2]] (close! ch))


;;;
;;; TITLE: default value
;;;

(def ch (chan))

(put! ch "something")
(println (alts!! [ch] :default :nothing))

(close! ch)


;;;
;;; TITLE: priority
;;;

(def ch-1 (chan))
(def ch-2 (chan))

(dotimes [i 3] (put! ch-1 :1))
(dotimes [i 3] (put! ch-2 :2))
(dotimes [i 6] (println (alts!! [ch-1 ch-2])))

(dotimes [i 3] (put! ch-1 :1))
(dotimes [i 3] (put! ch-2 :2))
(dotimes [i 6] (println (alts!! [ch-1 ch-2] :priority true)))

(doseq [ch [ch-1 ch-2]] (close! ch))




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

(>!! bc-ch "something-a")
(println @out)

(>!! bc-ch "something-b")
(println @out)

(doseq [ch chs] (close! ch))
(close! bc-ch)


;;;
;;; TITLE: pub/sub
;;;

(def to-pub (chan))
(def p (pub to-pub :tag))
(def ch-1 (chan))
(def ch-2 (chan))
(def out (atom ""))

(sub p :1 ch-1)
(go-loop []
  (when-let [v (<! ch-1)]
    (append-line out "ch-1: " v)
    (recur)))

(sub p :2 ch-2)
(go-loop []
  (when-let [v (<! ch-2)]
    (append-line out "ch-2: " v)
    (recur)))

(>!! to-pub {:tag :1 :msg "tag-1"})
(println @out)

(>!! to-pub {:tag :2 :msg "tag-2"})
(println @out)

(close! ch-1)
(close! ch-2)
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
