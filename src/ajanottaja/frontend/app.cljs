(ns ^:figwheel-hooks ajanottaja.frontend.app
  (:require ["@auth0/auth0-react" :refer [Auth0Provider]]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [re-frame.core :as rf]))

(enable-console-print!)

;; We want to be able to use react hooks if we want
(def functional-compiler (reagent.core/create-compiler {:function-components true}))

(defn app
  []
  [:> Auth0Provider
   [:div "HEllo there"]])

(defn mount
  []
  (rd/render [app] (.getElementById js/document "app") functional-compiler))

;; TODO: Setup hooks for figwheel hot reload loop

;; and this is what figwheel calls after each save
(defn ^:after-load re-render []
  (mount))

;; this only gets called once
(defonce start-up (do (mount) true))

