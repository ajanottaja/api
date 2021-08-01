(ns ajanottaja.domain.client
  (:require [clojure.java.io :as io]
            [cambium.core :as log]
            [hiccup.core :refer [html]]
            [malli.util :as mu]))


(defn index-page
  []
  [:html
   [:head
    [:title "Ajanottaja"]
    [:link {:rel "stylesheet" :href "styles.css"}]]
   [:body
    [:div {:id "app"}]
    [:script {:src "cljs-out/dev/main_bundle.js" :type "text/javascript"}]]])


(def client-routes
  [""
   {:tags [:client]}
   ["/index.html"
    {:get {:name :client
           :responses {200 {:body string?}}
           :handler (fn [_]
                      {:status 200
                       :headers {"content-type" "application/javascript"}
                       :body (html (index-page))})}}]
   ["/styles.css"
    {:get {:name :css
           :responses {200 {:body string?}}
           :handler (fn [_]
                      {:status 200
                       :headers {"content-type" "text/css"}
                       :body (slurp (io/resource "public/styles.css"))})}}]])


;; Rich comments
(comment
  (-> "public/styles.css"
      io/resource
      #_slurp)
  )