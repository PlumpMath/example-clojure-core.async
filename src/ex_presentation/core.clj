(ns ex-presentation.core
  (:refer-clojure :exclude [println])
  (:use [clojure.core.async :exclude [reduce take map into partition merge partition-by]])
  (:require [clojure.core.async :as async]
            [clojure.string     :as string]))


(throw (Exception. " REPL stop!"))

;;; NOTE:
;;;  Emacs + CIDER 조합을 사용하면, 출력이 분산되는 문제가 있어서 CIDER 버퍼로 출력을 모으는 꼼수.
(let [out *out*]
  (defn println [& more]
    "Redirecting output to CIDER."
    (binding [*out* out]
      (apply clojure.core/println more))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; TAKE AND PUT ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;
;;; TITLE: "take!" and "put!" function.
;;;

(do
  (def ch (chan))         ; 채널을 생성된다.
  (take! ch #(println %)) ; 채널에 데이터가 입력되면 등록된 -여기서는 "#(println %)"- 콜백이 실행된다.
  (put! ch "something")   ; 채널에 데이터를 입력한다.
  (close! ch)             ; 채널을 닫는다.
  )


;;;
;;; TITLE: Limitation of callbacks in the channel.
;;;

(let [ch (chan)]
  ;; 채널에서 기다리는 콜백의 개수가 지정된 숫자를 넘으면 예외가 발생한다.
  (dotimes [i 1025] (take! ch (fn [_]))))

(let [ch (chan)]
  ;; 채널에 데이터를 삽입하는 "put!" 함수도 콜백이 있다.
  ;; 디폴트로 아무 일도 안하는 함수가 전달된다.
  (dotimes [i 1025] (put! ch "something")))


;;;
;;; TITLE: Invoke the callback on the current thread when channel has data.
;;;

(let [ch (chan)]
  (time
    (do
      ;; 채널의 기본 동작은, 데이터를 있을 경우 "take" 콜백을 등록한 스레드에서 바로 실행된다.
      ;; 여러모로 성능상 이점이 있을 거라고 생각되지만, 사용에 유의해야 할 것 같다.
      (put! ch "something")
      (take! ch (fn [v]
                  (Thread/sleep 1000)
                  (println v)))
      (println "waiting until that callback is done!"))))

(let [ch (chan)]
  (time
    (do
      ;; "on-caller"를 false로 전달하면 "take" 콜백을 비동기적으로 실행 할 수 있다.
      (put! ch "something")
      (take! ch
             (fn [v]
               (Thread/sleep 1000)
               (println v))
             false)
      (println "waiting until that callback is done!"))))


;;;
;;; TITLE: "<!!" and ">!!" function.
;;;

(let [ch (chan)]
  ;; 끝에 "!!"가 붙은 함수들은 블럭킹이 되는, 즉 동기적으로 실행됩니다.
  ;; 스레드에서 1초간 기다리기 때문에 가장먼저 실행되는 함수는 let 폼의 마지막 줄의 "<!!" 함수다.
  ;; "<!!" 함수는 동기적으로 동작하기 때문에 채널에 소비할 데이터가 있을 때 까지 블럭킹 된다.
  ;; 즉, thrad 폼의 마지막 줄에 있는 ">!!" 함수가 실행될 까지 대기한다.
  ;; 그래서 "<!!" 함수를 처리하는데 1초가 넘는 시간이 걸리게 된다.
  (thread
    (Thread/sleep 1000)
    (println "Before to send data to channel.")
    ;; ">!!" 함수는 "promise"를 "put" 콜백으로 넘겨주고, 이 "promise"에 값이 전달될 때 까지 대기한다.
    ;; "put" 콜백이 실행되면 "deliver" 함수를 호출해 "promise"가 릴리즈 된다.
    (>!! ch "something"))
  (println "Before to receive data from channel.")
  (time (println "Transmitted data from channel:" (str "\"" (<!! ch) "\"")))
  (println "Done transaction."))

(let [ch (chan)]
  ;; ">!!"와 "<!!" 함수가 호출되는 순서를 변경하여도 기본적으로 위 예제와 동일하다.
  (thread
    (Thread/sleep 1000)
    (println "Before to receive data from channel.")
    ;; "<!!" 함수 역시 ">!!" 함수와 유사하게 동작한다.
    (println "Transmitted data from channel:" (str "\"" (<!! ch) "\"")))
  (println "Before to send data to channel.")
  (time (>!! ch "something"))
  (println "Done transaction."))


;;;
;;; TITLE: "<!" and ">!" function.
;;;

(let [ch (chan)]
  (go
    (println "Transmitted data from channel:" (str "\"" (<! ch) "\""))
    (println "Invoke after \"<!\""))

  (println "Invoke before \"<!\"")

  (go (>! ch "something")))


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
