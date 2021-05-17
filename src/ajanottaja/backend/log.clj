(ns ajanottaja.backend.log
  (:require [cambium.core :as log]))

(defmacro info [mdc msg & forms]
  (concat `(do ('~sym ~mdc ~msg))
            forms))

(doseq [sym log/level-syms]
  (println sym)
  (defmacro '~sym [mdc msg & forms]
    )

#_(defmacro info [mdc msg & forms]
  (concat `(do (log/info ~mdc ~msg))
        forms))

(macroexpand-1 '(info {:ok "hello"}
                      "hello-2"
                      (+ 10 10)
                      (* 10 10)))

(info {:ok "hello"} "what" (+ 10 10))

(info "hello" {:message "there"} (+ 1 1))


(defmacro do-log
  [body message &forms]
  `(do))
