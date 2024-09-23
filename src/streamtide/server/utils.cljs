(ns streamtide.server.utils
  "Utilities for server"
  (:require [cljs.core.async :refer [<! take!]]
            [district.shared.async-helpers :refer [safe-go <?]]))

(defn wrap-as-promise [chanl]
      (js/Promise. (fn [resolve reject]
                     (take! (safe-go (<? chanl))
                            (fn [v-or-err#]
                              (if (cljs.core/instance? js/Error v-or-err#)
                                (reject v-or-err#)
                                (resolve v-or-err#)))))))
