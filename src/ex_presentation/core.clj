(ns ex-presentation.core
  (:refer-clojure :exclude [println time])
  (:use [clojure.core.async :exclude [reduce take map into partition merge partition-by]])
  (:require [clojure.core.async :as async]
            [clojure.string     :as string]))

;;; NOTE:
;;;  Emacs + CIDER 조합을 사용하면, 출력이 분산되는 문제가 있어서 CIDER 버퍼로 출력을 모으는 꼼수.
;;;  그리고, 여러 스레드에서 로그를 출력하다 보니 경쟁 상태가 발생해 "agent"를 사용해 상호배제 시킴.
(def println (let [ag (agent nil)
                   out *out*]
               (fn [& more]
                 (binding [*out* out]
                   (send-off ag #(apply clojure.core/println (rest (flatten %&))) more)
                   ;; Wait for agent's job finish.
                   (await ag)
                   nil))))

;;; NOTE:
;;;  time 매크로의 출력이 위 println함수와 "*out*"을 두고 경쟁 상태가 발생해서 랩핑 했음.
(defmacro time [& body]
  `(let [ret# (atom nil)]
     (println (with-out-str
                (reset! ret# (clojure.core/time ~@body))))
     @ret#))


(println "Working thread-safe \"println\" function!")

(throw (Exception. " REPL stop!"))



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

;; => something



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
      ;; "take!"는 비동기적으로 실행될 거라고 예상 하지만, 틀릴 수 있다는 점은 유의해야 한다.
      (put! ch "something")
      (take! ch (fn [v]
                  (Thread/sleep 1000)
                  (println v)))
      (println "Waiting until that callback is done!"))))

;; => something
;; => Waiting until that callback is done!
;; => "Elapsed time: 1006.583748 msecs"


(let [ch (chan)]
  (time
    (do
      ;; "on-caller"를 false로 전달하면 콜백을 비동기적으로 실행 할 수 있다.
      (put! ch "something")
      (take! ch
             (fn [v]
               (Thread/sleep 1000)
               (println v))
             false)
      (println "Done!"))))

;; => Done!
;; => "Elapsed time: 0.835143 msecs"
;; => something


;;;
;;; TITLE: "<!!" and ">!!" function.
;;;

(let [ch (chan)]
  ;; 끝에 "!!"가 붙은 함수들은 블럭킹이 되는, 즉 동기적으로 실행되는 함수들이다.
  ;; "<!!"는 "take!" 함수의 콜백이, ">!!"는 "put!" 함수의 콜백이 실행될 때 까지 블럭킹 된다.
  (thread
    ;; "<!!" 함수를 먼저 실행 시키기 위해서 1초간 대기한다.
    (Thread/sleep 1000)
    (>!! ch "something"))

  (time
    (let [output (<!! ch)]
      (println "Transmitted data from channel:" (str "\"" output "\"")))))

;; => Transmitted data from channel: "something"
;; => "Elapsed time: 1004.049221 msecs"



;;;
;;; TITLE: "go" block, "<!" and ">!" function.
;;;

(let [ch (chan)]
  ;; "go" (매크로)블럭은 비동기적으로 실행된다.
  ;; "go" 블럭 안에어서는 "<!!"와 ">!!" 함수 대신 블럭킹 되는 것 같이 행동하는 "<!"와 ">!" 함수를 사용해야 한다.
  ;; 블럭킹 되는 것 같이 행동하는 것을 "파킹"이라고 부른다.
  (time
    (go
      (let [current (. java.lang.System (clojure.core/nanoTime))]
        (Thread/sleep 1000)
        (>! ch current))))

  (time
    (go
      ;; NOTE:
      ;;  go 매크로 안에서 커스텀 time 매크로가 사용이 불가해서 time 대신에 전달 시간을 보여줌.
      (let [send-time (<! ch)
            receive-time (. java.lang.System (clojure.core/nanoTime))
            delivery-time (/ (double (- receive-time send-time)) 1000000.0)]
        (println "Delivery time:" delivery-time "msecs")))))

;; => "Elapsed time: 0.126524 msecs"
;; => "Elapsed time: 0.125212 msecs"
;; => Delivery time: 1002.052713 msecs



;;;
;;; TITLE: can't put the `nil' in the channel.
;;;

(let [ch (chan)]
  ;; "nil"은 특별한 의미를 부여받은 심볼으로 채널에 삽입 할수 없다.
  (try
    (put! ch nil)
    (catch Exception e
      (println "caught exception: " (.getMessage e)))))

;; => caught exception:  Can't put nil on channel




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; CHANNEL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;
;;; TITLE: timeout channel
;;;

(let [tch (timeout 1000)]
  ;; 채널으로 데이터 입력이 없어도, 1초 후에 채널이 닫힌다.
  (time
    (<!! tch)))

;; => "Elapsed time: 1004.280797 msecs"



;;;
;;; TITLE: `thread' return the channel.
;;;

;; "thread"는 채널을 반환하고 채널에 "thread" body의 반환값을 전달한다.
(let [ch (thread
           (Thread/sleep 1000)
           "result of `thread' block")]
  (println "Waiting until `thread' is done.")
  (println (<!! ch)))

;; => Waiting until `thread' is done.
;; => result of `thread' block



;;;
;;; TITLE: `go' return the channel.
;;;

;; "go" 블럭도 채널을 반환한다.
(let [ch (go
           (Thread/sleep 1000)
           "result of `go' block")]
  (println "Waiting until `go' block is done.")
  (println (<!! ch)))

