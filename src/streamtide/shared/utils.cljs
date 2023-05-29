(ns streamtide.shared.utils
  (:require
    [bignumber.core :as bn]
    [clojure.string :as string])
  (:require-macros [streamtide.shared.utils]))

(defn now []
  "Gets current time in millisecons"
  (.getTime (js/Date.)))

(defn now-secs []
  "Gets current time in seconds"
  (int (/ (.getTime (js/Date.)) 1000)))

(defn uuid->network [state]
  "Gets the network id (as a keyword) from the oauth state"
  (keyword (first (string/split state #"-" 2))))

(defn network->uuid [network]
  "Builds a random uuid from a network id"
  (str (name network) "-" (-> (random-uuid) str (string/replace "-" ""))))

(defn safe-number-str [number]
  "Make sure number are not in scientific notation"
  (cond (nil? number) ""
        (and (= (type number) "string") (not (string/includes? number "e"))) number
        :else (.toLocaleString (bn/number number) "fullwide" #js {:useGrouping false})))
