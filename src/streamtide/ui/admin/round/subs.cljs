(ns streamtide.ui.admin.round.subs
  (:require [re-frame.core :as re-frame]))


(re-frame/reg-sub
  ::multiplier
  (fn [db [_ id]]
    (get-in db [:multipliers id])))

(re-frame/reg-sub
  ::all-multipliers
  (fn [db [_]]
    (get db :multipliers)))

(re-frame/reg-sub
  ::donation
  (fn [db [_ id]]
    (get-in db [:donations id])))

(re-frame/reg-sub
  ::all-donations
  (fn [db [_]]
    (get db :donations)))

(re-frame/reg-sub
  ::coin-info
  (fn [db [_ chain-id coin-address]]
    (get-in db [:coin-info chain-id coin-address])))
