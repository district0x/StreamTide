(ns streamtide.ui.send-support.subs
  (:require [re-frame.core :as re-frame]
            [streamtide.ui.send-support.events :as events])
  (:require-macros [reagent.ratom :refer [reaction]]))


(def registered-keys (atom nil))

(defn now [] (.getTime (js/Date.)))

(defn dispatch-if-not-superceded [{:keys [:key :event :time-received]}]
  (when (= time-received (get @registered-keys key))
    (re-frame/dispatch event)))

(defn dispatch-later [{:keys [:delay] :as debounce}]
  (js/setTimeout
    (fn [] (dispatch-if-not-superceded debounce))
    delay))

(defn dispatch-debounce [{:keys [:key :event :delay] :as debounce}]
  (let [ts (now)]
    (swap! registered-keys assoc (:key debounce) ts)
    (dispatch-later (assoc debounce :time-received ts))))

(re-frame/reg-sub-raw
  ::coin-conversion
  ; subscribe to coin->wei rate and triggers its computation.
  ; Use this when the conversion is linear with regard to the token amount, so you can make the computation later with the real amount
  (fn [db [_ coin donation-type]]
    (let [contract-address (:coin/address coin)]
      (case donation-type
        "vibe-market" (re-frame/dispatch [::events/compute-vibe-market-price {:contract-address contract-address}]))
      (reaction (get-in @db [:coin-conversion (keyword contract-address)])))))


(re-frame/reg-sub-raw
  ::coin-amount-conversion
  ; subscribe to coin->wei rate for a specific amount and triggers its computation.
  ; Use this when the conversion is linear with regard to the token amount, so you can make the computation later with the real amount
  (fn [db [_ user-address coin donation-type amount-eth]]
    (let [contract-address (:coin/address coin)
          data {:user-address user-address
                :contract-address contract-address
                :amount-eth amount-eth}
          event (case donation-type
                  "toshi-mart" [::events/compute-toshi-mart-price data])]
      (re-frame/dispatch [::events/set-coin-conversion-in-progress data])
      (dispatch-debounce {:delay 500 :key (dissoc data :amount-eth) :event event})
      (reaction (get-in @db [:coin-conversion (keyword user-address) (keyword contract-address)])))))


(re-frame/reg-sub
  ::coin-amount-conversion-in-progress?
  (fn [db [_ user-address contract-address]]
    (get-in db [:coin-amount-conversion-in-progress (keyword user-address) (keyword contract-address)])))
