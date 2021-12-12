(ns ajanottaja.failjure
  "Extensions to the failjure library"
  (:require [failjure.core :as f]
            #?@(:cljs [[goog.string.format]
                       [goog.string :refer [format]]])))


(defprotocol HasData
  (data [self]))


(defrecord AnnotatedFailure [message data]

  f/HasFailed
  (failed? [self] true)
  (message [self] (:message self))

  HasData
  (data [self] (:data self)))


;; Just re-export any existing public symbols from failjure.core
(def ^:private failjure-symbols
  (keys (ns-publics 'failjure.core)))


(defmacro failjure-intern [sym]
  `(let [failjure-sym# (ns-resolve 'failjure.core ~sym)]
     (intern *ns* (with-meta ~sym (meta failjure-sym#)) (deref failjure-sym#))))

#?(:clj
   (doseq [sym failjure-symbols]
     (failjure-intern sym)))


;; Then redefine any function/macro we need to treat differently

(defn fail
  "Takes 1-, 2-, or 3-arity version of `message`, `data`, and `fmt-parts` and returns an
  `AnnotatedFailure`. Defaults to empty map for `data`, and empty vector for `fmt-parts`
  if 1- or 2-arity version is used. Returns `AnnotatedFailure` with a (formatted) `message`
  and `data`."
  ([message]
   (->AnnotatedFailure message {}))
  ([message data]
   (->AnnotatedFailure message data))
  ([message data & fmt-parts]
   (->AnnotatedFailure (apply format message fmt-parts)
                       data)))


(defmacro cond-ok->
  "Works like clojure.core/cond->, but threads each expr via ok->
   to short-circuit on any failjures."
  [start & forms]
  (assert (even? (count forms))
          "cond-ok-> takes even number of forms")
  (if (zero? (count forms))
    start
    (let [g (gensym)
          clauses (map (fn [[condition form]]
                         `(if ~condition (f/ok-> ~g ~form) ~g))
                       (partition 2 forms))]
      `(f/attempt-all [~g ~start
                       ~@(interleave (repeat g) clauses)]
                      ~g))))

(defmacro cond-ok->>
  "Works like clojure.core/cond->>, but threads each expr via ok->>
   to short-circuit on any failjures."
  [start & forms]
  (assert (even? (count forms))
          "cond-ok-> takes even number of forms")
  (if (zero? (count forms))
    start
    (let [g (gensym)
          clauses (map (fn [[condition form]]
                         `(if ~condition (f/ok->> ~g ~form) ~g))
                       (partition 2 forms))]
      `(f/attempt-all [~g ~start
                       ~@(interleave (repeat g) clauses)]
                      ~g))))



(comment

  ;; Let's see what the expanded cond-ok-> form looks like
  (macroexpand-1 '(cond-ok-> 0
                             true inc
                             false dec))

  ;; Works just like `cond-ok->` for non-failjure values
  (cond-ok-> 0
             true inc
             false dec)

  ;; Short-circuits on failjures
  (cond-ok-> 0
             true inc
             true ((fn [f] (f/fail "failjure" f)))
             true inc) ;; Last inc never run

  
  ;; We can thread last conditionally too
  (cond-ok->> 0
              true (+ 10)
              false dec
              true (range 0))


  (cond-ok-> 0)

  (macroexpand '(f/ok-> 0 inc inc dec str))


  (cond-ok-> 0
             true inc
             false dec))


;; Rich comments

(comment
  (fail "Some bizarro error" {:code 500 :message "Dunno what went wrong."})
  (f/failed? (fail "Some ble" {:code 500}))
  (-> (fail "Foo" {:code 500 :error "What happened here."})
      (data))

  (cond->))
