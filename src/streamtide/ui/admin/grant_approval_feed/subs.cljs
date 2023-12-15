(ns streamtide.ui.admin.grant-approval-feed.subs
  (:require
    [re-frame.core :as re-frame]))

  (re-frame/reg-sub
    ::reviewing?
    (fn [db [_]]
    (get db :reviewing-grants?)))

  (re-frame/reg-sub
    ::decision
    (fn [db [_ address]]
    (get-in db [:reviewed-grant address])))
