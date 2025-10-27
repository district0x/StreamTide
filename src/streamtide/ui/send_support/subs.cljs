(ns streamtide.ui.send-support.subs
  (:require [re-frame.core :as re-frame]
            [streamtide.ui.send-support.events :as events])
  (:require-macros [reagent.ratom :refer [reaction]]))

(re-frame/reg-sub-raw
  ::coin-conversion
  (fn [db [_ contract-address]]
    (re-frame/dispatch [::events/compute-vibe-market-price {:drop-address contract-address}])
    (reaction (get-in @db [:coin-conversion (keyword contract-address)]))))
