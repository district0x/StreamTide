(ns streamtide.ui.admin.campaign.subs
  (:require
    [re-frame.core :as re-frame]))

  (re-frame/reg-sub
  ::updating-campaign?
  (fn [db [_ id]]
    (get-in db [:updating-announcement? id])))

  (re-frame/reg-sub
  ::removing-campaign?
  (fn [db [_ id]]
    (get-in db [:removing-announcement? id])))
