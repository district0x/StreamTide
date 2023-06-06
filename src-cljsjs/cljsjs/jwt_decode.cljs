(ns cljsjs.jwt-decode
  (:require ["jwt-decode" :as jd]))

(js/goog.exportSymbol "jwt_decode" jd)
