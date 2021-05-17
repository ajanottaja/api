(ns ajanottaja.cljc.server.interceptors-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [ajanottaja.server.interceptors :as interceptors]))


(facts "state interceptor"
       (let [state-intercept (:enter (interceptors/state {:some "state"}))]
         (fact "Inserts state value into request map"
               (-> (state-intercept {}) :request :state)
               => {:some "state"})))


(facts "rewrite-auth0-sub interceptor"
       (let [rewrite-auth0-sub (:enter (interceptors/rewrite-auth0-sub))]
         (fact "Rewrite sub claim to value of io.snorre.ajanottaja claim if present"
               (-> (rewrite-auth0-sub {:request {:claims {:aud "https://ajanottaja.snorre.io"
                                                          :io.snorre.ajanottaja/sub "5d90856c-5a48-46ae-8f6e-a06295ea0dfb"
                                                          :sub "auth0|606479e2f28de50069161c0d"
                                                          :iss "https://ajanottaja.eu.auth0.com/"
                                                          :exp 1619129588
                                                          :azp "5CxrNVcPBmZyKfOgvSbZT0LSYuJJ1Oom"
                                                          :scope ""
                                                          :iat 1619122388}}})
                   :request :claims :sub)
               => "5d90856c-5a48-46ae-8f6e-a06295ea0dfb")
         (fact "If no io.snorre.ajanottaja/sub is provided assume unauthorized"
               (-> (rewrite-auth0-sub {:request {:claims {:aud "https://ajanottaja.snorre.io"
                                                          :sub "auth0|606479e2f28de50069161c0d"
                                                          :iss "https://ajanottaja.eu.auth0.com/"
                                                          :exp 1619129588
                                                          :azp "5CxrNVcPBmZyKfOgvSbZT0LSYuJJ1Oom"
                                                          :scope ""
                                                          :iat 1619122388}}})
                   :response :status)
               => 403)))



(comment
  
  (midje.repl/autotest)
  (->> (slurp (clojure.java.io/resource "claims.json"))
       (muuntaja.core/decode "application/json")))