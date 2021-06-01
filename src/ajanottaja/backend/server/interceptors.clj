(ns ajanottaja.backend.server.interceptors
  (:require [cambium.core :as log]
            [clojure.string :as string]
            [reitit.http.interceptors.exception :as exception]
            [tick.alpha.api :as t]
            
            [ajanottaja.shared.failjure :as f]
            [ajanottaja.backend.jwt :as jwt]
            [ajanottaja.shared.schemas :as schemas]))


(defn error-handler
  "Generic handler for errors"
  [message exception request]
  {:status 500
   :body {:message message
          :exception (str exception)
          :uri (:uri request)}})

(defn exception-handler
  [handler e request]
  (log/error e)
  (handler e request))

(def error-handlers
  (merge
   exception/default-handlers
   {;; ex-data with :type ::error
    ::error (partial error-handler "error")

    ;; ex-data with ::exception or ::failure
    ::exception (partial error-handler "exception")

    ;; SQLException and all it's child classes
    java.sql.SQLException (partial error-handler "sql-exception")

    ;; override the default handler
    ::exception/default (partial error-handler "default")

    ;; print stack-traces for all exceptions
    ::exception/wrap exception-handler}))

(defn short-circuit
  "Short-circuits interceptor chain queue and returns the provided response"
  [ctx response]
  (assoc ctx
         :queue nil
         :response response))


(defn request-logger
  "Log requests with timings and other meta data"
  []
  {:name ::request-logger
   :enter (fn [ctx]
            (log/info  {:method (-> ctx :request :request-method)
                        :uri (-> ctx :request :uri)
                        :queries (-> ctx :request :query-params)
                        :headers (-> ctx :request :headers #_(dissoc "authorization"))}
                       "Request")
            (assoc ctx ::started (t/instant)))
   :leave (fn [ctx]
            (let [resp-time (-> (t/new-interval (::started ctx) (t/instant))
                                t/duration
                                t/micros)]
              (log/info 
                        {:status (-> ctx :response :status)
                         :method (-> ctx :request :request-method)
                         :uri (-> ctx :request :uri)
                         :time resp-time}
               (str "Response took " resp-time " microseconds"))
              ctx))})

(defn state
  "Add stateful values like datasource to the request context"
  [state]
  {:name ::state
   :enter (fn [ctx]
            (assoc-in ctx [:request :state] state))})


(defn validate-api-token
  "Handles authentication of requests using api-token (e.g. auth0 post-registration handler)"
  [api-token]
  {:name ::api-token
   :enter (fn [ctx]
            (log/debug "Run api-token interceptor to check provided api-token")
            (let [provided-token (-> ctx :request :headers (get "authorization"))]
              (if (= api-token provided-token)
                ctx
                (short-circuit ctx {:status 403 :body {:message "Not authorized"}}))))})


(defn validate-bearer-token
  "Handles authentication of requests using an auth0 jwt bearer token.
   Validates token using the provided jwks url."
  [jwks-url]
  {:name ::validate-token
   :enter (fn [ctx]
            (log/info {:jwks-url jwks-url} "Validate token from header")
            (f/if-let-ok? [claims (jwt/validate-token! jwks-url
                                                       (-> ctx
                                                           :request
                                                           :headers
                                                           (get "authorization" "")
                                                           (string/replace-first #"Bearer " "")))]
                          
                          (assoc-in ctx [:request :claims] claims)
                          (short-circuit ctx {:status 403 :body {:message "Not authorized"}})))})



(defn transform-claims
  "Replaces the sub claim with the https://ajanottaja.app/sub claim.
   This is done so that the sub claim uses the internal user id from Ajanottaja.
   Note that the reverse domain format of the claim is done to ensure uniqueness."
  []
  {:name ::rewrite-auth0-sub
   :enter (fn [ctx]
            (log/info (-> ctx :request :claims) "Rewrite the sub claim with internal ajanottaja id")
            (if-let [sub (-> ctx :request :claims :https://ajanottaja.app/sub)]
              (assoc-in ctx [:request :claims :sub] sub)
              (short-circuit ctx {:status 403 :body {:message "Not authorized"}})))})



(defn not-found
  "Returns a 404 response if handler returns a nil value."
  []
  {:name ::404-not-found
   :leave (fn [ctx]
            (tap> (:response ctx))
            (if (nil? (:response ctx))
              (assoc ctx :response {:status 404
                                    :body {:message "Not found"}})
              ctx))})