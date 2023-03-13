(ns streamtide.ui.admin.grant-approval-feed.subs
  (:require
    [re-frame.core :as re-frame]))

  (re-frame/reg-sub
    ::reviewing?
    (fn [db [_ address]]
    (get-in db [:reviewing-grant address])))

  (re-frame/reg-sub
    ::decision?
    (fn [db [_ address]]
    (get-in db [:reviewed-grant address])))
