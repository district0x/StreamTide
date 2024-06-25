(ns streamtide.shared.utils
  (:require
    [bignumber.core :as bn]
    [cljs-web3-next.core :as web3]
    [cljsjs.bignumber]
    [district.format :as format]
    [clojure.string :as string])
  (:require-macros [streamtide.shared.utils]))

(def abi-reduced-erc20 (js/JSON.parse "[{\"inputs\":[],\"name\":\"decimals\",\"outputs\":[{\"internalType\":\"uint8\",\"name\":\"\",\"type\":\"uint8\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[],\"name\":\"symbol\",\"outputs\":[{\"internalType\":\"string\",\"name\":\"\",\"type\":\"string\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[],\"name\":\"name\",\"outputs\":[{\"internalType\":\"string\",\"name\":\"\",\"type\":\"string\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"address\",\"name\":\"spender\",\"type\":\"address\"},{\"internalType\":\"uint256\",\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"approve\",\"outputs\":[{\"internalType\":\"bool\",\"name\":\"\",\"type\":\"bool\"}],\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"address\",\"name\":\"owner\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"spender\",\"type\":\"address\"}],\"name\":\"allowance\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}],\"stateMutability\":\"view\",\"type\":\"function\"}]"))

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

(defn from-wei
  ([amount]
   (from-wei amount :ether))
  ([amount unit]
   (web3/from-wei (str amount) unit)))

(defn to-base-amount [amount decimals]
  (let [[int-part decimal-part] (clojure.string/split amount #"\.")
        decimal-part (if (> (count decimal-part) decimals) (subs decimal-part 0 decimals) decimal-part)
        zeros-to-append (- decimals (count decimal-part))
        zeros (apply str (repeat zeros-to-append "0"))
        base-amount (str int-part (or decimal-part "") zeros)]
    (clojure.string/replace-first base-amount #"^0+" "")))

(defn from-base-amount [base-amount decimals]
  (clojure.string/replace
    (let [base-amount-len (count base-amount)]
      (if (< decimals base-amount-len)
        (let [int-part (subs base-amount 0 (- base-amount-len decimals))
              decimal-part (subs base-amount (- base-amount-len decimals))]
          (str int-part "." decimal-part))
        (str "0." (apply str (repeat (- decimals base-amount-len) "0")) base-amount)))
    #"\.?0*$" ""))

(defn format-price [price {:keys [:coin/symbol :coin/decimals]}]
  (let [price (from-base-amount price decimals)
        min-fraction-digits (if (= "0" price) 0 4)]
    (format/format-token (bn/number price) {:max-fraction-digits 5
                                            :token symbol
                                            :min-fraction-digits min-fraction-digits})))

(def auth-data-msg
  ; message to sign for log-in. '%s' is replaced by the OTP
  "Sign in to Streamtide! OTP:%s")

(defn active-round? [round timestamp]
  (>= (+ (:round/start round) (:round/duration round)) timestamp))


(def url-pattern (re-pattern "^(https?:\\/\\/)([a-zA-Z0-9.-]{1,256}\\.[a-z]{2,6})([/?][-a-zA-Z0-9:@%_\\+~#&=.]*)*$"))

(defn valid-url? [url]
  (re-matches url-pattern url))

(defn url->domain [url]
  (when url
    (let [domain (string/lower-case (or (nth (re-matches url-pattern url) 2) ""))]
      (if
        (string/starts-with? domain "www.")
        (subs domain 4)
        domain))))

(defn expected-root-domain? [url domains]
  (let [domain (url->domain url)
        domain (->> (string/split domain ".")
                    (take-last 2)
                    (string/join "."))]
    (contains? (set domains)
               domain)))

(def email-pattern (re-pattern "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])"))

(defn valid-email? [email]
  (re-matches email-pattern (string/lower-case email)))

(def social-domains
  {:facebook ["facebook.com" "fb.com" "fb.me"]
   :instagram ["instagram.com"]
   :linkedin ["linkedin.com"]
   :pinterest ["pinterest.com"]
   :patreon ["patreon.com"]})

(defn json-parse [text]
  (js/JSON.parse text))

(defn json-stringify [json]
  (js/JSON.stringify json))

(defn deep-merge
  [& maps]
  (letfn [(m [& xs]
            (if (some #(and (map? %) (not (record? %))) xs)
              (apply merge-with m xs)
              (last xs)))]
    (reduce m maps)))
