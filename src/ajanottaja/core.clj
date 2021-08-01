(ns ajanottaja.core
  (:require [cambium.core :as log]
            [ajanottaja.migrations]
            [ajanottaja.server]
            [ajanottaja.schemas]
            [mount.lite :as mount])
  (:gen-class))

(defn -main [& args]
  (log/info "Starting Ajanottaja")
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. #(mount/stop)))
  (mount/start)
  (log/info "Shutdown"))