(ns ajanottaja.backend.server
  (:require [cambium.core :as log]
            [camel-snake-kebab.core :as csk]
            [malli.util :as mu]
            [mount.lite :refer (defstate) :as mount]
            [muuntaja.core :as m]
            [org.httpkit.server :as httpkit]
            [reitit.coercion.malli]
            [reitit.coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.http :as http]
            [reitit.ring :as ring]
            [reitit.ring.malli]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.http.coercion :as coercion]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.dev]
            [reitit.interceptor.sieppari]
            [simple-cors.reitit.interceptor :as cors]

            ;; Own namespaces
            [ajanottaja.backend.config :as config]
            [ajanottaja.backend.db :as db]
            [ajanottaja.backend.domain.client :as client]
            [ajanottaja.backend.domain.auth0 :as auth0]
            [ajanottaja.backend.domain.time :as time]
            [ajanottaja.backend.server.interceptors :as interceptors]))

(def muuntaja-instance
  (-> m/default-options
      (assoc-in [:formats "application/json" :encoder-opts]
                {:encode-key-fn csk/->camelCaseString})
      (assoc-in [:formats "application/json" :decoder-opts]
                {:decode-key-fn csk/->kebab-case-keyword})
      m/create))


(defn routes
  "Create route tree"
  [config state]
  (log/info "Create routes table for use in reitit http router")
  [""
   client/client-routes
   ["/swagger.json"
    {:get {:no-doc true
           :swagger {:info {:title "Ajanottaja - Time tracking API (setup mode)"
                            :description "Keep track of where time flies."}
                     :tags []
                     :securityDefinitions {:bearer {:type "oauth2"
                                                    :flow "implicit"
                                                    :authorizationUrl "https://ajanottaja.eu.auth0.com/authorize?audience=https://ajanottaja.snorre.io"
                                                    :tokenUrl "https://ajanottaja.eu.auth0.com/oauth/token"}
                                           :apikey {:type "apiKey"
                                                     :name "Authorization"
                                                     :in "header"}}}
           :handler (swagger/create-swagger-handler)}}]
   ["/api"
    [""
     {:swagger {:security [{:bearer []}]}}
     ["/test"
      {:interceptors [(interceptors/validate-api-token (:api-token config))]
       :get {:name    :test
             :parameters {:query [:map [:x int?] [:y int?]]
                          :header [:map [:authorization string?]]}
             :responses {200 {:body [:map [:total int?]]}}
             :handler (fn [req]
                        {:status 200
                         :body   {:total (+ (-> req :parameters :query :x)
                                            (-> req :parameters :query :y))}})}
       :post {:name :test-post
              :summary "Post some wicked data"
              :parameters {:query [:map [:x int?] [:y int?]]}
              :response {200 {:body [:map [:total int?]]}}
              :handler (fn [req] {:status 200
                                  :body {:total (+ (-> req :parameters :query :x)
                                                   (-> req :parameters :query :y))}})}}]
     ["/test2"
      {:interceptors [(interceptors/validate-api-token (:jwks-url config))]
       :get {:name    :test
             :parameters {:query [:map [:x int?] [:y int?]]}
             :responses {200 {:body [:map [:total int?]]}}
             :handler (fn [req]
                        {:status 200
                         :body   {:total (+ (-> req :parameters :query :x)
                                            (-> req :parameters :query :y))}})}
       :post {:name :test-post
              :summary "Post some wicked data"
              :parameters {:query [:map [:x int?] [:y int?]]}
              :response {200 {:body [:map [:total int?]]}}
              :handler (fn [req] {:status 200
                                  :body {:total (+ (-> req :parameters :query :x)
                                                   (-> req :parameters :query :y))}})}}]]
    (auth0/routes config)
    (time/routes config)]])




(defn router-config
  "Reitit router configuration"
  [config state]
  {:exception pretty/exception
   :reitit.http/default-options-endpoint (cors/make-default-options-endpoint config)
   :data      {:coercion     (reitit.coercion.malli/create
                              {;; set of keys to include in error messages
                               :error-keys       #{:type :coercion :in :schema :value :errors :humanized :transformed}
                               ;; schema identity function (default: close all map schemas)
                               :compile          mu/closed-schema
                               ;; strip-extra-keys (effects only predefined transformers)
                               :strip-extra-keys true
                               ;; add/set default values
                               :default-values   true
                               ;; malli options
                               :options          {}})
               :muuntaja     muuntaja-instance
               :interceptors [;; Log interceptor
                              (interceptors/request-logger)
                              ;; State interceptor to add stateful values
                              (interceptors/state state)
                              ;; query-params & form-params
                              (parameters/parameters-interceptor)
                              ;; content-negotiation
                              (muuntaja/format-negotiate-interceptor)
                              ;; encoding response body
                              (muuntaja/format-response-interceptor)
                              ;; decoding request body
                              (muuntaja/format-request-interceptor)
                              ;; Handle generic exceptions
                              (exception/exception-interceptor
                               interceptors/error-handlers)
                              ;; Handle coerce exceptions
                              (coercion/coerce-exceptions-interceptor)
                              ;; coercing request parameters
                              (coercion/coerce-request-interceptor)
                              ;; coercing response bodys
                              (coercion/coerce-response-interceptor)]}})


(defn app!
  "Create ring handler with config"
  [config state]
  (log/info "Create reitit app for use in http-kit")
  (http/ring-handler
   (http/router (routes config state) (router-config config state))
   (ring/routes
      (swagger-ui/create-swagger-ui-handler
        {:path "/api-docs"
         :config {:validatorUrl nil
                  :operationsSorter "alpha"
                  :oauth2RedirectUrl (str (:url config) "/api-docs/oauth2-redirect.html")}})
   (ring/create-default-handler))
   {:executor reitit.interceptor.sieppari/executor
    :interceptors [(cors/cors-interceptor config)]}))


(defn dev-app!
  [config state]
  (fn [& args]
    (log/info "Create dev app!")
    (let [app (app! config state)]
      (apply app args))))

(defn prod-app!
  [config state]
  (app! config state))


(defn start-server!
  [config state]
  (log/info {:port (:port config)} "Start server")
  (let [server (httpkit/run-server (if (= :dev (:env config))
                                     (dev-app! config state)
                                     (prod-app! config state))
                                   config)]
    (log/info config "🚀 Server started")
    server))

(defn stop-server!
  [server]
  (log/info "Closing running server")
  (server :timeout 1000))

#_:clj-kondo/ignore
(defstate server
  :start (start-server! (:server @config/config) {:datasource db/datasource})
  :stop (stop-server! @server))


(comment
  @server
  (mount/status)
  (mount/start)
  (mount/stop))

(comment
  {:vlaaad.reveal/command '(clear-output)})