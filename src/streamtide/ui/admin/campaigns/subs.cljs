(ns streamtide.ui.admin.campaigns.subs
  (:require
    [re-frame.core :as re-frame]))

  (re-frame/reg-sub
  ::creating-campaign?
  (fn [db [_]]
    (get db :creating-campaign?)))
