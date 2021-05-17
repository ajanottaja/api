(ns ajanottaja.shared.failjure
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



;; Rich comments

(comment
  (fail "Some bizarro error" {:code 500 :message "Dunno what went wrong."})
  (f/failed? (fail "Some ble" {:code 500}))
  (-> (fail "Foo" {:code 500 :error "What happened here."})
      (data)))
