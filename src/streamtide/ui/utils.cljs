(ns streamtide.ui.utils
  "Frontend utilities"
  (:require [cljs-time.coerce :as tc]
            [district.graphql-utils :as gql-utils]))

(defn switch-popup [switch-atom show]
  "Switch the atom associated to the visibility of a popup to hide or show it"
  (reset! switch-atom show)
  (let [function (if show "add" "remove")]
    (js-invoke (-> js/document .-body .-classList) function "hidden" )))

(defn format-graphql-time [gql-time]
  "Pretty printed version of the time coming from the server"
  ;; TODO Check if works well with timezones
  (.toLocaleString (tc/to-date (gql-utils/gql-date->date gql-time))
                   js/undefined #js {:hour12 false :dateStyle "short" :timeStyle "short"} ))

