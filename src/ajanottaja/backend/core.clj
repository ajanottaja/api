(ns ajanottaja.backend.core
  (:require [cambium.core :as log]
            [ajanottaja.backend.migrations]
            [ajanottaja.backend.server]
            [ajanottaja.shared.schemas]
            [mount.lite :as mount])
  (:gen-class))

(defn -main [& args]
  (log/info "Starting Ajanottaja")
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. #(mount/stop)))
  (mount/start)
  (log/info "Shutdown"))