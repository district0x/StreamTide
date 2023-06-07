(ns streamtide.ui.my-content.subs
  (:require
    [re-frame.core :as re-frame]))

(re-frame/reg-sub
  ::uploading-content?
  (fn [db [_]]
    (get db :uploading-content)))

(re-frame/reg-sub
  ::setting-visibility?
  (fn [db [_ content-id]]
    (get-in db [:setting-visibility content-id])))

(re-frame/reg-sub
  ::removing-content?
  (fn [db [_ content-id]]
    (get-in db [:removing-content content-id])))

(re-frame/reg-sub
  ::removed-content?
  (fn [db [_ content-id]]
    (get-in db [:removed-content content-id])))
