(ns streamtide.ui.oauth-callback.verifier
  "Callback page to verify oauth tokens.
  Shows the spinner while verifying the oauth credentials. If valid, broadcast a message and close the page"
  (:require
    [district.ui.component.page :refer [page]]
    [district.ui.router.subs :as router-subs]
    [re-frame.core :as re-frame :refer [subscribe dispatch]]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.oauth-callback.events :as oc-events]
    [streamtide.ui.oauth-callback.subs :as oc-subs]))


(defmethod page :route.oauth-callback/verifier []
  (let [active-page-sub (re-frame/subscribe [::router-subs/active-page])
        query (:query @active-page-sub)
        auth-complete (subscribe [::oc-subs/auth-complete?])]
    (dispatch [::oc-events/broadcast-oauth-verifier query])
    (fn []
      (if @auth-complete
        [:p "Authentication complete. You can close this page."]
        [spinner/spin]))))
