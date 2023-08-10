(ns streamtide.shared.utils
  (:require
    [bignumber.core :as bn]
    [cljsjs.bignumber]
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
  (if (nil? number) "" (-> number js/BigNumber. bn/fixed)))

(def auth-data-msg
  ; message to sign for log-in. '%s' is replaced by the OTP
  "Sign in to Streamtide! OTP:%s")

(defn active-round? [round timestamp]
  (>= (+ (:round/start round) (:round/duration round)) timestamp))


(def url-pattern (re-pattern "^(https?:\\/\\/)?([a-zA-Z0-9.-]{1,256}\\.[a-z]{2,6})([/?][-a-zA-Z0-9:%_\\+~#&=]*)*$"))

(defn valid-url? [url]
  (re-matches url-pattern url))

(defn expected-domain? [url domains]
  (let [domain (nth (re-matches url-pattern url) 2)
        domain (->> (string/split domain ".")
                    (take-last 2)
                    (string/join ".")
                    (string/lower-case))]
    (contains? (set domains)
               domain)))

(def social-domains
  {:facebook ["facebook.com" "fb.com" "fb.me"]
   :instagram ["instagram.com"]
   :linkedin ["linkedin.com"]
   :pinterest ["pinterest.com"]})
