(ns ajanottaja.backend.holidays
  (:require [tick.core :as tick]
            [tick.calendar :as calendar]))

(defn div [& numbers] (java.lang.Math/floor (apply / numbers)))

;; https://gist.github.com/werand/2387286
;; http://en.wikipedia.org/wiki/Computus
;; Using the algorithm from http://www.bitshift.me/calculate-easter-in-clojure/ with a small
;; correction - i used the floor-function to correct the division results
(defn easter-sunday [year]
  (let [golden-year (+ 1 (mod year 19))
        century (+ (div year 100) 1)
        skipped-leap-years (- (div (* 3 century) 4) 12)
        correction (- (div (+ (* 8 century) 5) 25) 5)
        d (- (div (* 5 year) 4) skipped-leap-years 10)
        epac (let [h (mod (- (+ (* 11 golden-year) 20 correction)
                             skipped-leap-years) 30)]
               (if (or (and (= h 25) (> golden-year 11)) (= h 24))
                 (inc h) h))
        m (let [t (- 44 epac)]
            (if (< t 21) (+ 30 t) t))
        n (- (+ m 7) (mod (+ d m) 7))
        day (if (> n 31) (- n 31) n)
        month (if (> n 31) 4 3)]
    (tick/new-date year (int month) (int day))))

(def easter-sunday-memoed
  (memoize easter-sunday))

(def national-holidays
  "All norwegian holidays and how to calculate them"
  [{:name "New Year's Day"
    :description "Public holiday the day after new years eve"
    :fn #(tick/new-date % 1 1)}
   {:name "Maundy Thursday"
    :description "Public holiday thursday before easter"
    :fn #(tick/<< (easter-sunday-memoed %) 3)}
   {:name "Good Friday"
    :description "Public holiday friday before easter"
    :fn #(tick/<< (easter-sunday-memoed %) 2)}
   {:name "Easter"
    :description "Public holiday celebraiting easter"
    :fn #(easter-sunday-memoed %)}
   {:name "Easter Monday"
    :description "Public holiday the day after easter"
    :fn #(tick/>> (easter-sunday-memoed %) 1)}
   {:name "Labor day"
    :description "Public holiday celebrating workers day"
    :fn #(tick/new-date % 5 1)}
   {:name "Acension Day"
    :description "Public holiday celebrating Christ's acension to heaven"
    :fn #(tick/>> (easter-sunday-memoed %) 39)}
   {:name "Constitution day"
    :description "The Norwegian national holiday celebrating the constitution of 1814"
    :fn #(tick/new-date % 5 17)}
   {:name "Wit Sunday"
    :description "Another one of the christian themed holidays"
    :fn #(tick/>> (easter-sunday-memoed %) 49)}
   {:name "Wit Monday"
    :description "Public holiday day after Wit Sunday"
    :fn #(tick/>> (easter-sunday-memoed %) 50)}
   {:name "Christmas Day"
    :description "National holiday after christmas eve"
    :fn #(tick/new-date % 12 25)}
   {:name "Boxing Day"
    :description "National holiday second day after christmas eve"
    :fn #(tick/new-date % 12 26)}])

(comment
  
  ;; Get easter sunday for a given year, used as base
  ;; for other easter, ascension, and pentecost holidays.
  (easter-sunday 2021)
  (easter-sunday 2022)

  ;; Calculate all the national holidays 2021
  (map (fn [{:keys [name fn]}]
         {:name name
          :date (fn 2021)})
       national-holidays))