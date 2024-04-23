(ns streamtide.ui.utils
  "Frontend utilities"
  (:require [cljs-time.coerce :as tc]
            [clojure.string :as string]
            [district.ui.router.events :as router-events]
            [district.ui.web3-accounts.subs :as accounts-subs]
            [district.graphql-utils :as gql-utils]
            [re-frame.core :refer [dispatch subscribe]]
            [streamtide.ui.subs :as st-subs]))

(defn switch-popup [switch-atom show]
  "Switch the atom associated to the visibility of a popup to hide or show it"
  (reset! switch-atom show)
  (let [function (if show "add" "remove")]
    (js-invoke (-> js/document .-body .-classList) function "hidden" )))

(defn format-graphql-time [gql-time]
  "Pretty printed version of the time coming from the server"
  (.toLocaleString (tc/to-date (gql-utils/gql-date->date gql-time))
                   js/undefined #js {:hour12 false :dateStyle "short" :timeStyle "short"} ))

(defn truncate-text
  ([text]
   (truncate-text text 14))
  ([text length]
   (let [text-length (count text)
         length (max length 7)]
    (if (<= text-length length)
      text
      (str (subs text 0 (- length 7)) "..." (subs text (- text-length 4)))
      ))))


(defn user-or-address [name address]
  "Returns a truncated name if available or truncated address otherwise"
  (if (string/blank? name)
    (truncate-text address)
    (truncate-text name 35)))

(defn go-home []
  (dispatch [::router-events/navigate :route/home]))

(defn check-session []
  (let [active-account (subscribe [::accounts-subs/active-account])
        active-session? (subscribe [::st-subs/active-account-has-session?])]
  (when (and @active-account (not @active-session?)) (go-home))))

(defn build-grant-status-query [{:keys [:user/address]}]
  [:grant
   {:user/address address}
   [:grant/status]])


(defn build-tx-opts [opts]
  (merge
    {:maxPriorityFeePerGas nil
     :maxFeePerGas nil}
    opts))

(defn valid-address-format? [address]
  (re-matches #"^0x[a-fA-F0-9]{40}$" address))
