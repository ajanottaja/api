(ns ajanottaja.core
  (:require [cambium.core :as log]
            [cambium.logback.core.strategy :as strategy]
            [ajanottaja.migrations]
            [ajanottaja.server]
            [ajanottaja.schemas]
            [mount.lite :as mount])
  (:gen-class))

(defn -main [& args]
  (strategy/set-multi-strategy! "multiStrategy" "info" strategy/multi-forever-validator)
  (log/info "Starting Ajanottaja")
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. #(mount/stop)))
  (mount/start)
  (log/info "Shutdown"))