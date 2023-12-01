(ns streamtide.ui.my-settings.subs
  (:require
    [re-frame.core :as re-frame]))

(re-frame/reg-sub
  ::requesting-grant?
  (fn [db [_]]
    (get db :requesting-grant)))

(re-frame/reg-sub
  ::verifying-social?
  (fn [db [_ network]]
    (get-in db [:verifying-social? network])))

(re-frame/reg-sub
  ::stored-settings
  (fn [db [_ address]]
    (get-in db [:my-settings address])))