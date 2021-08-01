(ns ajanottaja.config
  (:require [aero.core :refer [read-config root-resolver]]
            [ajanottaja.schemas]
            [cambium.core :as log]
            [clojure.java.io :as io]
            [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]
            [mount.lite :refer (defstate) :as mount]))

(defn pwd
  "Current working directory"
  []
  (System/getProperty "user.dir"))

(def port?
  "Malli definition of port number"
  [:and :int [:< {:error/message "Should be a port number <= 65535"} 65535]])

(def db-config-schema
  "Malli schema for database configuration map"
  [:map
   [:host :string]
   [:port port?]
   [:user :string]
   [:password :string]
   [:name :string]])

(def request-method?
  [:enum :get :post :put :patch :delete])

(def cors-config-schema
  "Malli schema for cors config"
  [:map
   [:allowed-request-methods [:sequential request-method?]
    :allowed-request-headers [:sequential :string]
    :origins [:sequential :string]
    :max-age :int]])

(def server-config-schema
  "Malli schema for server configuration map"
  [:map
   [:url :string]
   [:port port?]
   [:cors-config cors-config-schema]
   [:api-token :string]])

(def config-schema
  "Malli schema definition for app wide configuration"
  [:map
   [:env [:and :keyword [:enum :dev :prod]]]
   [:db db-config-schema]
   [:server server-config-schema]])


(defn ^:private resolve-includes
  [_ inc]
  (str (pwd) "/" inc))

(defn read-config!
  "Read configuration from configuraiton file, environment variables, etc.
   If validation of configuration fails log a human readable error and throw an exception."
  []
  (let [config (as-> (io/resource "config.edn") conf
                 (read-config conf {:resolver resolve-includes})
                 (m/decode config-schema conf mt/string-transformer))]
    (if (m/validate config-schema config)
      config
      (do (->> (m/explain config-schema config)
               me/humanize
               log/info)
          (throw (Exception. "Error starting app"))))))

#_:clj-kondo/ignore
(defstate config
  :start (read-config!)
  :stop nil)


;; RIch shippets for testing code
(comment
  ;; Start the config state
  (mount/start #'config)

  ;; Deref the config state 
  @config

  (m/decode [:map [:port [:and int? [:< {:error/message "Should be a port number <= 65535"} 65535]]]]
            {:port "3000"} mt/string-transformer)


  (resolve-includes nil ".secrets.edn")

  ;; Read config file and explain any errors
  (->> (read-config "resources/config.edn")
       (#(m/decode config-schema % malli.transform/string-transformer))
       #_:db
       #_(m/explain db-config-schema)
       #_me/humanize)

  ;; Example error for an empty config
  (me/humanize (m/explain config-schema {})))