;; => Waiting until `go' block is done.
;; => result of `go' block



;;;
;;; TITLE: closed channel
;;;

(let [ch (chan)]
  ;; 이미 삽입된 데이터는 채널이 닫혔어도 전달 받을 수 있다.
  (put! ch "something from closed channel.")
  (close! ch)
  (println (<!! ch)))

;; => something from closed channel.




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; BUFFER ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;
;;; TITLE: fixed buffer
;;;

(let [buf-size 1
      ch (chan buf-size)]
  ;; core.async는 디폴트로 3가지 종류의 버퍼를 지원한다.
  ;; 이 예제에서는 가장 기본적인 fixed buffer를 사용한다.
  ;; 버퍼 개수를 "chan" 함수에 넘겨주면 그 만큼 데이터를 채널에 쌓을 수 있다.
  (go
    (>! ch "1. \">!\" insert a data to the channel without parking.")
    (println "1. Done!"))

  (go
    (>! ch (str "2. \">!\" insert a data to the channel with parking.\n"
                "   Parking until take a data in the channel."))
    (println "2. Done!"))

  (Thread/sleep 1000)
  (println (<!! ch))
  (Thread/sleep 1000)
  (println (<!! ch)))

;; => 1. Done!
;; => 1. ">!" insert a data to the channel without parking.

;; => 2. Done!
;; => 2. ">!" insert a data to the channel with parking.
;; =>    Parking until take a data in the channel.



;;;
;;; TITLE: dropping buffer
;;;

;; "dropping-buffer"는 버퍼의 공간이 없는 경우 채널로 들어오는 데이터를 버린다.
;; 버퍼 크기가 2인 채널에 데이터를 3개 넣은 경우 세번째 넣은 데이터는 버려진다.
(let [times 3
      buf-size 2
      buf (dropping-buffer buf-size)
      ch (chan buf)]
  (dotimes [i times]
    (>!! ch (str "something-" (inc i))))
  (go
    (dotimes [i times]
      (println "Try" (str (inc i) "..."))
      (println (<! ch)))))

;; => Try 1...
;; => something-1
;; => Try 2...
;; => something-2
;; => Try 3...



;;;
;;; TITLE: sliding buffer
;;;

;; "sliding-buffer"는 버퍼의 공간이 없는 경우 채널에서 가장 오래된 데이터를 버린다.
;; 버퍼 크기가 2인 채널에 데이터를 3개 넣은 경우 첫번째 넣은 데이터는 버려진다.
(let [times 3
      buf-size 2
      buf (sliding-buffer 2)
      ch (chan buf)]
  (dotimes [i times]
    (>!! ch (str "something-" (inc i))))
  (go
    (dotimes [i times]
      (println "Try" (str (inc i) "..."))
      (println (<! ch)))))

;; => Try 1...
;; => something-2
;; => Try 2...
;; => something-3
;; => Try 3...



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ALTS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;
;;; TITLE: alts
;;;

(def ch-1 (chan))
(def ch-2 (chan))
(def ch-sym->name #({ch-1 "ch-1", ch-2 "ch-2"} %))

(go-loop []
  (let [[v ch] (alts! [ch-1 ch-2])]
    (when-not (nil? v)
      (println v "from" (ch-sym->name ch))
      (recur))))

(>!! ch-1 "something-a")

(>!! ch-2 "something-b")

(doseq [ch [ch-1 ch-2]]
  (close! ch))



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

(doseq [[idx ch] (map-indexed vector chs)]
  (tap m ch)
  (go-loop []
    (when-let [v (<! ch)]
      (println "ch-" idx ":" v)
      (recur))))

(>!! bc-ch "something-a")

(>!! bc-ch "something-b")

(doseq [ch chs]
  (close! ch))
(close! bc-ch)



;;;
;;; TITLE: pub/sub
;;;

(def to-pub (chan))
(def p (pub to-pub :tag))
(def ch-1 (chan))
(def ch-2 (chan))

(sub p :1 ch-1)
(go-loop []
  (when-let [v (<! ch-1)]
    (println "ch-1: " v)
    (recur)))

(sub p :2 ch-2)
(go-loop []
  (when-let [v (<! ch-2)]
    (println "ch-2: " v)
    (recur)))

(>!! to-pub {:tag :1 :msg "tag-1"})

(>!! to-pub {:tag :2 :msg "tag-2"})

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
