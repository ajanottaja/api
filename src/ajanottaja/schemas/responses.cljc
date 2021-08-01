(ns ajanottaja.schemas.responses
  (:require [malli.core :as m]
            [malli.util :as mu]
            [ajanottaja.schemas :as schemas]))


(def not-found-schema
  [:map
   [:status [:enum 404]]
   [:message :string]])


(def error-responses
  {404 not-found-schema})

(comment
  ;; Require malli related utils for easy access
  (require '[malli.error :as me]
           '[malli.core :as m]
           '[malli.transform :as mt]
           '[malli.util :as mu])


  ;; Transform account data as json using malli and kebab case
  (m/validate not-found-schema {:status 404 :message "Not Found"})
  (m/validate not-found-schema {:status 400 :not-in-schema "Foo"}))