(ns streamtide.ui.admin.announcements.subs
  (:require
    [re-frame.core :as re-frame]))

  (re-frame/reg-sub
  ::adding-announcement?
  (fn [db [_]]
    (get db :adding-announcement?)))

  (re-frame/reg-sub
  ::removing-announcement?
  (fn [db [_ id]]
    (get-in db [:removing-announcement? id])))
