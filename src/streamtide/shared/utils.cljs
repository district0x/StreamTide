(ns streamtide.shared.utils
  (:require
    [clojure.string :as string])
  (:require-macros [streamtide.shared.utils]))

(defn now []
  (.getTime (js/Date.)))

(defn uuid->network [state]
  ; Gets the network id (as a keyword) from the oauth state
  (keyword (first (string/split state #"-" 2))))

(defn network->uuid [network]
  ; Builds a random uuid from a network id
  (str (name network) "-" (-> (random-uuid) str (string/replace "-" ""))))
