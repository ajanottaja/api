(ns ajanottaja.helpers)


(defn filter-nils
  "Given map m filters out all nil values."
  [m]
  (into {} (filter (comp some? val) m)))


(comment
  (filter-nils {:hello nil :there "kenobi"}))